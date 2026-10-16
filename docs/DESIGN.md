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

## Provider SPI boundary

All provider contracts and value types, including `ModelPricing`, belong exclusively
to `com.kingsrook.qbits.assistant.spi` in `qbit-assistant-provider-api`. Consumers
can depend on this artifact alone and allow its package in G1. Runtime provider
registry and fake implementations remain in their existing internal packages.

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

`cost = input × in + output × out + cacheRead × read + cacheWrite5m × w5 + cacheWrite1h × w1`, in micro-dollars, rounded up (`com.kingsrook.qbits.assistant.spi.ModelPricing.costMicros`).

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
     - `/assistant/config` returns the profile for the panel header, the greeting and the starter prompts (built per user from the tables the user can read; configured ones need a `[table]` tag, see UPGRADE.md).
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
- `CoreUiApiGateway` adapts core's `UiApiInvoker` (the loopback stand-in is deleted). It labels calls with the client channel `assistant`, plus the turn id as detail (`assistant/<turnId>`). Form params go to the invoker's `withFormParam`; extra headers (the bulk-load tools' `X-QQQ-AI-Route`) go through `HeaderedUiApiInvoker`, a small subclass in core's package because core's request has no `withHeader` yet. A host can still inject its own gateway with `withUiApiGateway`.
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

## Spec'd assistant endpoints and the core invoker (phase d, QQQ 4.1.0-RC.6)

- **The endpoints are spec'd `/qqq/v1` operations.** `api/specs/AssistantSpecs` registers the UI API extension `assistant` (semver 1.0.0, established prefix `/assistant`) through `MiddlewareSpecExtensionsMetaData`.
  - The URLs, bodies and statuses stay the same (201 create, 202 cancel, 204 delete and feedback, and the assistant's `{"error","code"}` errors), so Next needs no change.
  - They appear in the served `/qqq/v1/openapi.yaml`. The turn and events streams are declared as `text/event-stream`, with `AssistantTurnEventV1` describing the events.
  - The fragment is committed as `qbit-assistant/src/main/resources/openapi/qqq-v1/assistant.yaml`. `AssistantSpecsContractTest` checks it against the code, and the release baseline in test resources, with core's `UiApiContract`.
- **No raw routes.** `AssistantRouteProvider` keeps only a before-handler that learns the server's local port from the first `/qqq/v1` request.
- **Tools call core's `UiApiInvoker`** (`CoreUiApiGateway`), and the loopback stand-in is removed.
  - The request's credentials (`UiApiCredentials.fromContext`: session cookies, Authorization, API key, exactly as they came in) are captured by the spec that starts the turn, confirms an action or explains access. They are passed to the tools, so OAuth2, table auth and API-key sessions all work. They are never logged.
  - The gateway also refuses the assistant's own operations, so a turn can never start a turn.
- QQQ 4.1.0-RC.6 (the default `qqq.snapshot.version`) contains #1128, so no local build is needed.

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

### X1/Y1 as built
- Record history is an explicit, default-off host opt-in (`withRecordHistory(true)`); kof22 does not opt in. RC.6 removes only `audit*`; later core #1193 support allows audit reads with a write-only denial. Other walls and user audit permissions remain intact.
- RC.6's invoker fixes the `assistant` channel server-side and ignores spoofed client headers. Turn context survives knowledge discovery, RAG reads, confirm actions and owned-turn explain-access; scheduled calls retain `scheduled/<taskId>`.
- The producer enumerates assistant tables, processes, reports and widgets (including source-QBit metadata), adds explicit denied-client rules without replacing user permissions, and registers `assistant_*` idempotently. Runtime construction independently validates the wall with core's `isDenied` and fails on exposed objects.
- The fixture enables core's audit channel field. Hosts must migrate the nullable audit column before enabling it; kof22 needs its own feature changeset.
- Successful insert/update/delete tool calls link to `/app/audit` with `recordId`, `auditTable.name` and `clientChannel` criteria, using the same encoder as `present_results`. Hosts without the channel field get no audit link. Existing audit link storage widens to TEXT for long encoded filters.
- HTTP tests enumerate the configuration denial matrix for an admin, contrast Next UI access, reject broken startup metadata, and verify confirmed-write actor/channel plus the decoded audit filter.

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

### P-c as built

- The qbit classification stand-in is gone: the levels are core's `actions.ai.DataClassification` (QQQ 4.1.0-RC.6, core #1142).
  The default `ClassificationSource` reads core metadata (`AiDataClassifications`, `AiTableMetaData`, `AiFieldMetaData`) merged with the configured table/field map; configuration wins when both are set. Hosts can still supply their own source with `withClassificationSource`.
- Turn/page/tool classification is monotonic, persisted on the turn and retained
  on the conversation. Field subsets use overrides; unknown cross-table results
  conservatively use the highest configured level. Metadata tools stay PUBLIC.
- Small engine hooks check a fresh provider row before every model dispatch,
  including strict retries. PHI requires explicit row eligibility; HIPAA mode
  requires it for all calls. Blocks withhold results, explain the rule without
  values, and write an audit. `ProviderService.eligibleAlternative` is an empty
  seam for P-e; P-c never changes providers automatically.
- HIPAA mode refuses non-eligible settings/selection and requires a valid key
  reference at startup. AssistantStore alone encrypts/decrypts conversation
  payloads using AES-256-GCM; legacy plaintext reads remain supported and corrupt
  ciphertext fails closed. No key is persisted. Provider diagnostic text is
  suppressed in HIPAA logs and turn errors.
- Admin read-only, owner-locked `assistant_data_audit` records each PII/PHI model
  dispatch and each block, outside navigation. PHI conversation retention defaults
  to 2190 days in either mode; shorter tool-result/replay cleanup is unchanged.
  The app home shows HIPAA mode and PHI retention.

### P-d as built

- ROLE allowances are per permission holder. Shared scopes sum a private immutable
  accounting projection of usage counters; owner-locked source rows stay locked.
  Migration backfills existing usage before writes resume. No sessions are elevated.

- Standard admin budget rows add daily/monthly cost or token allowances above the
  existing config/policy budgets and provider monthly caps. Overrides carry a
  reason, session grantor and expiry; they never waive the other limit layers.
- Start-turn checks use the existing 429 BUDGET path through PolicyService.
  Every dispatch, including strict retries, evaluates current usage and overrides.
  Events deduplicate WARNED, DOWNGRADED and STOPPED.
- QQQ RC.5 core QAlerts sends budget signals alongside panel notices, with three
  admin-visible alert types and a default logging sink. Resolution awaits active
  alert/recurrence tracking so overrides cannot clear shared alerts incorrectly.
- Explicit registry model selection leaves provider defaults untouched. Automatic
  downgrades prefer LOCAL, then price. PHI eligibility and destination provider caps
  still apply; a missing eligible target stops the turn.
- The home owns the budget cards and 30-day cost/token bars, with user, model,
  provider and tool breakdowns, inherited colors and empty states.

### P-e as built

- Profiles select OFF (the migration default), HEURISTIC or CLASSIFIER and optional
  simple/complex/classifier registry models. Selection uses ProfileService's existing
  specificity. Without a profile, enabled escalation config supplies the complex
  model and effort. Empty slots retain provider row/default/policy selection.
- The documented heuristic is a pure rule table: >600 characters, two distinct app
  table names, complexity keywords, pending write context, >8 previous turns or a
  previous tool limit. This branch has no conversation write-mode field.
- Classifier transport is non-streaming, bounded to four output tokens/three seconds,
  and contains only the fixed instruction and user question. PHI guards apply before
  dispatch. Errors/unexpected replies use the heuristic. ROUTE usage enters budget
  accounting; normal dispatch budgets may override the selected route.
- FallbackChain follows at most four edges on retryable failures, only before any
  text/tool start of that call. Candidates are checked for enablement, health,
  PHI/HIPAA eligibility and budgets. Without explicit links only the same LOCAL
  adapter family is considered. Provider switches rebuild neutral messages and
  discard providerState; failed attempts remain visible in usage.
- ProviderHealth persists UP/DOWN, checked-at and a 60-second connection-failure
  circuit. Local stale/unknown health uses a bounded two-second probe. Successful
  completions close the breaker. Turn rows persist route/source, fallback count and
  final provider/model; notices name only the unavailable and answering providers.
- Admin record screens, additive Postgres DDL and the flow-layout home expose these
  settings and counters without granting AI access to assistant configuration.

### P-f as built

- `AssistantModelProvider.embed` is optional and unsupported by default. The
  OpenAI-compatible implementation shares chat's JDK HTTP authentication, TLS,
  Azure deployment paths, timeout and sanitized error mapping. A request accepts
  1–64 inputs; `EmbeddingService` batches larger jobs and records each call as
  EMBED, including daily rollups and provider/model budget accounting.
- Registry rows have CHAT/EMBEDDING kinds and embedding dimensions. Legacy rows
  remain CHAT. Discovery suggests EMBEDDING for ids containing embed, bge, e5,
  gte or nomic; administrators can correct it. Chat routing/fallback/downgrades
  cannot select an embedding model. Embedding resolution is explicit and checks
  fresh provider enablement and HIPAA eligibility before dispatch.
- `assistant_rag_source` is an admin-only navigation table. Its text fields must
  exist and allow VALUES/MASKED exposure; application data access must allow READ.
  Index now and Clear index are standard streamed ETL record actions with Review
  and Result, never navigation entries. Indexing pages the UI API as the clicking
  admin, reuses get_record's masking path, chunks deterministically and compares
  hashes. Complete scans alone prune disappeared records; per-record replacement
  also removes obsolete field chunks. Configuration/model identity changes clear
  stale vectors. No source text is saved on a source or vector row.
- `VectorStore` supplies upsert/delete/search/count plus content-free chunk
  reconciliation and atomic record replacement. The in-memory implementation uses
  exact cosine; the optional pgvector module uses host JDBC, a validated instance
  schema, bound values and an HNSW cosine index. Sources from other embedding
  models are excluded before ranking. The DBA installs pgvector; existing
  installations do not require extension-installation privileges on startup.
- `KnowledgeTool` is separate from the unchanged `UiApiToolbox`. A small
  ContextBuilder hook adds it only for an enabled source, policy permission and
  UI-readable source table. Search embeds the question, requests 4× the result
  limit, then re-reads each hit through get_record with the asking user's session.
  403/404 hits contribute only to the filtered count. Safe current values produce
  snippets and `/app` citations; record refs propagate touched tables into P-c
  classification and the normal tool-call log. The citation instruction is in
  the static rules block. The result limit is capped at eight.
- PHI source content and PHI conversation queries only reach eligible embedding
  providers. Embedding budgets, kill switch and provider concurrency checks apply
  to each batch. A budget downgrade into another vector space stops embedding.
- Tests exercise real fixture UI API reads, denials, record locks, field masking,
  PHI refusal, classification, reconciliation, embedding wire contracts and a
  golden cited answer. JDBC shape/transaction tests always run; a pgvector:pg16
  Testcontainers test skips on hosts without Docker.
- P-g may invoke the manual service within a designated user's authenticated
  context. P-f supplies no scheduled runner or stored/elevated session. Multiple
  replicas should serialize manual re-indexing of a source at the host level;
  per-record pgvector replacement is transactional, while scan reconciliation is
  not a snapshot of concurrently changing application data.

### P-g as built

- `assistant_scheduled_task` is admin-only, AI exposure NONE, and appears as
  Scheduled tasks. The owner-locked, read-only `assistant_scheduled_run` child
  list retains run status, accounting and a bounded summary. Conversations gain
  read-only `scheduledTaskId` and `channel` (`panel` or `assistant/scheduled`).
  Task and run timestamps use the runtime clock; cron uses Quartz and a validated
  ZoneId, including DST. Enablement and schedule edits recompute `nextRunAt`.
- Credentials are references, never stored tokens. `credentialEnvName` must
  match `[A-Z_][A-Z0-9_]*` and the dedicated
  `allowedScheduledCredentialEnvNames` allowlist. The existing secret resolver
  supplies its value. Read-only `credentialStatus` reports presence without
  exposing token characters and is stripped on every save.
- A run sends JSON `{accessToken: ...}` to the same loopback base URL's
  `POST /qqq/v1/manageSession`. RC.5 with HTTP-only cookies returns the UUID in
  `Set-Cookie: sessionUUID`; legacy JSON `uuid` is also accepted. Only then does
  the runner resolve that UUID through the host authentication module, exactly
  the UUID lookup used by the UI request pipeline. No direct token authentication,
  automated-user session or constructed QSession exists in assistant code.
  The resolved user id must equal `runAsUserId` before any model call. MOCK in
  the integration fixture maps test tokens to the existing user-prefix sessions;
  that token adapter is test code only.
- kof22 provisions an Authentik designated account with normal QQQ permissions
  and injects an accepted long-lived token through its secret manager and
  ExternalSecret into an allow-listed environment variable. The task holds the
  variable name and QQQ user id. Host authentication must accept that token type
  as `accessToken`; an Authentik management API token is not automatically an
  application access token. Neither account nor credential is provisioned here.
- `assistantScheduledTasksRunner` registers a 60-second QScheduleMetaData when
  `scheduledTasksSchedulerName` names a host scheduler. QQQ supplies its normal
  scheduler context for assistant-table bookkeeping; no ArchUnit exception is
  necessary. Configure `uiApiBaseUrl` at startup. No browser visit is required.
- **Claim compatibility:** RC.5 UpdateInput cannot express a conditional
  `WHERE next_run_at = expected`. AssistantStore therefore claims the expected
  schedule slot using the database-enforced unique `(taskId, scheduledFor)` run
  key and advances `nextRunAt` before login/dispatch. This is a unique-insert
  optimistic claim rather than a conditional update. The memory fixture also
  serializes claim operations on its instance. The database constraint, not
  that monitor, prevents cross-replica duplicate execution. Do not remove claim
  history or deploy to a backend without atomic unique keys. A crash can lose a
  run; it never retries a claimed slot. A later tick advances an already-claimed
  slot left behind by a crash between claim and schedule update.
- The unchanged AssistantApi starts a new normal turn under the logged-in
  designated user, preserving start gates, rate limiting, P-c classification,
  P-d budgets and P-e routing/fallback. An internal context supplies the task's
  selected profile and additional cost cap. Cost includes chat, classifier,
  failed calls and knowledge-query embeddings. STOP is checked at dispatch
  boundaries; an in-flight call can exceed the cap, and no next call is sent.
- Every scheduled UI API request, including metadata and knowledge retrieval,
  carries `X-QQQ-Client: assistant/scheduled/<taskId>`. The only uiapi change is
  additive `LoopbackUiApiGateway.invokeWithClient`; custom gateways currently
  fail closed for scheduled requests because their channel contract is unknown.
  Tool-call rows also record `clientChannel`. Core channel deny enforcement
  remains pending the client-channel RC; existing AI exposure/data-access
  restrictions continue to apply on RC.5.
- Approve write mode, Revoke write mode and Run now are admin record actions
  using standard streamed ETL Review/Result. Review shows the exact prompt's
  SHA-256 and run-as user. Result rereads and compares the complete reviewed
  definition before recording the actual administrator's user id and time.
  A persisted approval hash additionally binds prompt, run-as user, credential
  reference, profile and requested mode. Editing any of these clears approval;
  ordinary saves cannot set approval or computed bookkeeping fields.
- This base has create/update/delete tools, but no W1 conversation mode/policy
  switch. Scheduled runs explicitly narrow the existing tool policy: write tools
  require both request and matching approval, a READ_WRITE table and the policy's
  allowed-tools list. All scheduled writes require panel confirmation, including
  when normal policy allows immediate writes. A pending proposal adds a waiting
  notice to the run summary; the scheduler never confirms it.
- RECORD keeps the run and conversation; NOTIFICATION and BOTH also submit an
  Alerts notification with `NotificationAudience.user(runAsUserId)` and an
  `/app/...` conversation link. Reflection targets the installed Alerts QBit's
  RC.5 notification SPI/sink, following WebhooksAlerts' optional-dependency
  pattern. Without it, a log contains run id and status only. Notification text
  contains no answer data. In HIPAA mode run summaries contain exactly
  `see conversation`; conversation payloads retain the existing encryption.
    The existing record URL is an admin-screen link. Non-admin owners can read
    their scheduled conversations in the panel's conversation list; opening a
    particular existing conversation from a notification still needs a frontend
    deep-link contract (the current panel only supports open/new-prompt links).
    This phase does not broaden existing conversation record-screen permissions.
- Failures close the run as FAILED or BLOCKED with sanitized errors and wait
  until the next schedule. All output modes retain their audit record. Tests
  exercise real manageSession and UI API requests with a fixed clock, designated
  permissions and owner locks, headers, competing runners, approval invalidation,
  notification modes, PHI refusal, encrypted HIPAA storage and cost STOP.

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

## Answer presentation (owner priority 2026-10-13)

`present_results` is always offered and reads no records. It takes a direct answer,
metrics, a table, an optional table-derived chart, filtered list links, record links
and next-step actions. The server validates metadata and the user's UI API table
visibility once per turn, formats values using QQQ field types/display formats,
computes percentages and builds every `/app` href. Unknown or denied items are
dropped with an explanation. The assistant must still retrieve every fact through
data tools before presenting it.

The [presentation part and SSE contract](answer-presentation.md) defines `summary`,
`blocks` and the complete server-built GFM `markdown` fallback. Each successful
call emits `presentation` with `{messageId, part}` and persists that part; the tool
response tells the model not to repeat its tables or links. Audit rows contain
only accepted presentation data. Normal record-data retention removes these parts.

The built-in Presentation skill is compact static prompt text in the cached rules
block. It asks for a one-sentence answer first, metrics for single numbers, tables
for breakdowns/comparisons, charts for useful shares/trends and actual filter links
whenever offering a list. Administrator profile skills append after it as before
and extend it; there is no replacement boolean or extra permission surface.

The owner's one-line table was a frontend parsing defect: Next's `AssistantMarkdown`
used react-markdown without `remark-gfm`, so table lines became a paragraph.
The renderer fix belongs to `qqq-frontend-next`; this worktree preserves real
newlines and provides structured blocks plus a proper multi-line GFM fallback.
The frontend change is maintained separately and is not verified by this build.

The shared golden task asks "How many images do I have by type?": five UI count
calls establish 81 JPEG, 20 PNG and zero other configured types, then a presentation
with two rows, 80.2%/19.8% shares, two pie points and working MIME filter links.
The same task is included in the opt-in `live` Anthropic eval suite.


## Transcript redaction (secured data §15)

`TranscriptRedactor` collects classified values from tools using `ClassificationSource`,
including core table/field metadata. Stored record fields use
`ClassifiedValueTokens.forRecordField`; criteria/proposals without a record use
`forContext`. Core selects `shred:v1`, `hmac:v1`, or `changed:v1`. Knowledge snippets
retain their source field's token. Matching is case-sensitive and includes canonical,
displayed, escaped, encoded and explicitly truncated variants.

`AssistantStore` stages content in turn memory and writes placeholders until
finalization, protecting backend writes and their audits. It redacts before truncation
and encryption. This covers messages, tool/bulk logs, replay events, pending payloads
and audit reasons; scheduled summaries derive from redacted messages. Model requests
and live SSE remain unchanged under PHI routing. Persisted text deltas are joined
before redaction, with the text in the first delta and remaining deltas empty;
sequence numbers remain stable.

Crashes or unfinished tools at timeout leave placeholders. Proposals remain
non-executable (`PREPARING`) until finalization; redacted proposals are rejected and
must be completed in the application. A core `LogRedactorInterface` hook covers all
threads until the turn and its tool workers finish, then releases the raw values.

History stays redacted; later turns re-read records. Existing transcripts are not
migrated, and arbitrary text is not inferred to be classified without a matching
observed value or classified criterion/field. Canary tests scan every assistant table,
core audits and captured logs, while proving eligible models receive original values.
Requires QQQ 4.1.0-RC.7 (`qqq#1182`); keep the PR draft until publication.
