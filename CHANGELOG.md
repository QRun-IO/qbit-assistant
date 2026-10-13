# Changelog

## Unreleased - P-d

- Admin budgets, expiring overrides, deduplicated warning/downgrade/stop events.
- Per-dispatch limits preserving PHI routing and existing caps.
- Home budget cards, 30-day cost/token bars and provider breakdowns.


## Unreleased — P-c

- Add configurable table/field classification, persistent turn/conversation maxima,
  and PHI routing checks before every model call, with a safe blocked-turn reason.
- Add HIPAA mode, eligible-provider enforcement, AES-256-GCM conversation storage,
  provider diagnostic redaction, and independent PHI conversation retention.
- Add owner-locked, admin read-only data routing audits and HIPAA status on the home.
- Keep QQQ RC.4 compatibility with a classification stand-in and core #1142 adapter seam.

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
