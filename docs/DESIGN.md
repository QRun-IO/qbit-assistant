# qbit-assistant: settings, usage and app home (design)

The assistant's overall design is the QQQ spec `QRun-IO/qqq` `docs/specs/2026-10-11-qqq-assistant-qbit-design.md` (owner decisions D1 to D7). This note covers what 0.1.0-RC.4 adds on top of it: provider settings, per-call usage, prices and the app home.

Owner request (2026-10-13): *"it needs a good homepage for the app for it, and it needs a settings screen where you setup the API keys, provider, test connectivity etc. and the app homepage should show health, usage, costs, etc."*

## Data

All of it is in standard QQQ tables with full record screens (D6), prefixed `assistant_`, secured by `assistant.admin` and, for user-owned rows, the `assistant_user_id` record lock. Every `assistant_*` table has AI exposure `NONE`.

| Table | Written by | Purpose |
|---|---|---|
| `assistant_provider` | admins | Which provider and model answer, the key reference, limits, the last connection test. |
| `assistant_model_price` | admins (seeded) | USD per million tokens per model. |
| `assistant_usage` | the qbit only | One row per model call: turn steps (`TURN`) and connection tests (`TEST`). |

`assistant_turn` keeps the per-turn totals and `assistant_usage_daily` the budget rollups, as before. `assistant_usage` adds the per-call detail the home needs: the provider, the failures and latency.

## API keys

Decision: **reference, not storage.** A provider row holds the NAME of an environment variable (`apiKeyEnvName`); `ProviderService` resolves it through `config.getSecretResolver()` (default `System::getenv`) when it builds a client.

Why:
- QQQ core has no encrypted or secret field type. The key-provider SPI and Tink cipher in the RC.6 batch (qqq#1123, #1107) encrypt streamed backup artifacts, not columns, and are not released.
- Spec §2.8: keys are `${env.*}` secrets and are never stored in tables.
- Deployments already hold the key in a secret store (kof22: Infisical, then an ExternalSecret, then the pod env). Rotating it there is the "replace key" step; the database never has a copy to leak through a backup, an export or the API.

Guards:
- Only allow-listed names: each registered type's default (`ANTHROPIC_API_KEY`) plus `allowedApiKeyEnvNames`. Otherwise an admin could aim a row at `DATABASE_PASSWORD` and send it to a provider.
- A base URL override must be in `allowedBaseUrls`. Otherwise an admin could send the real key to their own host.
- The variable name must look like one (`[A-Z_][A-Z0-9_]*`), so a key pasted into the field is refused before it is stored. The error never echoes the value.
- `apiKeyStatus` is a real, read-only column filled by the post-query customizer: `Set • …abcd (from VAR)` (no characters for keys under 12) or `Not set`. Saves drop it, so it is always null in the database. (A virtual field cannot sit in a base section in QQQ 4.1, hence the column.)
- Clients are cached by row settings and a SHA-256 of the key. Keys are never logged, and neither `toString` nor the errors contain one.

## Choosing the provider

`ProviderService.resolve(policyModel)`:
1. The enabled row marked default (marking one default unmarks the others), else the first enabled row.
2. With no enabled row, the provider registered in code (`withModelProvider`) and the policy's main model: hosts that never add a row keep working.
3. A row that cannot be used (no key, a disallowed name or URL, an uninstalled type) makes the assistant report `NOT_CONFIGURED` rather than fall back silently.

The model is the row's, else the policy's main model. The rows and prices are cached for `policyCacheSeconds` and dropped on any change.

## Limits

- **Monthly cost cap (USD):** checked when a turn starts (429 `BUDGET`) and before each model call of a running turn, from the provider's `assistant_usage` cost since the 1st (UTC).
- **Max output tokens per request:** lowers the request's `max_tokens`.
- **Max concurrent requests:** a per-provider semaphore around each model call on this replica.

These sit next to the existing policy budgets (per user per day, per instance per month), which still apply.

## Test connection

A record action on `assistant_provider`, as a standard streamed ETL with Review and Result summary lines and no custom form.
- **Review** lists the providers, the masked key status and every settings problem, without calling anything.
- **Result** makes one call per provider: the prompt "Connection test. Reply with the single word OK.", at most 32 output tokens, effort low. It reports `connected to <model> in <n> ms`, or a hint, the kind, the HTTP status and the provider's own credential-free message.

The result goes on the row (`lastTest*`) and into `assistant_usage` (`TEST`), so a test's cost is counted like any other call.

## Cost

`cost = input × in + output × out + cacheRead × read + cacheWrite5m × w5 + cacheWrite1h × w1`, in micro-dollars, rounded up (`ModelPricing.costMicros`).

Prices come from the `assistant_model_price` row for (type, model). Empty cache prices are 0.1x, 1.25x and 2x the input price. A model without a row uses the `pricing` config map, else costs 0. A price change affects later calls only; each usage row keeps the cost it was recorded with.

## App home

One HTML widget (`assistantHome`) on the Assistant app. It is the only place this data is summarized: no other dashboard repeats it, and the developer panel stays per-record.

`AssistantHomeData` reads the tables as the viewing admin, at most `homeMaxRows` rows each, for the last 30 days (UTC) and the current month, and says when totals are partial. `AssistantHomeView` renders it in flow and grid layout only:
- no tables, fixed widths or inner scroll areas
- inherited text colors, so light and dark both work
- a status row, one row per provider, then cards for usage by day, costs, users, tools and failures.

Every section has an empty state; nothing is invented.

The nav is the home plus the main tables: Providers, Conversations, Model prices, Policy. Messages, turns, tool calls, feedback and model calls are child lists on a conversation, and model calls also on a provider. Processes are actions on their tables.

## Strict tools (0.1.0-RC.5)

**Incident (kof22, 2026-10-13).** Every turn failed on its first model call with 400 `invalid_request_error`: "The compiled grammar is too large, which would cause performance issues. Simplify your tool schemas or reduce the number of strict tools."
- The request sent all ten read tools with `strict: true`.
- Anthropic compiles every strict schema in a request into one grammar.
- The request was inside every documented limit: 20 strict tools, 24 optional parameters, 16 union parameters. The real limit is not published.
- The log line only said `INVALID_REQUEST httpStatus=400`, so the reason was invisible.

**Decision.**
- **No tool is strict by default.** `AssistantQBitConfig.strictToolNames` opts tools in.
- Strict mode only guaranteed schema-valid input. The kernel already validates every input and returns `INVALID_INPUT` results, and the UI-API tools (below) go through the same validation as the UI.
- If a provider still refuses strict schemas (`ModelProviderException.isStrictSchemaRejected`), the turn retries once with no strict tools and logs a WARN.

**Budget.**
- `AnthropicRequestContractTest` fails the build when the strict tools of the default request exceed 3 tools, 6 optional parameters or 2 union parameters. These budgets are far below the shape that failed.
- It also checks the documented request rules:
  - adaptive thinking without `budget_tokens`, and no sampling parameters;
  - a valid effort, and no forced `tool_choice`;
  - at most 4 cache breakpoints, with longer TTLs first;
  - a user-first, user-last message order;
  - strict-schema keywords.

**Real-API check (dev).** `ANTHROPIC_API_KEY=... ./mvnw verify -Dassistant.live=true -Dtest.excludedGroups= -Dgroups=live -Djacoco.skip=true -pl qbit-assistant-provider-anthropic -am` runs the live golden evals. They send exactly this request shape.

**Diagnostics.** On a 4xx the provider's error type, sanitized message and request id are:
- logged at WARN;
- recorded as the `assistant_usage` request id;
- shown to `assistant.admin` holders, in the panel's error and in Test connection.

## Owner decisions (2026-10-13)

Owner: *"the agent should have to go through the same API that the UIUX does. that way it is held to the same security boundaries etc"*, plus the ability to configure the assistant and verify its security and data access.

1. **Tools via the UI API.** Every tool that reads or writes data, or runs a process, report or widget, runs a spec'd `/qqq/v1` operation with the calling user's own session. That means the UI's authentication, permission checks, record locks, validation, audit and error mapping. There are no direct core data actions and no elevated sessions.
2. **Data access is configured and verifiable in the UI.**
   - Each table and process has an AI data access setting: NONE, READ or READ_WRITE. Sensitive tables default to NONE.
   - Admins can see what the assistant could do for a given user, and why.
3. **Writes need confirmation.** A write tool only *proposes* the change, as a pending action card. Nothing changes until the user confirms in the panel, unless the policy turns confirmation off.
4. **Persona and skills, never more access** (owner, 2026-10-13): *"in the config for the AI settings, we should be able to set a name and personality and extra skills etc for the agent - and again - the agent should be limited to only using our UIUX API and being able to only do the things that the currently logged in user could do with the same UIUX API via the UIUX"*.
   - **Profiles** (`assistant_profile`, admin-edited): display name, icon, personality (tone instructions, at most 2,000 characters), greeting and up to 6 starter prompts.
     - One profile is the default. Others are scoped to an app, to users holding a permission (a role), or to both. The most specific profile wins.
     - `/assistant/config` returns the profile for the panel header, the greeting and the starter prompts.
   - **Skills** (`assistant_skill`, linked to profiles through `assistant_profile_skill`): named Markdown instruction packs of at most 6,000 characters, with a description, an enabled flag, trigger keywords and apps.
     - A skill is read only when relevant: a keyword appears in the question, the user is in one of its apps, or it has neither, in which case it is always read.
     - All skills read for one question total at most 12,000 characters.
   - Profile and skill text goes into the per-user system block, framed as "tone and know-how only". The rules come first, and rule 10 says these can never override the rules or give other tools, data or permissions.
   - **Hard guarantee:**
     - Every data, process, report or widget capability is a `/qqq/v1` operation, called through the gateway (core `UiApiInvoker` once it is released) with the CURRENT user's session.
     - The tool list is the spec'd operations ∩ data access ∩ policy. Profiles and skills are never read when tools are built or run.
     - It is enforced by ArchUnit: the tool, UI API and access code may not use QQQ table, process, report, dashboard or async actions, the core AI kernel, or the profile and skill classes. It is also enforced by a runtime guard: `AssistantStore` refuses any table that is not `assistant_*`.
     - `UiApiToolsSecurityTest.testDenialMatrix` checks that a user without permission gets the identical status and message from the UI API and through the assistant for query, get, insert, update and delete. The assistant has no process or report tool, and process data access defaults to NONE.
5. All other open questions in this section were decided as recommended (shared rule: "all recommendations accepted").

## Tools through the UI API (0.1.0-RC.6)

### Transport: loopback HTTP with the user's session

`UiApiGateway.invoke(UiApiCall)` makes an HTTP request to this server's own `/qqq/v1`, carrying the user's `sessionUUID` cookie and `X-QQQ-Invoked-By: assistant`.
- Only operations defined by `MiddlewareVersionV1`'s endpoint specs can be called. Anything else, including the assistant's own raw routes, is refused before a request is made.
- A call without a session is also refused.

**Why loopback rather than calling the spec handlers in process.** The spec's `buildInput(Context)`, session setup, the version's `preExecute`, Javalin filters, permission checks and error mapping all hang off the Javalin request pipeline.
- Calling the executors directly would skip some of these and drift over time.
- Building a synthetic Javalin `Context` relies on internal API.
- A request to the server's own port runs the identical pipeline by construction. It costs one local hop, which is milliseconds next to a model call.

This matches core's design (`docs/UI-API-EXTENSIONS.md` §5, `UiApiInvoker`, QRun-IO/qqq#1128).
- `LoopbackUiApiGateway` is the stand-in until a QQQ release has the invoker. Then an adapter over `UiApiInvoker.forServer(server)` replaces it (or a host injects one with `withUiApiGateway`), and nothing else changes.
- The base URL is `uiApiBaseUrl` if configured. Otherwise it is `127.0.0.1` plus the local port the first assistant request arrived on, so in a multi-replica deployment the call stays on the replica that holds the session's process state.

### The tools

| tool | `/qqq/v1` operation |
|---|---|
| `list_tables` | `GET /metaData` (tables with the user's read/insert/edit/delete flags) |
| `describe_table` | `GET /metaData/table/{tableName}` |
| `query_records` | `POST /table/{tableName}/query` (plus `.../count` when there are more rows) |
| `count_records` | `POST /table/{tableName}/count` |
| `get_record` | `GET /table/{tableName}/{primaryKey}` |
| `search_records` | `POST /search` |
| `render_widget` | `POST /widget/{widgetName}` |
| `get_record_history` | `GET /table/{tableName}/{primaryKey}`, then `POST /table/{tableName}/query` on `audit_table`, `audit` and `audit_detail` |
| `create_record` / `update_record` / `delete_record` | `POST /table/{tableName}` / `PATCH /table/{tableName}/{primaryKey}` / `DELETE /table/{tableName}/{primaryKey}` |

`navigate` and `search_help` stay on the core kernel because they read no records:
- `navigate` only builds an `/app` link, and the page enforces permissions when it opens.
- `search_help` reads help text from metadata.

Running processes and reports through the assistant (`/processes/...`, `/reports/...`) is not offered yet. Process data access rows are stored so that the screens can already show and set them.

### Layers (each one can only narrow the one before)

1. **The user's own permissions and record locks**, enforced by the UI API itself. A user who cannot do something in the UI gets the identical status and message through the assistant: the tool result is `DENIED`/`NOT_FOUND`/`INVALID_INPUT` with `"<operation> answered HTTP <status>: <message>"`.
2. **Data access** (`assistant_data_access`, editable by `assistant.admin`). A row sets NONE, READ or READ_WRITE per table (`TABLE`) or process (`PROCESS`). Without a row:
   - tables whose AI exposure is NONE (the `assistant_*` tables, sessions, OAuth, and any table whose metadata says so) are NONE, and a row cannot raise them;
   - other tables are READ, so the assistant never writes unless an admin allows it;
   - processes are NONE.
   The write tools are offered only when some table is READ_WRITE.
3. **Policy** (`assistant_policy`):
   - `writesRequireConfirmation` (default true);
   - `allowedTools` (empty allows all);
   - `maxRowsPerCall` (default 50, ceiling 200);
   - `redactPii` (default true: secrets, card numbers and SSNs are redacted, with the core `AiRedactor`).
4. **AI field exposure** from table metadata:
   - NONE and METADATA_ONLY values never reach the model;
   - MASKED values are masked;
   - only VALUES fields can be filtered or sorted on.

### Writes and confirmation

With confirmation on, a write tool validates the table's data access and the fields. It then inserts an `assistant_pending_action` row (owner-locked, `PENDING`, expiring after `pendingActionTtlSeconds`, default 15 minutes) and returns a `pending_action` render, which the panel shows as a card. The model is told the change is not done.
- `POST /qqq/v1/assistant/actions/{id}/confirm` claims the action (`RUNNING`, so a double click cannot run it twice). It then runs the stored operation through the UI API **with the confirming request's session** and records the outcome as `EXECUTED` or `FAILED`.
- `POST /qqq/v1/assistant/actions/{id}/reject` runs nothing.
- `GET /qqq/v1/assistant/actions/{id}` reads the action's state.
- Only the user who proposed an action can see or decide it.

### Tool-call log

Each `assistant_tool_call` row records:
- the user and the tool;
- the parameters, with secrets redacted;
- the UI API operation and the HTTP status the UI would have seen;
- the rows touched and the outcome;
- the pending action, if there is one;
- an in-app link to the written record, whose page shows its audit entries.

A confirmed write adds its own row. The rows are a child list on conversations, and pending actions are another one.

### Tests

`UiApiToolsSecurityTest` runs the fixture app on a real QQQ application server. For each case it makes the UI's own request (a direct `/qqq/v1` call with the user's cookie) next to the assistant's tool call:
- read denied: the same 403 and message;
- write denied without the insert permission, even with READ_WRITE and no confirmation;
- data access only narrows: NONE, READ, and sensitive tables that cannot be raised;
- a confirmed write runs once, through the UI API, and is logged;
- only the owner can decide an action;
- only spec'd operations can be called, and never without a session;
- the policy narrows the tools and caps rows.

The golden and red-team evals now run through the UI API too.

## Explain access and recent denials (0.1.0-RC.6)

- **Explain access:**
  - `GET /qqq/v1/assistant/access?table=&prompt=` returns, for the calling user and each table (or only the named table, or only the tables a prompt mentions):
    - the user's UI permissions, from `GET /qqq/v1/metaData` with their own session;
    - the assistant's data access and where it comes from (setting, default, or sensitive table);
    - each tool's operation, with allowed or the reason it would be denied (policy, data access, not visible in the UI, or no read/insert/edit/delete permission).
  - The `assistantAccess` widget on the Assistant home renders the same thing for the viewing admin.
  - Choosing *another* user would need that user's permissions without their session. QQQ core has no API to evaluate another user's permissions, and the assistant never builds or borrows a session. That part waits for a core API, so the report is always computed with the requester's own session.
- **Recent denials:** the home's "Recent denials" card lists the newest tool calls the UI API, data access or the policy refused, with who, the table, the operation and the HTTP status, or the assistant setting that refused it.

## Owner decisions (2026-10-13), continued: write mode, the hard wall, audit attribution, providers

These were added on the same day. All recommendations below are accepted; we adjust once they can be seen working.

### W. Write mode: the assistant is just a UI client
Owner: *"the assistant should be able to go into 'write mode' too - with a user selection or preference? allowing it to do things like create and schedule a report for you, or update a record. it should ALWAYS only have access via the UIUX API (the assistant is just a UI) and all permissions that the user has via the UIUX should always hard apply at a layer above the assistant. All auditing needs to be aware of this too and audit accordingly"*

- **Mode per conversation.** A panel toggle switches between **Read-only** (the default) and **Write**.
  - The default comes from the user's preference (`assistant_user_preference.default_write_mode`).
  - An admin policy can disable write mode globally (`assistant_policy.write_mode_allowed`), per role (`write_mode_permission`: only holders may switch to Write), or per table or process (data access READ or NONE).
- **Read-only:** the write tools are not in the tool list at all. This is decided when the request is built, not by the model.
- **Write:** every write is still a pending-action card that the user confirms.
  - A user may set "auto-confirm low-risk writes" only when the policy allows it (`auto_confirm_allowed`).
  - Low-risk means a single-record insert or update on a table the admin marked `auto_confirm_ok`.
  - Deletes, bulk operations, processes, report schedules and notifications are never auto-confirmed.
- **What counts as a write.** All of these are `/qqq/v1` UI API operations:
  - insert, update and delete records;
  - run a process (`/processes/{name}/init` and steps; every step is a confirmed action);
  - create, save or schedule a report or saved view (through the saved-report and scheduled-job tables' own insert operations);
  - create a notification.
- **Enforcement is beneath the assistant.** The UI API and core permission layer decide, exactly as for Next UI. The assistant only forwards the user's credentials (`UiApiInvoker`, `UiApiCredentials`, qqq#1128).
  - The ArchUnit rule (no core actions from the tool code) and the denial matrix stay.
  - The matrix is extended to every write operation, in both modes.

### X. The hard wall between the assistant and its configuration
Owner: *"there should also be a hard wall between the assistant and the configuration for the assistant. he should never be able to see his own configuration tables etc."*

AI exposure NONE is not enough, because the assistant acts as the user and an admin user can edit the configuration. The wall is enforced in core, beneath the assistant, by client channel (UI API 1.2.x, approved design):

- Every `/qqq/v1` request the assistant makes carries `X-QQQ-Client: assistant/<turnId>`. It is set server-side by `UiApiInvoker.forServer(server, "assistant")` and `withClientDetail(turnId)`, and the invoker drops any client-supplied value. Core stores it as `QSession.getClientChannel()`.
- **Deny rules:**
  - `ClientChannelsMetaData.withDeniedPattern("assistant", "assistant_*")`, plus core's defaults: `authentik_*`, `audit*`, `*api_key*`, `*apiKey*`, `*credential*`, `*secret*` and `webhooks_*secret*`;
  - `QPermissionRules.withDeniedClients(Set.of("assistant"))` on every assistant process (retention, test connection) and widget.
  
  The rules are enforced in `PermissionsHelper`: the object is left out of metadata, and calls get 403, failing closed.
- The qbit registers **every** `assistant_*` table, process and widget in the deny set. A **startup check** fails the instance if any `assistant_*` object is not denied to the `assistant` channel.
- Profiles and skills reach the model only as server-assembled instructions. No tool reads or edits them (this is already enforced by ArchUnit), so a prompt injection can never persist itself.
- The current conversation's context is read by internal code (`AssistantStore`, assistant tables only), never by a tool.
- **Tests:** an admin through the assistant is denied on every configuration table and process (403, and absent from `list_tables` and `/metaData`), while the same admin through Next UI is allowed.

### Y. Audit attribution
- Every action records the human user as the actor **and** the client channel: `actor=james@kof22.com via=assistant/<turnId>`, versus `via=next-ui`.
- Core writes `clientChannel` on `audit` (`AuditsMetaDataProvider.withClientChannelField(true)`, filled by `DMLAuditAction`), so audit screens can filter by channel.
- `assistant_tool_call` keeps the turn, conversation and model, and links to the audit rows (`auditHref`), so `via=assistant/<turnId>` leads to the conversation, the turn and the model.
- **Tests:** an assistant write produces audit rows with the human actor and `via=assistant/<turnId>`; the same write from the UI produces `via=next-ui`.
- kof22 needs a feature-named changeset (`changesets/features/...`) for the audit column.

### P. Providers, local models, HIPAA routing, budgets
1. **Providers and models as data, any number, each with an enabled toggle.**
   - Kinds:
     - Anthropic (exists);
     - **OpenAI-compatible**, one adapter for OpenAI, Azure OpenAI, OpenRouter, Ollama, LM Studio, vLLM and the llama.cpp server;
     - **AWS Bedrock** (Claude and others);
     - **Google Vertex** (Claude and Gemini).
   - `assistant_provider` gains: kind, base URL, key env reference (unchanged pattern, no stored secrets), local or cloud, `hipaa_eligible` and region.
   - The new **`assistant_model`** table holds: provider, model id, display name, capabilities (tools, context window, vision, streaming), price in and out per million tokens (0 or a configured compute cost for local models), and an enabled flag.
   - Test connection works for every provider and every model.
   - Tool calling follows each provider's rules. The strict-grammar lesson applies: strict only where supported, and a fallback when it is refused.
2. **Local models** (Mac Studio, and the workstation "lora" with an RTX A5000 24 GB, running Ollama, LM Studio or vLLM; another agent sets up the hosts). Any OpenAI-compatible endpoint is supported, with health checks, model discovery (`GET /v1/models` fills `assistant_model`) and per-provider timeouts.
3. **HIPAA/PHI routing:**
   - A data classification on tables and fields in metadata: PHI, PII, CONFIDENTIAL or PUBLIC.
   - Each turn tracks the highest classification its context and tool results touched.
   - The policy allows PHI only to `hipaa_eligible` providers (local, or cloud under a BAA). Otherwise it blocks, with a clear reason.
   - A per-instance **HIPAA mode** (on for MHP, off for kof22) also enforces:
     - PHI redaction in logs;
     - encrypted conversation storage;
     - a separate retention period;
     - a full audit (who, which classification, which model);
     - no non-eligible providers.
   - **BAAs are an owner and legal step**: the flag records a fact; it does not create one.
4. **Budgets:**
   - daily and monthly cost and token budgets per user, group or role, provider or model, and instance;
   - default behaviour: warn at 80% (bell and alert, through the Alerts SPI when present), downgrade to a cheaper or local model at 100%, and stop at a configurable ceiling;
   - admins can override;
   - usage and cost over time on the app home, by user, model, provider and tool.
5. **Extras:**
   - model routing (simple versus complex, per profile) and fallback chains across providers;
   - **RAG** over the instance's content: a local embedding model, pgvector, and retrieval filtered by permissions through the UI API as the user;
   - **scheduled assistant tasks**: recurring prompts that run as a designated user (with that user's stored credentials through the UI API, never a system session) and post results as notifications or records.
6. Everything goes through the `/qqq/v1` UI API, as the user, with the guarantees above.

### P-a as built

- Per-provider `tlsCaBundle` references startup-parsed, transient server CA bundles. OpenAI-compatible chat, health and discovery trust JVM roots plus the bundle, retain hostname verification, and report sanitized NETWORK errors with a trust hint.
- `assistant_provider` records kind, location, HIPAA eligibility, region, API
  version, timeouts and discovery status. `assistant_model` is its admin-only
  child list: enablement, capabilities, prices and test status, unique per provider/model.
- `ProviderSettings`, discovery and health extend the provider SPI through default
  methods. Legacy `create(apiKey, baseUrl)` implementations keep working.
- The OpenAI-compatible module uses JDK HTTP and Jackson, Chat Completions with
  SSE or single JSON replies, Azure deployment URLs and api-key auth, and keyless
  local endpoints. Keys and URL overrides pass the existing allowlists first.
- Strict tools require explicit opt-in, OpenAI or Azure, and recursively strict
  object schemas. A strict/schema/grammar 400 signals the existing one-retry fallback.
- Discovery only adds missing models; it preserves edits and disables catalogs
  larger than 20 for cloud providers. Health uses a short models request. Provider
  tests probe health then complete; model tests can probe a disabled model before
  enabling it. Completion tests record TEST usage.
- Registry prices and capabilities override adapter/type defaults. Empty provider
  model selects the first enabled registry model; an explicitly disabled selection
  is a setup error. Rows without models retain the prior behavior.
- Localhost contract tests cover dialects, fragmented parallel tool calls, usage,
  errors, timeouts, cancellation, discovery and a golden tool round trip. The live
  smoke is environment-driven and excluded by default.

### P-b as built

- Optional Bedrock and Vertex modules reuse the Anthropic mapping and streaming
  adapter through an injected SDK client. Bedrock retains SDK SigV4 and binary
  event-stream decoding; a small Backend delegate supplies allow-listed endpoints
  because SDK 2.71.0 has no Bedrock endpoint setter.
- Bedrock uses the server's AWS default credential chain; IRSA/web-identity hosts
  supply STS on their runtime classpath. No STS or control-plane SDK is bundled.
- Vertex uses scoped Google ADC. `claude-*` routes to the Anthropic backend; other
  ids route to the OpenAI-compatible endpoint with a per-request token supplier,
  `google/` for unprefixed ids, and no strict tools.
- Both types require region, reject key references and default to CLOUD. Vertex
  additionally requires `cloudProject` (`cloud_project VARCHAR(100)`); it is part
  of resolved settings and the provider cache key. Type hooks own validation.
- Discovery is a static catalog, health is unsupported, and Test connection uses
  the existing completion. HIPAA eligibility remains an admin fact, not a BAA.
- Localhost contract tests cover wire paths, authentication, text/tools/usage,
  expired-token refresh, errors, allowlists and golden turns for all three routes.

### Phases (each a green PR)
| | Scope | Depends on |
|---|---|---|
| d | Spec'd `/assistant` endpoints, tools on `UiApiInvoker` (#19) | QQQ RC.6 |
| W1 | Write mode: conversation toggle, user preference, policy (global, role, table), tool list by mode, auto-confirm rules, write matrix in both modes; Next toggle | — |
| X1 | Client channel `assistant/<turnId>` on every call, deny registration for all `assistant_*` objects and the security surfaces, startup check, admin-denied tests | core client-channel PR (UI API 1.2.x) |
| Y1 | Audit attribution tests and `auditHref` to audit rows filtered by channel | core audit column |
| W2 | Process, report-schedule, saved-view and notification writes as confirmed UI API operations | W1, X1 |
| P-a | Provider and model registry, OpenAI-compatible adapter, discovery and health | — |
| P-b | Bedrock and Vertex | P-a |
| P-c | Classification, HIPAA routing and mode | P-a, core metadata |
| P-d | Budgets and dashboards | P-a |
| P-e | Routing and fallback chains | P-a |
| P-f | RAG (embeddings, pgvector, UI-API-filtered retrieval) | P-a |
| P-g | Scheduled assistant tasks | W1, X1 |


## Next phases

- **(c) Data access screens:**
  - the Data Access table, in the nav;
  - an "Explain access" action (for a user, and a table or a prompt, which operations the assistant could call and which it would be denied, with the reason);
  - a recent-denials widget on the app home.
- **(d) Spec'd assistant endpoints:** move `/qqq/v1/assistant/...` (conversations, turns, events, feedback, cancel, config, actions) onto `AbstractEndpointSpec`s through the qbit spec extension point (`MiddlewareSpecExtension`, established prefix `/assistant`, QRun-IO/qqq#1128). The SSE turn and events streams stay raw routes until the spec layer can describe a stream.
