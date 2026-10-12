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
   .withBackendName("rdbms")                                     // where the assistant* tables live
   .withTableMetaDataCustomizer(snakeCaseCustomizer)             // optional: backend names for your schema
   .withModelProvider(AnthropicModelProvider.fromEnvironment()); // key from ANTHROPIC_API_KEY

new AssistantQBitProducer().withQBitConfig(config).produce(qInstance);
```

`produce` does the following:
- Registers the QBit.
- Adds the tables, the `assistantUserId` security key type, the `assistantRetention` process and the **Assistant Admin** app.
- Builds the runtime.
- Registers the `/qqq/v1/assistant/*` routes when Javalin is on the classpath.
- Registers the record Developer-screen panel when QQQ core has the developer-panel contract.

Create the tables in your database: see [Tables](#tables). Then grant the permissions.

### The API key

The key never goes in config, tables or logs. There are two ways to supply it:
- `AnthropicModelProvider.fromEnvironment()` reads `ANTHROPIC_API_KEY`.
- `AnthropicModelProvider.builder().apiKey(valueFromYourSecretStore).build()` takes the key from your own secret store. It also accepts the reference form `"${env.NAME}"`.

The provider's `toString` never contains the key. Its errors carry only the HTTP status and the API error type.

## Configuration

| Setting | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Per-app switch. The environment variable `QQQ_ASSISTANT_ENABLED=false` also turns it off. |
| `backendName` | (required) | Backend of the `assistant*` tables. |
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
| `ownerSecurityKeyType` | `assistantUserId` | Key type of the owner record locks. |
| `pricing` | Anthropic list prices, 2026-10 | USD per million tokens per model, used for cost and budgets. |
| `allowedWidgetNames` | none | Dashboard widgets `render_widget` may render. The kernel denies every widget not on this list. |
| `hiddenWidgetNames` | none | Widgets that are never rendered, even when allowed. |
| `customRedactionPatterns` | none | Extra regular expressions the kernel redacts from tool results. |
| `killSwitchPollMillis` | 2000 | How often a running turn re-checks the kill switch while it waits on the model. |

At runtime, an admin can override the kill switch, the main model and effort, and the budgets in the single **`assistantPolicy`** row.

A running turn notices the kill switch in two ways:
- It re-reads the switch before every model call and tool step.
- A watcher polls it every `killSwitchPollMillis` while the turn waits on the model.

When a turn sees the switch on, it cancels every turn running on that replica. If the policy row cannot be read, the switch counts as **on**: it fails closed.

## Permissions

| Permission | Grants |
|---|---|
| `assistant.hasAccess` | Use the panel. It grants **no data access**: the assistant reads only what the user can read anyway. |
| `assistant.admin` | The Assistant Admin app, its tables and the retention process. |

There is no assistant-specific data permission. Everything the kernel reads is checked against the user's normal table permissions and record locks. The QBit never uses a system session (an ArchUnit test enforces this).

When the QBit is enabled, it registers the supplemental instance meta-data `assistant` (`{version, basePath}`). The Next UI only probes `/config` when `/metaData` carries it.

### Record locks on the assistant's own tables

Conversation data belongs to its user. Every user-owned table has a record lock on `userId` with key type `assistantUserId`:
- The panel routes add the user's own id to their session, so users only ever see their own rows.
- Admins see everyone's rows in the Assistant Admin app through the key type's all-access key, `assistantUserIdAllAccess`. Grant that key to `assistant.admin` holders from your authentication customizer:

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
- The `assistant*` tables are never visible to the model.

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

All of these are standard, visible QQQ tables in the Assistant Admin app:

| Table | Holds |
|---|---|
| `assistantConversation` | Conversations. |
| `assistantMessage` | Messages (`partsJson`). |
| `assistantTurn` | Turn accounting. |
| `assistantTurnEvent` | Stream events, for replay. |
| `assistantToolCall` | The audit of every tool call. |
| `assistantFeedback` | Feedback on answers. |
| `assistantPolicy` | The kill switch and overrides. |
| `assistantUsageDaily` | Usage rollups: per user per day, plus an instance row whose `userId` is null. |

Field names are camelCase. Map them to your schema with a table customizer. `docs/schema-postgres.sql` has a reference DDL.

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

## License

Apache 2.0.
