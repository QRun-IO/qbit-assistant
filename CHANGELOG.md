# Changelog

All notable changes to this project are documented here. The format follows Keep a Changelog, and versions follow semantic versioning.

## [0.1.0] - Unreleased

Phase 0/1 of the QQQ Assistant: read-only Q&A and navigation. Requires QQQ 4.1 with:
- the AI tool kernel (`qqq-backend-core` `actions.ai`, QRun-IO/qqq#1078)
- the developer-panel contract (QRun-IO/qqq#1076).

### Added (0.1.0-RC.4)
- **Provider settings** (`assistant_provider`): type (registered `AssistantProviderType`s; `AnthropicProviderType` in the Anthropic module), model, enabled, default, base URL override, monthly cost cap, max output tokens per request, max concurrent requests, and the last connection test. The default enabled row is the active provider; with no rows the provider registered in code is used, so existing hosts keep working.
- **Key handling:** the key is never stored. A row names the environment variable that holds it (`apiKeyEnvName`, allow-listed); `apiKeyStatus` is computed on read (`Set • …abcd`) and dropped on save. A pasted key, an unrelated variable or an unlisted base URL is refused. New config: `withProviderType`, `allowedApiKeyEnvNames`, `allowedBaseUrls`, `withSecretResolver`, `providerTestTimeoutSeconds`.
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
