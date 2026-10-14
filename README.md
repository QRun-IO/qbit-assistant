# QBit Assistant

An in-app AI assistant for any QQQ application. The user asks a question in a chat panel in the Next UI. The assistant reads the application's data **with exactly that user's permissions**, answers with numbers taken from the data, and links to the records and lists it used. Conversations start Read-only. Users can select Write mode when policy permits it; changes normally wait for their confirmation.

Design spec: `QRun-IO/qqq` `docs/specs/2026-10-11-qqq-assistant-qbit-design.md`.

## Modules

| Artifact | What it is |
|---|---|
| `com.kingsrook.qbits:qbit-assistant-provider-api` | Provider SPI, connection settings, messages, usage and pricing types; only Jackson annotations at runtime. |
| `com.kingsrook.qbits:qbit-assistant` | The QBit: conversation tables, turn engine, `/qqq/v1/assistant` routes (JSON + Server-Sent Events), budgets, kill switch, the provider registry, and a scripted provider for tests and demos. |
| `com.kingsrook.qbits:qbit-assistant-provider-anthropic` | The Claude provider (`com.anthropic:anthropic-java`). Add it to use Claude. The core QBit has no LLM SDK dependency. |
| `com.kingsrook.qbits:qbit-assistant-provider-openai` | OpenAI-compatible chat and embedding providers. |
| `com.kingsrook.qbits:qbit-assistant-provider-bedrock` | Claude through AWS Bedrock. |
| `com.kingsrook.qbits:qbit-assistant-provider-vertex` | Claude and Gemini through Google Vertex AI. |
| `com.kingsrook.qbits:qbit-assistant-rag-pgvector` | JDBC vector store; retains the runtime dependency for `VectorStore` and `ChunkVector`. |

### Implementing a provider outside qbit-assistant

Depend on `com.kingsrook.qbits:qbit-assistant-provider-api` at the same version as
QBit Assistant, and implement `AssistantModelProvider` and optionally
`AssistantProviderType`. No assistant runtime dependency is needed. Existing
`com.kingsrook.qbits.assistant.provider.*` imports and
`com.kingsrook.qbits.assistant.engine.ModelPricing` stay the same.
`AssistantProviderType.validate(ProviderSettings)` receives unresolved settings
(including `apiKeyEnvName`), without a database entity or resolved credentials.
Hosts register providers through `AssistantQBitConfig` in `qbit-assistant`.

Data tools use the QQQ UI API (`/qqq/v1`) with the signed-in user's session. The UI API enforces permissions and record locks; assistant data-access policy and field exposure can only narrow access. Only record-free navigation and help use the core AI tool kernel. Every tool result reaches the model wrapped as untrusted data.

## Write mode and personal preferences

The **Assistant** section on the core Preferences page controls **Start conversations
in Write mode** and **Auto-confirm low-risk changes**. It is available to users
with `assistant.hasAccess`, without requiring `assistant.admin` or permission on
the backing table. The standard visible `assistant_user_preference` table remains
available to administrators.

Clients read and partially update settings through `GET` / `PATCH
/qqq/v1/preferences/assistant`, with a PATCH body such as
`{"values":{"defaultWriteMode":true}}`. There is no user-id parameter. The former
`/qqq/v1/assistant/preferences` operations are removed. The panel reads effective
defaults from `/qqq/v1/assistant/config` under `preferences`; conversation responses
include `writeMode`. Policy can make the effective default Read-only even when
the saved preference is Write.

Write tools, including bulk profile saves and bulk loads, are absent in Read-only
mode. Write mode requires policy permission and READ_WRITE data access. Low-risk
auto-confirm is limited to single-record inserts/updates on `auto_confirm_ok`
tables, with both policy and user opt-in. Its preference is read-only with a reason
when policy disallows it. Scheduled tasks additionally require their own approval
and always wait for confirmation. The assistant client channel cannot access the
preferences section.

## Host audit attribution (X1/Y1)

Use QQQ **4.1.0-RC.8 or later** (core Preferences, qqq#1162). Until RC.8 is published, build with `-Dqqq.snapshot.version=4.1.0-PREFS-SNAPSHOT`. The QBit explicitly denies every assistant
configuration table, process, report and widget to the `assistant` channel;
runtime initialization fails if an assistant object is exposed. The same human
can still administer it through Next UI (`next-ui`). Core's security-surface
deny patterns remain in place.

Enable the audit client field in the host's core audit provider **after adding
the backing column**:

```java
new AuditsMetaDataProvider().withClientChannelField(true)
   .defineAll(qInstance, backendName, null);
```

Add nullable `audit.client_channel VARCHAR(100)` using the host's migration and
column-naming conventions. For kof22 this requires a feature-named changeset
under `changesets/features/`; this QBit does not migrate the host's audit table.
The actor remains the signed-in human; `clientChannel` distinguishes
`assistant/<turnId>`, `assistant/scheduled/<taskId>` and `next-ui`.

Apply the X1/Y1 DDL in [UPGRADE.md](UPGRADE.md) to widen the existing
`assistant_tool_call.audit_href` column to TEXT for encoded filters.
Successful record writes store `assistant_tool_call.auditHref` as an `/app/audit`
filter link over `recordId`, `auditTable.name` and `clientChannel`. The human
opens this link with their UI permissions; the assistant cannot read audits by
default. Without the audit channel field, the QBit leaves `auditHref` empty.
Confirmed scheduled actions use their original turn id when the human approves.


## RAG setup

Knowledge retrieval is optional. The vector index stores **no record content**:
only source/table/key/field/chunk identifiers, SHA-256 hashes and embeddings.
Indexing reads through the UI API as the clicking administrator. Retrieval
re-reads each hit through that same API as the asking user, silently filters
403/404 results, applies `get_record` field exposure/masking/redaction and returns
fresh snippets with `/app/...` citations. `search_knowledge` follows `allowedTools`
and is offered only with an enabled source the user may read in the UI.

1. Register the OpenAI-compatible provider module and configure the embedding
   endpoint through the existing provider settings/URL allowlist. Discover or add
   a model, set **Model kind = EMBEDDING** and its dimensions. Known discovery
   defaults are `bge-m3` (1024) and `nomic-embed-text` (768); these remain editable.
   CHAT models cannot be selected for embedding, and EMBEDDING models cannot answer
   chat turns. Each embedding batch contains at most 64 inputs.
2. Use the default `InMemoryVectorStore` for tests/small hosts, or add
   `com.kingsrook.qbits:qbit-assistant-rag-pgvector` at the QBit's version for
   persistence. The host supplies its JDBC driver and DataSource; this module
   does not depend on QQQ RDBMS internals.
3. For pgvector, have the DBA install the extension once in the chosen database:
   `CREATE EXTENSION IF NOT EXISTS vector`. Give the application a distinct schema
   per QQQ instance. Configure the store before producing the QBit:

   ```java
   PgVectorStore vectors = new PgVectorStore(dataSource, 1024)
      .withSchema("assistant_rag_my_instance");
   vectors.ensureSchema();
   config.withVectorStore(vectors);
   config.withUiApiBaseUrl("http://127.0.0.1:8000");
   ```

   The UI API URL is this server's own listening address. Configure it explicitly
   so Index now works before any chat request has taught the runtime its local
   port. The store creates a content-free `assistant_rag_chunk` table and an HNSW
   cosine index. Its fixed dimension must match every source using the store;
   different models are searched separately. No credentials go in source rows.
   See the [pgvector installation/index documentation](https://github.com/pgvector/pgvector)
   and the reference DDL in [schema-postgres.sql](docs/schema-postgres.sql).
4. Open **Assistant → Knowledge sources** as an `assistant.admin`. Add the table,
   comma-separated text fields, optional title field and QQQ query-filter JSON,
   embedding model, and chunk size/overlap (defaults 1500/200). Tables with AI data
   access NONE, `assistant_*` tables, and fields with NONE/METADATA_ONLY exposure
   are refused. MASKED fields are masked before embedding and in snippets.
5. Select **Index now**. Review makes no embedding calls; Result reports records
   seen, chunks embedded, unchanged, removed and errors. Re-running unchanged
   content sends no embeddings. A complete successful scan removes disappeared
   records; failed scans preserve unvisited entries. **Clear index** removes only
   that source's vectors. Changes after indexing are read fresh, but semantic
   ranking becomes current only after re-indexing.

Embedding usage has purpose `EMBED`, counts toward budgets, and observes the kill
switch and provider concurrency limits. PHI sources and queries in PHI
conversations require an eligible embedding provider; the eligibility flag is an
operator assertion, as for chat. Automatic budget downgrades cannot change an
embedding model's vector space, so an incompatible downgrade stops the call.

Scheduling is deferred to P-g. Its designated-user runner can call
`RagService.index` within that user's authenticated context; P-f creates no system
session, stored admin credentials, scheduler or background content crawler.
Data tools call the QQQ UI API through `UiApiToolbox` and `UiApiGateway` with the signed-in user's session. The UI enforces permissions and record security; the assistant further narrows access and caps and redacts returned values. Data tool results reach the model wrapped as untrusted data. Only record-free navigation and help use the core AI tool kernel.

## Requirements

- Java 21.
- **QQQ 4.1 with the AI tool kernel (QRun-IO/qqq#1078) and the developer-panel contract (QRun-IO/qqq#1076).** The first QQQ 4.1 RC that contains both is the minimum. `qqq-middleware-javalin` is needed for the routes. Until that RC exists, build against a local QQQ build. See [Build](#build).
- A backend for the assistant's tables, e.g. the app's RDBMS backend.
- For Claude: an Anthropic API key held in the application's secret store.

## Install

```xml
<dependency>
   <groupId>com.kingsrook.qbits</groupId>
   <artifactId>qbit-assistant</artifactId>
   <version>${qbit-assistant.version}</version>
</dependency>
<dependency>
   <groupId>com.kingsrook.qbits</groupId>
   <artifactId>qbit-assistant-provider-anthropic</artifactId>
   <version>${qbit-assistant.version}</version>
</dependency>
```

```java
AssistantQBitConfig config = new AssistantQBitConfig()
   .withBackendName("rdbms")                                     // where the assistant_* tables live
   .withTableMetaDataCustomizer(snakeCaseCustomizer)             // optional: backend names for your schema
   .withModelProvider(AnthropicModelProvider.fromEnvironment())  // fallback while no provider row exists
   .withProviderType(new AnthropicProviderType());               // offered on the Providers settings screen

new AssistantQBitProducer().withQBitConfig(config).produce(qInstance);
```

`produce` does the following:
- Registers the QBit.
- Adds the tables, the `assistant_user_id` security key type, the `assistantRetention` and **Test connection** processes, and the **Assistant** app with its home.
- Builds the runtime.
- Registers the `/qqq/v1/assistant/*` routes when Javalin is on the classpath.
- Registers the record Developer-screen panel when QQQ core has the developer-panel contract.

Create the tables in your database: see [Tables](#tables). Then grant the permissions.

### The API key

The key never goes in config, tables or logs. It lives in the application's secret store and reaches the server as an environment variable (for kof22: Infisical, then an ExternalSecret, then the pod env).

- **Provider settings rows** (recommended) name the variable: `assistant_provider.apiKeyEnvName`, default the type's own (`ANTHROPIC_API_KEY`). The server reads the variable when it builds the client.
- Without a row, the provider registered in code is used: `AnthropicModelProvider.fromEnvironment()` reads `ANTHROPIC_API_KEY`, and `AnthropicModelProvider.builder().apiKey(valueFromYourSecretStore).build()` takes the key from your own secret store (it also accepts `"${env.NAME}"`).

The provider's `toString` never contains the key. Its errors carry only the HTTP status and the API error type.

## Provider settings (the Providers table)

Admins set up providers on the **Providers** screen (`assistant_provider`), a standard QQQ table:

| Field | Meaning |
|---|---|
| Name, Type, Model | The type is one of the registered provider types (`withProviderType`; Anthropic now, others plug in the same way). The model list comes from the type and the price table. An empty model uses the policy's main model. |
| Enabled, Default | The assistant uses the default enabled provider, else the first enabled one. Marking one default unmarks the others. With no enabled row, the provider registered in code is used. |
| API Key Variable | The **name** of the environment variable that holds the key, never the key. Only allowed names are accepted: each registered type's default plus `allowedApiKeyEnvNames`. A pasted key (or `DATABASE_PASSWORD`) is refused and nothing is stored. |
| API Key | Read-only, computed on every read: `Set • …abcd (from ANTHROPIC_API_KEY)` or `Not set (...)`. It shows the last four characters at most. No read path (screen, API, query, export) returns the key. |
| Base URL Override | Empty uses the provider's own API. Any other URL must be listed in `allowedBaseUrls`, so a row cannot send the key to a host of an admin's choosing. |
| Monthly Cost Cap (USD) | When this provider's cost this month (UTC) reaches the cap, new requests are refused (429 `BUDGET`) and a running answer stops before its next model call. |
| Max Output Tokens Per Request, Max Concurrent Requests | Caps one model call's output, and how many calls run at once on this server. |
| Last test | Status, time, latency, the model that answered, and the error text, from **Test connection**. |

**Why a reference and not an encrypted column.** QQQ core has no encrypted or secret field type. The key-provider and cipher work in the QQQ 4.1.0-RC.6 batch (QRun-IO/qqq#1123, #1107) encrypts backup artifacts, not table columns, and is not released. The design spec (§2.8) also says keys never go in tables. A reference keeps the key in the secret store that already holds it, so rotating it there is the "replace key" step, and the database never holds one.

**Test connection** is a record action on a provider (select one or more). It is a standard streamed ETL: Review lists what will be called and anything wrong with the row (missing key, disallowed variable or URL) before any call. Result has one line per provider: `connected to claude-sonnet-5-5 in 812 ms`, or the error with its kind and HTTP status. It makes one short live call (a 1-sentence prompt, at most 32 output tokens, effort low), saves the result on the row, and records the call in `assistant_usage` with purpose `TEST`.

## The app home

The **Assistant** app's home is the one place for the assistant's health, usage and cost (no other dashboard repeats it). All of it comes from the tables below, read as the viewing admin; an empty table gives an empty state, never sample data.

- **Health:** is it answering (kill switch, default provider, key present, last test), the 7-day error rate of model calls, cost and requests this month against the instance's monthly limit.
- **Providers:** one row per provider: type, model, masked key status, last test, cost this month against its cap, limits, and any problem with its settings.
- **Usage, last 30 days:** bars of model requests per day, totals (conversations, requests, tokens in and out, cost) and the last 7 days in words.
- **Costs this month:** month to date against the policy limit and each provider cap, then by model. Cost is tokens times the model's price from **Model prices**.
- **By user, this month**, **Top tools** (calls, failures, average time) and **Recent failures** (model calls and tool calls, linked).

The nav lists the home and the main tables: **Providers**, **Conversations**, **Model prices** and **Policy**. Messages, turns, tool calls, feedback and model calls are child lists on a conversation; model calls are also listed on their provider. Retention and Test connection are table and record actions, never nav entries.

## Model prices

`assistant_model_price` holds USD per million tokens per provider type and model: input, output, cache read, and cache writes (5 minute and 1 hour). Empty cache prices default to 0.1x, 1.25x and 2x the input price. The reference DDL seeds the Anthropic list prices of 2026-10. Edit a row to change what later calls cost; past rows keep the cost they were recorded with. A model with no row uses the `pricing` config map, else costs 0.

## Configuration

| Setting | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Per-app switch. The environment variable `QQQ_ASSISTANT_ENABLED=false` also turns it off. |
| `backendName` | (required) | Backend of the `assistant_*` tables. |
| `providerName` | `anthropic` | Which registered provider to use. |
| `models` | Sonnet 5.5 at effort `medium` | `mainModel`, `mainEffort`, `mainMaxOutputTokens` (16000). The escalation (Opus 5.5, `high`) and summary (Haiku 5.5) slots are reserved for phase 2. Model ids are the exact strings `claude-sonnet-5-5`, `claude-opus-5-5` and `claude-haiku-5-5`. |
| `defaultExposure` | `VALUES` | The kernel's default exposure. Use `METADATA_ONLY` for high-sensitivity apps. |
| `maxToolCallsPerTurn` | 25 | Tool calls allowed in one answer. |
| `maxParallelToolCalls` | 4 | Independent read tools that run at the same time. |
| `maxRowsToModel` / `maxCellChars` | 50 / 500 | Caps on what the model sees (the kernel applies them). |
| `maxTurnSeconds` | 300 | Wall-clock limit for one answer. |
| `budgets` | none | `userDaily{Tokens,CostMicros}{Soft,Hard}` and `instanceMonthly{Tokens,CostMicros}{Soft,Hard}`. A soft limit adds a warning. A hard limit refuses new turns (429) and stops a running turn after its current step. Tokens counted are uncached input, cache writes and output. Cost is in micro-USD. |
| `maxTurnsPerUserPerMinute` / `maxConcurrentTurnsPerUser` | 10 / 1 | Rate limits. |
| `conversationRetentionDays` / `toolResultRetentionDays` / `approvalRetentionDays` | 90 / 30 / 365 | Used by `assistantRetention`. |
| `retentionSchedulerName` | none | Runs `assistantRetention` daily on that scheduler. |
| `ownerSecurityKeyType` | `assistant_user_id` | Key type of the owner record locks. |
| `pricing` | Anthropic list prices, 2026-10 | USD per million tokens per model, used for cost and budgets. |
| `allowedWidgetNames` | none | Dashboard widgets `render_widget` may render. The kernel denies every widget not on this list. This config list is the **only** way to allow a widget: QQQ widget meta-data has no supplemental hook, so there is no per-widget "assistant-safe" flag. Allow only widgets that render the same data for every user who can see them. |
| `hiddenWidgetNames` | none | Widgets that are never rendered, even when allowed. |
| `customRedactionPatterns` | none | Extra regular expressions the kernel redacts from tool results. |
| `killSwitchPollMillis` | 2000 | How often a running turn re-checks the kill switch while it waits on the model. |
| `providerTypes` | none | `withProviderType(...)`: the types the Providers screen offers (`AnthropicProviderType`). |
| `allowedApiKeyEnvNames` | none | Extra environment variable names a provider row may name as its key. Each type's default is always allowed. |
| `allowedBaseUrls` | none | Base URL overrides a provider row may use. The type's own URL is always allowed. |
| `secretResolver` | `System::getenv` | How a key variable name becomes the key (a host secret store, or tests). |
| `providerTestTimeoutSeconds` | 30 | How long Test connection waits for the model. |
| `homeMaxRows` | 20000 | Rows per table the app home reads for its period; it says when totals are partial. |
| `tablePathPrefix` | `/app` | The front of the links the app home makes to tables and records. |
| `emailExternalApproved` | `false` | Stands in for the EXTERNAL approval rule (not built yet): scheduled-task result email is sent only when `true`. |
| `emailRecipientResolver` | owner id if it is an address | Hook: task owner user id to email address. |

At runtime, an admin can override the kill switch, the main model and effort, and the budgets in the single **`assistant_policy`** row.

A running turn notices the kill switch in two ways:
- It re-reads the switch before every model call and tool step.
- A watcher polls it every `killSwitchPollMillis` while the turn waits on the model.

When a turn sees the switch on, it cancels every turn running on that replica. If the policy row cannot be read, the switch counts as **on**: it fails closed.

## Permissions

| Permission | Grants |
|---|---|
| `assistant.hasAccess` | Use the panel. It grants **no data access**: the assistant reads only what the user can read anyway. |
| `assistant.admin` | The Assistant app (home, settings and audit tables), Test connection and the retention process. |

There is no assistant-specific data permission. Everything the kernel reads is checked against the user's normal table permissions and record locks. The QBit never uses a system session (an ArchUnit test enforces this).

When the QBit is enabled, it registers the supplemental instance meta-data `assistant`. The v1 `/metaData` response publishes it as `supplementalInstanceMetaData.assistant: {"available": true, "version": 1, "basePath": "/qqq/v1/assistant"}`. The Next UI only probes `/config` when that object is present.

### Record locks on the assistant's own tables

Conversation data belongs to its user. Every user-owned table has a record lock on `userId` with key type `assistant_user_id`:
- The panel routes add the user's own id to their session, so users only ever see their own rows.
- Admins see everyone's rows in the Assistant app through the key type's all-access key, `assistant_user_id_all_access`. Grant that key to `assistant.admin` holders from your authentication customizer:

```java
// in your QAuthenticationModuleCustomizerInterface.customizeSession(...)
AssistantSecurity.applySessionKeys(qSession, assistantConfig);
```

## Data policy

What reaches the model provider:
- the tool definitions
- a fixed safety prompt
- an app digest (labels and descriptions, no data)
- the user's name, timezone and readable-table list
- the conversation's text
- tool results produced by the kernel.

The kernel applies these rules to tool results:
- Hidden fields, password fields and fields with a security lock are never sent.
- Exposure `METADATA_ONLY` and `MASKED` fields are filtered or masked.
- Rows are capped at 50 and cells at 500 characters.
- Secrets such as keys, JWTs and card numbers are redacted.
- The `assistant_*` tables are never visible to the model.

Old tool results are cleared from the context of later turns; only one-line summaries remain.

Stored in your database:
- every message
- every tool call: sanitized input, outcome, permission check, row count, duration, redactions, and the model-visible result. After `toolResultRetentionDays`, the result is cleared, stream events are deleted, and render payloads and record references are removed from stored messages. Summaries and answer text stay until `conversationRetentionDays`.
- turn accounting: tokens, cache reads and writes, and cost
- streamed events, kept for replay
- feedback
- daily usage.

Logs never contain message content.

## HTTP API

Base path `/qqq/v1/assistant`. Every request is authenticated through the app's normal session (cookie, bearer token or API key). Request bodies must be JSON.

| Method | Path | |
|---|---|---|
| GET | `/config` | Feature probe: `{enabled, reason?, shortcut, model, limits, features}`. A 404 means the QBit is not installed. |
| GET / POST | `/conversations` | List your conversations (`?status=ARCHIVED`), or create one. |
| GET / PATCH / DELETE | `/conversations/{id}` | Load with paged messages (`?before=&limit=`), rename / pin / archive, or delete. |
| POST | `/conversations/{id}/turns` | Ask a question. The response is an SSE stream. |
| GET | `/turns/{id}/events?after={seq}` | Replay a turn's events after a sequence number, then follow it live. |
| POST | `/turns/{id}/cancel` | Stop an answer. |
| POST | `/messages/{id}/feedback` | Rate an answer: `{rating: 1 or -1, reason?, comment?}`. |

The stream sends these events, each with a `seq`:
- `message.start`, `text.delta`
- `tool.start`, `tool.result` (with `render` and `recordRefs`)
- `presentation` (`messageId`, `part`: summary, blocks and server-built GFM markdown)
- `ui.navigate`, `notice`
- `usage`, `message.end`
- `error`
- `ping` every 15 seconds.

The stream ends after `message.end`. Thinking is never streamed.

### SSE behind a proxy (Traefik, nginx)

- The responses carry `Cache-Control: no-cache` and `X-Accel-Buffering: no`. Do not compress or buffer `text/event-stream`.
- Give the routes an idle timeout longer than the 15-second ping interval and a total timeout of at least `maxTurnSeconds`. For Traefik, raise the entrypoint's `respondingTimeouts` (`readTimeout`, `writeTimeout`, `idleTimeout`) or put the assistant routes on a router whose transport allows it. Don't add a buffering middleware to these routes.
- A dropped connection does not stop the answer. The client resumes with `/turns/{id}/events?after=<last seq>`.

## Tables

All of these are standard, visible QQQ tables in the Assistant app. The model never reads any of them (AI exposure `NONE`).

| Table | Holds |
|---|---|
| `assistant_conversation` | Conversations. |
| `assistant_message` | Messages (`partsJson`). |
| `assistant_turn` | Turn accounting. |
| `assistant_turn_event` | Stream events, for replay. |
| `assistant_tool_call` | The audit of every tool call. |
| `assistant_feedback` | Feedback on answers. |
| `assistant_policy` | The kill switch and overrides. |
| `assistant_usage_daily` | Usage rollups: per user per day, plus an instance row whose `userId` is null. |
| `assistant_provider` | Provider settings: type, model, key variable (never the key), limits, last connection test. |
| `assistant_model_price` | USD per million tokens per model, used for every call's cost. |
| `assistant_usage` | One row per model call (a turn step or a connection test): user, conversation, turn, provider, model, status, latency, tokens, cost. Locked to its user like the other audit tables. |

Table names follow the QRun-IO naming rule: lowercase snake_case with the `assistant_` prefix, the same as the physical tables in `docs/schema-postgres.sql` (the reference DDL). Field names are camelCase. Map them to your columns with a table customizer. Upgrading from 0.1.0-RC.1, which used camelCase table names and the `assistantUserId` key type: see [UPGRADE.md](UPGRADE.md).

## Build

```bash
./mvnw verify                                                    # against the committed QQQ version
./mvnw verify -Dqqq.snapshot.version=4.1.0-PREFS-SNAPSHOT        # local core Preferences build until RC.8 is published
```

Two test groups run in every build and fail it on any error:
- the golden evals (JUnit tag `golden`): scripted conversations against a seeded fixture app
- the permission red team (tag `redteam`).

The **live** evals run the same golden tasks against Claude. They are skipped unless both `ANTHROPIC_API_KEY` and `-Dassistant.live=true` are set:

```bash
ANTHROPIC_API_KEY=... ./mvnw verify -Dassistant.live=true -Dtest.excludedGroups= -Dgroups=live -Djacoco.skip=true -pl qbit-assistant-provider-anthropic -am
```

## Providers

Add `com.kingsrook.qbits:qbit-assistant-provider-openai` at the same version as
the QBit and register the adapter alongside any Anthropic adapter:

```java
config.withProviderType(new OpenAiCompatibleProviderType());
```

`OpenAiCompatibleProviderType` is in
`com.kingsrook.qbits.assistant.provider.openai`. Its installed type id is
`openai-compatible`; provider rows choose OPENAI, AZURE_OPENAI, OPENROUTER,
OLLAMA, LM_STUDIO, VLLM, LLAMA_CPP or OPENAI_COMPATIBLE as their kind.

- Local kinds use their localhost endpoint by default and need no key. Leave
  `apiKeyEnvName` empty. An auth proxy can use an explicitly allowed key env name.
- Cloud defaults use `OPENAI_API_KEY`, `AZURE_OPENAI_API_KEY` or
  `OPENROUTER_API_KEY`. Values stay in the host's secret store.
- An override must match an entry in `config.withAllowedBaseUrls(Set.of(...))`.
  Azure and OPENAI_COMPATIBLE have no default URL and always require an entry.
  Redirects are not followed. Azure uses the deployment model id and defaults
  `apiVersion` to `2024-10-21`.
- **Discover models** adds a provider's Models child list; existing edits survive.
  Large cloud catalogs (over 20 models) start disabled. **Test connection** is
  available on both providers and models and records a TEST usage row.
- A selected registered model must be enabled. With no model selected, the first
  enabled registry model wins; providers with no model rows retain legacy selection.
  Registry prices override type/model prices, then configured prices. Unpriced
  local models cost zero. Capabilities can be adjusted per registered model.

For Ollama behind a TLS proxy with a private CA and bearer authentication:

```java
config.withProviderType(new OpenAiCompatibleProviderType())
   .withAllowedBaseUrls(Set.of("https://llm.example.lan:8443/v1"))
   .withAllowedApiKeyEnvNames(Set.of("LOCAL_LLM_API_KEY"))
   .withTrustedCaBundleFile("local-llm", Path.of("/etc/app/llm-root-ca.pem"));
```

Set `LOCAL_LLM_API_KEY` in the server environment from your secret store. In the
provider record choose kind `OLLAMA`, base URL `https://llm.example.lan:8443/v1`,
`apiKeyEnvName = LOCAL_LLM_API_KEY`, and `tlsCaBundle = local-llm`. Only the names
are stored. `withTrustedCaBundle("local-llm", pem)` also accepts one or more PEM
certificates directly at startup. Invalid PEM fails configuration immediately.
The named bundle adds to the JVM roots; hostname verification stays enabled.
Leave `tlsCaBundle` empty to use JVM trust alone. Restart after changing a CA file.

Default tests use localhost mocks. To exercise an existing development server,
set `ASSISTANT_LIVE_OPENAI_BASE_URL` and `ASSISTANT_LIVE_OPENAI_MODEL`, optionally
`ASSISTANT_LIVE_OPENAI_KIND` (default OLLAMA), `ASSISTANT_LIVE_OPENAI_KEY_ENV`
(the variable name containing the key), and `ASSISTANT_LIVE_OPENAI_CA_FILE`
(a PEM bundle path). Unset or unreachable endpoints, including TLS failures, skip
the live test. Run:

```sh
./mvnw verify -pl qbit-assistant-provider-openai -am -Dtest.excludedGroups= -Dgroups=live -Djacoco.skip=true
```

### AWS Bedrock (Claude)

Add `com.kingsrook.qbits:qbit-assistant-provider-bedrock` at the QBit version and
register `com.kingsrook.qbits.assistant.provider.bedrock.BedrockProviderType`:

```java
config.withProviderType(new BedrockProviderType());
```

Create a provider row with type `bedrock`, kind `BEDROCK`, and the required AWS
`region` (for example `us-east-1`). Leave `apiKeyEnvName` empty. The server uses
the AWS default credential chain: environment variables, profiles and container
credentials (ECS/EKS Pod Identity) work with the transitive dependencies.
**For IRSA / web-identity credentials, the host application must add
`software.amazon.awssdk:sts` to its runtime classpath**, matching the transitive
AWS SDK version. The default chain picks it up when present; this adapter does
not include STS or the full AWS SDK. Credentials are never stored in tables.

Leave the base URL empty for the SDK's
`https://bedrock-runtime.<region>.amazonaws.com` endpoint. Overrides, including
VPC endpoints, must be listed in `withAllowedBaseUrls`. Model ids are Bedrock
Claude ids or inference profile ids, for example
`us.anthropic.claude-sonnet-4-5-20250929-v1:0`. The model must be available to the
server's AWS role in that region.

### Google Vertex AI (Claude, Gemini)

Add `com.kingsrook.qbits:qbit-assistant-provider-vertex` at the QBit version and
register `com.kingsrook.qbits.assistant.provider.vertex.VertexProviderType`:

```java
config.withProviderType(new VertexProviderType());
```

Create a row with type `vertex`, kind `VERTEX`, required `region` (for example
`us-east5`) and `cloudProject` (the GCP project id). Leave `apiKeyEnvName` empty.
The server uses Google Application Default Credentials, such as
`GOOGLE_APPLICATION_CREDENTIALS` or workload identity. Access tokens are refreshed
when expired, held only in memory, and obtained afresh for each Gemini request.

Models beginning `claude-` (for example `claude-sonnet-4-5@20250929`) use the
Anthropic Vertex backend's `streamRawPredict` API. All other model ids use Vertex's
OpenAI-compatible endpoint:

```text
https://{region}-aiplatform.googleapis.com/v1/projects/{project}/locations/{region}/endpoints/openapi
```

Unprefixed ids, such as `gemini-2.5-flash`, are sent as `google/gemini-2.5-flash`;
already-prefixed ids are preserved. Gemini requests omit `strict`, including when
a tool opts in. An allow-listed base URL overrides the Claude API origin or the
complete OpenAI-compatible base URL, according to the selected model. Use separate
provider rows when an override needs different URL prefixes for the two APIs.

Both cloud types default to CLOUD and expose a short static model catalog.
**Discover models** returns those defaults; it does not call Bedrock's
`ListFoundationModels` (which needs the control-plane SDK) or Vertex Model Garden.
Health probes are unsupported; **Test connection** performs the existing one-shot
completion. Model access and availability depend on the configured region/project.
`hipaaEligible` is an administrator-recorded fact: a BAA and the associated legal
and operational requirements remain owner responsibilities. The flag does not
establish eligibility or implement PHI routing.

The default build tests both clouds using localhost only, fake credentials, AWS
binary event-stream framing, Claude SSE and Gemini Chat Completions, including
engine/tool golden turns. No cloud credentials or live endpoints are required.

## License

Apache 2.0.


## Email through qbit-email

When the optional [qbit-email](https://github.com/QRun-IO/qbit-email) is on the classpath **and** produced in the instance, a finished scheduled-task run can be emailed to its owner. Without it nothing changes: `AssistantEmail.sendScheduledResult` returns `NOT_AVAILABLE`. The dependency is `optional`; every reference to it is in `AssistantEmailSender`.

This integration uses qbit-email **0.1.0-RC.1**. Scheduled tasks (P-g) are available, but the runner does not yet wire email delivery. Hosts can call `AssistantEmail.sendScheduledResult(qInstance, config, new ScheduledResultNotice(...))` after a run, supplying its owner, classification, absolute run link and explicit opt-in as `emailResult`. Persisting a task-level email opt-in and wiring the runner remain a separate integration step.

Gates, in order (each returns a status and queues nothing):
1. the task opted in (`NOT_REQUESTED`);
2. qbit-email is installed (`NOT_AVAILABLE`);
3. `emailExternalApproved` is `true` (`NOT_APPROVED`). The EXTERNAL approval rule exists only in the spec, so this explicit flag gates it until the rule is built;
4. a recipient resolves (`NO_RECIPIENT`): `emailRecipientResolver` maps the owner's user id to an address; by default an owner id that already is an address is used.

What is sent, one message per recipient, idempotency key `assistant:scheduled:<run id>`, linked record the run:
- result classified PUBLIC (or none): template `assistant_scheduled_result` (NOTIFICATION, Markdown, registered on first send and never overwritten) with the task name, run time, a summary cut to 280 characters and a link to the run. The full model output is never emailed;
- result classified above PUBLIC (core's `DataClassification`): only the link, through qbit-email's `email_secure_message` pattern. No task name, summary or output.

The outbox result maps to `QUEUED`, `DUPLICATE`, `SUPPRESSED` or `REFUSED` (for example a link that is not absolute http(s)). Addresses and bodies are never logged.

## Data classification and HIPAA mode

Classify application tables and fields in core's AI metadata (`AiTableMetaData` / `AiFieldMetaData`, QQQ 4.1.0-RC.6), or in the host configuration (a configured value wins when both are set):

```java
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;

AssistantQBitConfig config = new AssistantQBitConfig()
   .withDataClassification("patient", DataClassification.PHI)
   .withDataClassification("patient", "id", DataClassification.PUBLIC)
   .withDataClassification("patient", "email", DataClassification.PII)
   .withDataClassification("patient", "status", DataClassification.PUBLIC)
   .withHipaaMode(true)
   .withHipaaRetentionDays(2190)
   .withConversationEncryptionKeyEnvName("ASSISTANT_CONVERSATION_KEY");
```

Set that environment variable through your secret manager to a base64-encoded,
random 256-bit (32-byte) key. Supply only its **name** in config, never the key.
The existing server-side `withSecretResolver` hook also resolves this reference.
Startup validates the key. HIPAA mode defaults off; encryption can also be enabled
with the key reference while HIPAA mode is off.

The levels are core's `DataClassification` (PUBLIC < CONFIDENTIAL < PII < PHI); the
qbit's former stand-in enum is gone. Unconfigured tables default to PUBLIC; a field
falls back to its table unless explicitly overridden. A field subset uses its
selected fields; unrestricted reads and record page contexts use the highest
classification across the table and its fields. Returned columns and record-label
fields also count: tools include a primary key even for subsets, so classify it
explicitly if the host considers it safe for that subset. Cross-table results with unknown
tables use the highest classification anywhere. Metadata-only tools remain PUBLIC.
The assistant's own tables remain denied to tools.

The default `ClassificationSource` reads core's metadata (`AiDataClassifications`,
`AiTableMetaData`, `AiFieldMetaData`) merged with the configured map; configuration
wins when both are set. Hosts can still supply `withClassificationSource(ClassificationSource)`.

PHI is sent only to a persisted provider row explicitly marked `hipaaEligible`.
Every model dispatch rechecks eligibility, including calls after tools and strict
schema retries. Blocks end the turn with `PHI_BLOCKED` or `HIPAA_PROVIDER`; the
PHI tool result is never sent to that provider. There is no automatic fallback.
A conversation keeps its highest classification, so moving to a PUBLIC page does
not make existing PHI history safe for a non-eligible provider.

HIPAA mode also refuses non-eligible providers at resolution and when enabling
settings rows, disables code-only fallback, requires encrypted conversation
storage, and suppresses provider error details in logs and turn errors. The app
home displays HIPAA mode and PHI retention. The eligibility flag records the
operator's decision (including any applicable BAA); it does not establish one.

Both modes audit each PII/PHI model call and every routing block in
`assistant_data_audit` (admin read-only, owner-locked, on the conversation screen).
PHI conversations use `hipaaRetentionDays` even with HIPAA mode off. A null value
uses normal retention; tool-result/replay cleanup remains independently configured.
See [UPGRADE.md](UPGRADE.md) for schema changes, plaintext migration, and key handling.

## Data-driven budgets

Admins configure **Assistant > Budgets** for UTC DAY or MONTH allowances in USD or
TOKENS, scoped to INSTANCE, USER, ROLE (a permission), PROVIDER or MODEL. Provider
and model scope values are registry record ids. Warnings default to 80%; at 100%
the default action is DOWNGRADE, and the hard ceiling defaults to 120% (always
STOP). Multiple budgets apply together and STOP wins.

An explicit downgrade model is optional. Otherwise the cheapest enabled usable
model is selected, preferring LOCAL providers. PHI never goes to a provider that
is not HIPAA-eligible. No eligible target means STOP. Administrators can grant
extra allowance or temporary unlimited use from the budget's Overrides child list,
with a required reason and expiry; the session sets the grantor.

Budget state changes appear once per scope/period/state in the Events child list,
as panel notices and, through core QAlerts, bell alerts when an alerts sink is installed (logging otherwise).
The Assistant home shows budgets, overrides, daily cost and tokens, and breakdowns
by user, model, provider and tool. Existing `AssistantBudgets`/policy limits and
provider monthly caps continue to apply independently of these rows and overrides.
See [UPGRADE.md](UPGRADE.md) for the required additive schema.


### Profile routing and provider fallback

On an Assistant Profile, **Model routing** defaults to OFF. HEURISTIC selects the
complex slot for long questions (>600 characters), two mentioned app tables,
analysis/comparison/explanation/trend/planning requests, pending writes, more than
eight previous turns, or a previous tool limit; otherwise it selects the simple
slot. CLASSIFIER uses an optional registry model with just the question and a
fixed instruction, four output tokens and a three-second timeout. An invalid reply,
error, unavailable classifier or PHI-ineligible classifier uses HEURISTIC. ROUTE
usage counts toward budgets. Empty model slots keep the provider/policy default;
budgets may downgrade either choice. Without a profile, enabled code-level
`AssistantModelRouting.escalation*` supplies the complex default.

A model's **Fallback model** points to its next candidate, including another
provider. Chains are cycle-free and bounded to four hops. Only retryable failures
before any text or tool start from that call permit fallback. Disabled, down,
PHI-ineligible or budget-blocked candidates are skipped. Without explicit links,
only another LOCAL model in the same adapter family is eligible; local requests
never automatically move to cloud. Provider state/signatures are removed when
switching, preserving neutral tool history. Each attempted call is audited as usage.

NETWORK/TIMEOUT marks a provider DOWN for 60 seconds; success marks it UP. Local
health is probed when unknown/stale with a two-second timeout. Provider cards show
health and down-until, failures show fallback totals, and turn records show the
route and final destination. See [UPGRADE.md](UPGRADE.md) for the additive schema.

## Scheduled tasks

Administrators configure recurring prompts in **Assistant → Scheduled tasks**.
Tasks use Quartz cron syntax (for example `0 0 8 * * ?`) and an IANA timezone
(for example `America/Chicago`; default `UTC`). Tasks start disabled.

```java
config.withUiApiBaseUrl("http://127.0.0.1:8000")
   .withScheduledTasksSchedulerName("hostScheduler")
   .withAllowedScheduledCredentialEnvNames(Set.of("ASSISTANT_SCHEDULED_TOKEN"));
```

Provide a long-lived access token accepted by the host's authentication provider
in that environment variable. Store only its **name** on the task and set
`runAsUserId` to the corresponding QQQ user id. The runner posts the token to
`/qqq/v1/manageSession`, reads its session cookie and verifies the returned
session's user before starting a new conversation. No system session reads user
data. Configure the loopback URL at startup; a scheduled job cannot rely on a
browser having visited first.

For kof22, provision a designated Authentik account with the intended application
permissions, obtain a token that kof22's QQQ authentication provider accepts as an
`accessToken`, and inject it through the existing secret-manager/ExternalSecret
pipeline. An Authentik management API token is suitable only if the host's auth
adapter explicitly accepts it. Rotate the token in the secret manager, never in
the task table. This feature does not provision an account or token.

**Run now**, **Approve write mode** and **Revoke write mode** are Review/Result
record actions. Write approval names the designated user and displays the exact
prompt SHA-256. Changing prompt, user, credential reference, profile or write-mode
request clears approval. Scheduled writes always wait for that user's panel
confirmation, even if normal policy disables confirmation.

Each run keeps an owner-locked audit row and a normal conversation. RECORD adds
no notification; NOTIFICATION and BOTH also notify the designated user through
the optional Alerts QBit SPI. Without Alerts, notification delivery is log-only.
HIPAA summaries are always `see conversation`. Cost caps stop at dispatch
boundaries, so the last completed call can cross the cap; no further call starts.
Apply the [P-g migration](UPGRADE.md#p-g-scheduled-tasks) before enabling jobs.
### Answer presentation

The always-available `present_results` tool turns retrieved results into metrics,
tables, charts and actionable in-app links. The server validates the user's UI API
visibility, formats field values, computes shares and builds all links. It emits
and stores a `presentation` part with a complete GFM Markdown fallback. Profile
skills can extend the built-in presentation instructions. See the
[part, stream and input contract](docs/answer-presentation.md).

The image-breakdown golden task also runs in the existing opt-in live provider suite:
`./mvnw verify -Dassistant.live=true -Dtest.excludedGroups= -Dgroups=live -Djacoco.skip=true -pl qbit-assistant-provider-anthropic -am`
(with `ANTHROPIC_API_KEY` set). Live calls are excluded from normal verification.

### Record history and the assistant channel

Core's default `ClientChannelsMetaData` denies `audit*` to the `assistant`
channel. Accordingly, `get_record_history` is offered only when all three audit
tables it queries (`auditTable`, `audit`, and `auditDetail`) exist and are not
channel-denied, including their effective permission rules. A host that intends
to expose record history can opt in during instance configuration:

```java
new AssistantQBitConfig()
   .withRecordHistory(true);
```

This is an owner decision and a hard wall: record history defaults to disabled;
**kof22 does not opt in**. On QQQ RC.6, the opt-in removes exactly `audit*`
from the assistant's denied patterns (the former manual configuration step).
RC.6 does not add a separate channel write denial for audits. On later RCs that
include QRun-IO/qqq#1193, it calls `withAssistantAuditReads()`, which allows audit
reads but denies audit writes. The user's own audit permissions still apply.
Other patterns, including `worm_audit_*`, `assistant_*`, and security surfaces,
remain untouched, as do explicit object and permission-rule denials.

History visibility uses core's `isDenied` read/all-operation check. RC.6 has no
operation argument; the later write-only deny patterns must remain separate
from that check so they do not hide the read tool.
