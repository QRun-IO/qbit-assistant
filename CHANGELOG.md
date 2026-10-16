# Changelog

## Unreleased - Empty renders and stream context (UI API extension 1.4.0)

- Tool result renders always carry their lists: a `records` render for a query that matched nothing now has `"rows": []` and `"columns": []` (QQQ's JSON omitted empty lists, and Next's Assistant crashed the page reading `rows.length`). Parts and events stored before the fix are completed when read. Extension 1.4.0 documents the shape as `AssistantRecordsRenderV1`.
- Streamed text arrives on the provider's own (virtual) thread; the turn's progress updates now run in the turn's QContext, so job progress no longer fails with "Could not flush job progress" (NPE on the missing instance).

## Unreleased - UI API 1.6.0

- UI API extension assistant 1.3.0 (requires QQQ 4.1.0-RC.15, UI API 1.6.0): operationIds, standard error responses with BasicErrorResponseV1, extension tags and trace IDs. Preserve HTTP statuses and assistant-specific error codes.
- Retain AssistantErrorV1 as a deprecated component for compatibility. Remove dangling `null` schema references from the two bodyless 204 responses (delete conversation and feedback), with an empty-content workaround for core schema traversal. Success payloads are unchanged; the contract test retains the release baseline with explicit exceptions for the error schema migration and these documentation corrections.

## Unreleased - Central health

### Added

- Dynamic `assistant.provider.<id>` and `assistant.model.<id>` dependency checks in component `assistant`, category AI, reusing provider diagnostics and minimal connection calls with deadlines, cost-cap/kill-switch skips and TEST usage accounting.
- LOCAL breaker reuse of central provider outcomes younger than 60 seconds, with its existing lazy probe as fallback.
- Generic Assistant health status card linking to filtered System Health; registration, status, accounting, deadline and sentinel-secret tests.
- QQQ 4.1 compatibility for the header shim: setting extra headers avoids duplicates now that core forwards them too.

### Removed

- The home's independent answering/last-test health indicators and per-provider health/down-until display. Central health owns status and history. Manual Test connection actions, `lastTest*` fields and the circuit breaker remain.

## Unreleased - Jobs tray

- Track assistant answers in the core Jobs tray through `JobTracking`, with thinking, tool and writing progress and Stop support.
- Require QQQ 4.1.0-RC.8 and the host's durable jobs runtime and jobs tables; answers remain untracked when the runtime is absent.
- Default to `JobTrackingRunTracker`; opt out with `withRunTracker(AssistantRunTracker.NOOP)`.

## Unreleased - In-app help

- Ship the host-neutral Assistant help bundle through QQQ's native registry, replacing the temporary loader.
- Cover Assistant tables, fields, actions, widgets and selectors; retain live metadata references and add a bundle coverage gate.
- Document HOST bundle overrides and remove deployment-specific provider, classification, retention and backup guidance.

## Unreleased - Transcript redaction

- Tokenize classified PII/PHI in stored messages, tool inputs/results, replay events, pending actions and derived summaries using QQQ secured-data tokens. Preserve eligible model requests and live SSE.
- Stage content until turn completion, redact split deltas and classify criteria even when no records match; protect assistant logs with a scoped core redactor.
- Reject proposals whose parameters were redacted; prevent confirmation before turn completion.
- Read core table-level classification metadata and add secured-data canary tests for storage, audits, logs and in-memory model access.
- Preserve durable-turn watchdog, supersede, shutdown and restart recovery with turn-scoped transcript redaction; widen token-bearing columns and entity limits, with H2-safe upgrade SQL.
- Require QQQ 4.1.0-RC.7 (qqq#1182); retain the default audit-history opt-in and avoid duplicated routing headers against current core. RC.7 is released.
## Unreleased - Foreign keys

- Reference DDL: add the missing foreign keys (conversation tree, usage, data audit,
  budget accounting, profile skills) with orphan quarantine and their indexes; make
  `assistant_model.provider_id` `ON DELETE CASCADE`. A Postgres guardrail test fails
  any declared relation without a foreign key.

## Unreleased - Durable turns

- Renew user-owned run leases with heartbeats, poll cross-replica Stop, enforce
  hard deadlines, and finalize active runs during graceful shutdown. Recover
  abandoned runs on replay, active discovery, or the next question.
- Default to superseding a prior answer in the same conversation. Accept explicit
  concurrent questions; the per-user cap supersedes the oldest run instead of
  refusing another question. Per-minute limits and budgets still apply.
- Add `GET /qqq/v1/assistant/turns/active`, `turn.phase` progress and `startedAt`
  on `message.start`. Cancel is idempotent and returns HTTP 200.
- Add the optional `AssistantRunTracker` Jobs tray SPI (no-op by default),
  pending QQQ core job tracking (#1210).
- Add the durable-turn schema upgrade; apply it before deploying this runtime.
  A partial unique index fences duplicate terminal events from concurrent reapers.
- UI API extension `assistant` 1.2.0 (additive): `turns/active`, the optional
  `concurrency` on a new turn, 503 `SHUTTING_DOWN`, `turn.phase`,
  `message.start.startedAt`, cancelReason `SUPERSEDED`. The 429 response stays
  documented for budgets and per-minute limits; the "answer already in progress"
  refusal is no longer returned. Cancel keeps 202 documented for older replicas.

## Unreleased - Starter defaults

- Every seed changeset's rollback is empty: a seeded row cannot be told apart from a host's own row with the same name, and Liquibase also rolls back changesets it marked ran, so a name-based undo would delete host data.

- Fix the defaults changelog: every precondition is a portable `sql-check` (the formatted-SQL `table-exists` form failed to parse); the policy, budget and prices are seeded only into empty tables and the profile only when no default exists, so an existing host never changes on deploy. The test now runs the changelog through Liquibase 4.33, including an already configured host and a missing table.
- The Setup card prices the default model the way calls are costed (registry, price rows, configured prices); "Add rows for every table" reads fresh data-access rows.

- Ship `db/liquibase/qbit-assistant/assistant-defaults.changelog.sql`: insert-only starter rows for the policy, a default profile, four starter skills offered through it, a monthly instance budget (warn, downgrade, stop), current Anthropic prices and a disabled example scheduled task.
- Add the Setup card to the Assistant home: every configuration table with its state (set up, needs setup, using defaults, optional) and the next step.
- Add the `assistant_data_access` table action "Add rows for every table", which writes the default access out as editable rows.
- Add `help/qbit-assistant.yaml` (QQQ help-bundle format) for the app and every assistant table, saying what each table is for and how its rows get created; applied by `AssistantHelp` until core help bundles ship in a release.
- Guardrail tests: every table is used at runtime, seeded or explained, and has empty-state help; the changelog seeds a fresh schema and the seeded rows change the assistant's behavior.

## Unreleased - X1/Y1

- Add default-off `withRecordHistory(true)` host opt-in: remove only `audit*` on RC.6, or invoke core #1193 audit-read/write-deny support when available. Preserve user permissions and all other channel denials; kof22 does not opt in.

- Register explicit assistant-channel denials on every assistant table, process,
  report and widget, preserving core security patterns; fail runtime startup
  when an assistant object is exposed.
- Bump the assistant UI API extension to 1.1.0 for optional owned-turn attribution
  on explain-access, and regenerate its OpenAPI contract.
- Preserve turn attribution in knowledge discovery, RAG reads and explain-access
  calls; confirm actions retain the original turn and scheduled runs retain the task.
- Build audit links with the presentation filter-link builder, filtering by
  record, table and channel, including deletes; widen audit link storage to TEXT.
  Document the host audit migration and the QBit column widening.
- Verify the admin denial matrix through the real invoker and Next UI, startup
  failures, and confirmed-write human/channel attribution with decoded audit links.
## Unreleased - Email

### Changed

- Switch scheduled-result email delivery to qbit-email 0.1.0-RC.2's published `com.kingsrook.qbits.email.api` package, preserving templates, categories, idempotency keys, linked records and status mapping.
- Add optional qbit-email 0.1.0-RC.1 delivery of a scheduled task's result (`AssistantEmail.sendScheduledResult`): template `assistant_scheduled_result`, link-only secure message for results above PUBLIC using core's `DataClassification`, owner-recipient hook, opt-in and `emailExternalApproved` gates. Scheduled-task wiring remains a separate integration step.

## Unreleased - Provider API extraction (#30)

- **BREAKING:** move all 23 provider API types, including `ModelPricing`, to `com.kingsrook.qbits.assistant.spi`. Recompile consumers with the new imports; see UPGRADE.md. Enforce exclusive package ownership and API dependency boundaries with ArchUnit.

- Extract provider SPI/value types and `ModelPricing` into `qbit-assistant-provider-api`, now exclusively in `com.kingsrook.qbits.assistant.spi`. Its only production dependency is Jackson annotations.
- Move Anthropic, OpenAI, Bedrock and Vertex main dependencies to the API; retain runtime fixtures in test scope. Pgvector still needs runtime RAG interfaces.
- Provider-specific validation now takes unresolved `ProviderSettings` instead of `AssistantProvider`; add an ArchUnit boundary and standalone coverage gates.
## Unreleased - P-g

- Add admin Scheduled tasks, owner-locked run history, Quartz cron/timezones,
  and a 60-second QQQ scheduled process using the host's scheduler context.
- Log in through the loopback UI API with a separately allow-listed credential
  environment reference; verify the designated user before starting a normal turn.
- Add Review/Result Approve write mode, Revoke write mode and Run now actions.
  Approval binds the task definition; scheduled writes always await confirmation.
- Attribute scheduled UI API requests and tool logs, apply per-run STOP caps,
  and send optional Alerts notifications to the designated user. HIPAA summaries
  contain only `see conversation`.
- Add unique schedule-slot claims, fixed-clock fixture integration tests and
  additive Postgres DDL. No API routes or provider modules change.

## Unreleased - P-f

- Add OpenAI-compatible embeddings (including Azure), explicit CHAT/EMBEDDING
  model kinds, editable discovery hints, and budgeted EMBED usage.
- Add admin Knowledge sources with streamed Review/Result Index now and Clear
  index actions. Indexing uses the clicking admin's UI API session, chunks text,
  skips unchanged hashes and reconciles disappeared records.
- Add content-free in-memory and optional JDBC pgvector stores, per-instance
  schemas, HNSW cosine search and transactional record replacement.
- Add permission-filtered `search_knowledge`, fresh UI API reads, existing
  masking/redaction, PHI embedding guards, classification propagation and citations.
- Add embedding HTTP contracts, real UI API RAG security/golden tests and
  always-on JDBC contracts; the pgvector container test skips without Docker.
## Unreleased - Answer presentation

- Add `present_results`: server-formatted metrics, tables, computed percentages,
  charts and validated in-app filter/record/action links.
- Persist and stream presentation parts with a multi-line GFM fallback and
  record-data retention; add a static default presentation skill.
- Add the owner's 81 JPEG / 20 PNG golden example, shared live eval coverage,
  permission/formatting/input tests and strict-schema budget coverage.

## Unreleased - P-e

- Add per-profile heuristic/classifier model routing, with budgeted ROUTE usage.
- Add bounded cross-provider fallback chains, PHI-safe selection, neutral history
  rebuilding, and a 60-second connection circuit breaker with cached local probes.
- Show provider health, fallback totals and turn routing; include additive schema
  migration and admin record-screen controls.


## Unreleased - P-d

- Require QQQ RC.5 and send budget alerts directly through core QAlerts, with admin-visible alert type metadata and core sink tests.

- Admin budgets, expiring overrides, deduplicated warning/downgrade/stop events.
- Per-dispatch limits preserving PHI routing and existing caps.
- Home budget cards, 30-day cost/token bars and provider breakdowns.


## Unreleased — P-c

- Add configurable table/field classification, persistent turn/conversation maxima,
  and PHI routing checks before every model call, with a safe blocked-turn reason.
- Add HIPAA mode, eligible-provider enforcement, AES-256-GCM conversation storage,
  provider diagnostic redaction, and independent PHI conversation retention.
- Add owner-locked, admin read-only data routing audits and HIPAA status on the home.
- Classification levels are core's `DataClassification` (the qbit stand-in is removed); the default source reads core AI metadata merged with configuration.

All notable changes to this project are documented here. The format follows Keep a Changelog, and versions follow semantic versioning.

## [0.1.0] - Unreleased

- Add optional AWS Bedrock (Claude) and Google Vertex (Claude/Gemini) adapters using server credentials, static model discovery and allow-listed endpoint overrides. AWS IRSA/web-identity hosts supply STS themselves; no additional dependency is bundled.
- Add the provider `cloudProject` column, cloud settings validation, injected Anthropic clients and per-request OpenAI bearer-token suppliers. Vertex Gemini prefixes model ids and omits strict tools.
- Add localhost cloud contract tests and golden tool turns, including AWS binary event-stream framing, SigV4, token refresh and cloud HTTP error mapping.

- Add named, server-only CA bundles for HTTPS providers, additive JVM trust and sanitized TLS diagnostics; tolerate repeated/missing streamed tool identities and invalid arguments, with a golden tool round-trip test.
- Add the provider/model registry, per-model capabilities and prices, keyless local providers, discovery and health, and provider/model connection tests.
- Add the optional JDK HTTP OpenAI-compatible adapter for OpenAI, Azure, OpenRouter, Ollama, LM Studio, vLLM and llama.cpp, with strict-schema safeguards and localhost contract tests.

Phase 0/1 of the QQQ Assistant: read-only Q&A and navigation. Requires QQQ 4.1 with:
- the AI tool kernel (`qqq-backend-core` `actions.ai`, QRun-IO/qqq#1078)
- the developer-panel contract (QRun-IO/qqq#1076).

### Changed (0.1.0-RC.7, needs QQQ with QRun-IO/qqq#1128)
- The assistant's endpoints are spec'd `/qqq/v1` operations (UI API extension `assistant` 1.0.0, prefix `/assistant`): same URLs, bodies and statuses, now in the served OpenAPI; turns and events are `text/event-stream`. The raw routes are gone.
- Tools call core's `UiApiInvoker` with the caller's own request credentials (`UiApiCredentials`); the loopback stand-in is removed. The assistant's own operations are never invocable by its tools.
- The OpenAPI fragment is committed (`openapi/qqq-v1/assistant.yaml`) and contract-tested.

### Changed (0.1.0-RC.6): tools go through the UI API (owner decision 2026-10-13)
- **Every data tool runs a spec'd `/qqq/v1` operation with the user's own session** (`UiApiGateway`, loopback HTTP with the `sessionUUID` cookie): the same authentication, permissions, record locks, validation, audit and errors as the UI. A user without a permission gets the identical denial through the assistant. `navigate` and `search_help` (no records) stay on the core kernel. `LoopbackUiApiGateway` is a stand-in for core's `UiApiInvoker` (QRun-IO/qqq#1128).
- Tools: `list_tables`, `describe_table`, `query_records`, `count_records`, `get_record`, `search_records`, `render_widget`, `get_record_history`, and new **`create_record`, `update_record`, `delete_record`**.

### Added (0.1.0-RC.6)
- **Profiles** (`assistant_profile`, nav "Profiles"): display name, icon, personality (â‰¤ 2,000 chars, appended to the per-user system block), greeting, starter prompts; one default, optional app / permission scope. `/assistant/config` returns `profile`.
- **Skills** (`assistant_skill`, nav "Skills", linked through `assistant_profile_skill`, a child list on profiles): admin-written Markdown instructions read only when relevant (keywords / apps), â‰¤ 6,000 chars each and 12,000 per question. Instructions only: they never grant tools, endpoints or access (ArchUnit-enforced).
- **Explain access**: `GET /qqq/v1/assistant/access?table=&prompt=` and the `assistantAccess` home widget: per table and tool, allowed or the reason.
- **Recent denials** card on the Assistant home.
- `AssistantStore` refuses any table that is not `assistant_*`; ArchUnit forbids the tool / UI API / access packages from using QQQ actions, the AI kernel or the profile and skill classes.
- **Data access** (`assistant_data_access`, nav table "Data access"): NONE / READ / READ_WRITE per table or process. Default: NONE for sensitive tables (AI exposure NONE; cannot be raised), READ for other tables, NONE for processes. It only narrows the UI API.
- **Pending actions** (`assistant_pending_action`): with confirmation on (the default), a write only proposes; `POST /qqq/v1/assistant/actions/{id}/confirm` runs it through the UI API with the confirming user's session, once; `.../reject`; `GET .../actions/{id}`. Child list on conversations.
- **Policy columns** on `assistant_policy`: `writesRequireConfirmation`, `allowedTools`, `maxRowsPerCall`, `redactPii`.
- **Tool-call log columns** on `assistant_tool_call`: `uiApiOperation`, `httpStatus`, `auditHref`, `pendingActionId`; tool inputs are stored with secrets redacted.
- Config: `uiApiBaseUrl`, `withUiApiGateway`, `pendingActionTtlSeconds`. `/assistant/config` reports `features.writes` (some table is READ_WRITE) and `features.pendingActions`.

### Fixed (0.1.0-RC.5)
- **Every turn failed with "The AI service could not answer this request."** Anthropic refused the request with 400 `invalid_request_error` "The compiled grammar is too large ... reduce the number of strict tools": all ten read tools were sent with `strict: true`, and the API compiles every strict schema of a request into one grammar. Tools are now **not strict by default**. The kernel already validates every tool input and answers `INVALID_INPUT`. A host can opt individual tools in with `withStrictToolNames(...)`.
- If a provider still refuses the strict schemas, the turn **retries once without strict tools** and logs a warning (`ModelProviderException.isStrictSchemaRejected`).
- Provider errors now carry the **API's own error type, a sanitized message** (one line, capped, key-like tokens redacted) and the **request id**:
  - they are logged at WARN for 4xx;
  - they are recorded in the `assistant_usage` row's request id;
  - they are shown to `assistant.admin` holders in the panel and in Test connection.
  Before this, a 400 was logged as "INVALID_REQUEST httpStatus=400" with no reason.
- A turn that hits a JVM `Error` (for example a linkage problem) now ends as `ERROR` with a log line, instead of being swallowed by the executor and ending as an empty `COMPLETE` answer.
- `AnthropicRequestContractTest` checks the exact request against the Messages API constraints and a conservative strict-tool budget.

### Added (0.1.0-RC.4)
- **Provider settings** (`assistant_provider`): type (registered `AssistantProviderType`s; `AnthropicProviderType` in the Anthropic module), model, enabled, default, base URL override, monthly cost cap, max output tokens per request, max concurrent requests, and the last connection test. The default enabled row is the active provider; with no rows the provider registered in code is used, so existing hosts keep working.
- **Key handling:** the key is never stored. A row names the environment variable that holds it (`apiKeyEnvName`, allow-listed); `apiKeyStatus` is computed on read (`Set â€¢ â€¦abcd`) and dropped on save. A pasted key, an unrelated variable or an unlisted base URL is refused. New config: `withProviderType`, `allowedApiKeyEnvNames`, `allowedBaseUrls`, `withSecretResolver`, `providerTestTimeoutSeconds`.
- **Test connection**, a record action on providers (standard streamed ETL Review and Result lines): one short live call, result saved on the row and recorded as a `TEST` usage row.
- **Per-call usage** (`assistant_usage`): one row per model call (turn steps and tests) with provider, model, status, error kind, HTTP status, latency, tokens and cost. Child list on conversations and providers.
- **Model prices** (`assistant_model_price`), editable, used for every call's cost; the reference DDL seeds the Anthropic list prices of 2026-10. The built-in `pricing` defaults now cover every current Claude model.
- **The app home** (`assistantHome` widget): health, providers, usage by day, costs against caps, by user, top tools and recent failures, from real rows with empty states.
- `ModelResponse.model`: the model that answered (set by the Anthropic adapter).

### Changed (0.1.0-RC.4)
- The app is labelled **Assistant** (name `assistantAdmin` unchanged). Its nav lists the home and the main tables only (Providers, Conversations, Model prices, Policy); other tables are child lists. `assistantRetention` is a table action on conversations instead of a nav entry.
- Every `assistant_*` table has AI exposure `NONE`, so the assistant never reads its own settings or audit tables (before, only the `assistant.admin` permission kept them out).
- `AssistantMetaDataBuilder.buildAdminApp(tables)` no longer takes the retention process.

### Changed (0.1.0-RC.3)
- **Snake_case names** (QRun-IO naming rule, 2026-10-12). Metadata only; the physical tables were already `assistant_*`:
  - tables `assistantConversation` ... `assistantUsageDaily` are now `assistant_conversation`, `assistant_message`, `assistant_turn`, `assistant_turn_event`, `assistant_tool_call`, `assistant_feedback`, `assistant_policy` and `assistant_usage_daily`; the conversation possible-value source follows the table name
  - permissions are unchanged: `assistant.hasAccess` keeps QQQ's standard `hasAccess` suffix, and `assistant.admin` stays as is
  - security key type `assistantUserId` is now `assistant_user_id`, with all-access key `assistant_user_id_all_access`
  - the old names remain as `@Deprecated` `LEGACY_*` constants until 0.2.0. See [UPGRADE.md](UPGRADE.md) for the stored references to update (permissions, saved views and reports, `audit_table` names).

### Added
- **`qbit-assistant`**
  - `AssistantQBitProducer` and `AssistantQBitConfig`:
    - enable flag, plus the `QQQ_ASSISTANT_ENABLED` environment variable
    - provider name, model routing (Sonnet 5.5 at effort medium)
    - limits: 25 tool calls, 4 parallel, 50 rows, 500 characters per cell, 300 s
    - budgets, rate limits
    - retention of 90 / 30 / 365 days
    - owner key type and pricing.
  - Standard, visible tables: `assistant_conversation`, `assistant_message`, `assistant_turn`, `assistant_turn_event`, `assistant_tool_call`, `assistant_feedback`, `assistant_policy` and `assistant_usage_daily`. User-owned tables are locked to their owner (`assistant_user_id`), and admins read everything through `assistant_user_id_all_access`.
  - The Assistant Admin app, gated by `assistant.admin`.
  - The `assistantRetention` process.
  - `/qqq/v1/assistant` routes: config probe, conversations CRUD, turns as Server-Sent Events, replay through `/events?after=`, cancel, and feedback. The panel requires `assistant.hasAccess`. The kill switch and budgets are checked when a turn starts, and the kill switch again before every step.
  - `TurnEngine`:
    - streams the model's answer
    - runs read and navigation tools through `AiToolKernel`, with parallel calls bounded
    - sends results back in one message, with errors marked `is_error`
    - enforces the limits on tool calls, time, budget, cancel and kill switch
    - persists messages, turns, tool calls, events and usage.
  - `ContextBuilder` in cache-prefix order: tools, then the static system block (1 h), then the user digest (5 m), then history, then the new turn.
  - The provider SPI (`AssistantModelProvider`, `ModelRequest`, `StreamHandle`, `ModelCapabilities`) and `ScriptedModelProvider`.
  - The record Developer-screen "Assistant" panel, built on the core developer-panel contract, with the "Ask the assistant about this record" deep link.
- **`qbit-assistant-provider-anthropic`**: `AnthropicModelProvider` on anthropic-java 2.71.0. It provides:
  - strict tools and `tool_choice: auto`
  - adaptive thinking with display omitted, and an explicit effort
  - cache breakpoints (1 h / 5 m)
  - refusal and max_tokens handling
  - cancel
  - an API key taken only from the environment or a host secret.
- **Tests**:
  - ArchUnit rules
  - golden evals v1: examples D and the read half of A, plus count, navigate and search
  - the permission red team (a build gate)
  - Anthropic tests against recorded SSE streams
  - live evals against Claude (opt-in).

### Security review follow-ups
- Kernel settings come from config: `allowedWidgetNames` (the kernel denies any widget not listed), `hiddenWidgetNames` and `customRedactionPatterns`.
- **Orphaned turns.** A turn left RUNNING by a dead replica is finished as ERROR the first time it is seen, by replay, cancel, delete or a new turn. Its message gets status ERROR, and synthetic `error` (INTERNAL) and `message.end` events are persisted. Replay loops are capped at `maxTurnSeconds` + 60 s.
- **Retention.** The tool-result cutoff now also deletes stream events, and removes render payloads and record references from stored messages.
- **Usage.** `addUsage` uses per-key locks, a unique key on (scope, userId, usageDate) and an insert-race retry. Usage of cancelled, timed-out and failed model calls is counted when the provider reports it.
- **Kill switch.** It fails closed when the policy can't be read. A watcher cancels running turns promptly when it flips.
- **Limits.** Page-context scalars are capped (strings up to 512 characters, at most 100 selected ids). Conversation lists are limited to 200, running turns to 50, and replay is paged at 500 events.
- **Ordering.** Terminal events (`usage`, `error`, `message.end`) are persisted before the turn's status becomes final.
- **Supplemental meta-data.** The `assistant` supplemental instance meta-data is registered when the QBit is enabled.

## [0.1.0-RC.2] - 2026-10-12

**Do not use.** 0.1.0-RC.2 on Maven Central is an accidental republish of the RC.1 code by a tag build; do not use it. The next real candidate is 0.1.0-RC.3. (Cause: QRun-IO/qqq-orb#20; the `publish_release` workflow now runs on GA tags only.)
