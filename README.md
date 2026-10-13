# QBit Assistant

An in-app AI assistant for any QQQ application. The user asks a question in a chat panel in the Next UI. The assistant reads the application's data **with exactly that user's permissions**, answers with numbers taken from the data, and links to the records and lists it used. This first version only reads data and opens screens. It cannot change anything.

Design spec: `QRun-IO/qqq` `docs/specs/2026-10-11-qqq-assistant-qbit-design.md`.

| Artifact | What it is |
|---|---|
| `com.kingsrook.qbits:qbit-assistant` | The QBit: conversation tables, turn engine, `/qqq/v1/assistant` routes (JSON + Server-Sent Events), budgets, kill switch, the provider SPI, and a scripted provider for tests and demos. |
| `com.kingsrook.qbits:qbit-assistant-provider-anthropic` | The Claude provider (`com.anthropic:anthropic-java`). Add it to use Claude. The core QBit has no LLM SDK dependency. |

The data access itself is done by the AI tool kernel in QQQ core (`com.kingsrook.qqq.backend.core.actions.ai.AiToolKernel`). The kernel checks table permissions and exposure rules, applies record security, and caps and redacts what the model sees. Every tool result reaches the model wrapped as untrusted data.

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
./mvnw verify -Dqqq.snapshot.version=4.1.0-ASSISTDEV-SNAPSHOT     # against a local QQQ build with #1078 + #1076
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

## License

Apache 2.0.
