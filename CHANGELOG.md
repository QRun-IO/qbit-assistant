# Changelog

All notable changes to this project are documented here. The format follows Keep a Changelog, and versions follow semantic versioning.

## [0.1.0] - Unreleased

Phase 0/1 of the QQQ Assistant: read-only Q&A and navigation. Requires QQQ 4.1 with:
- the AI tool kernel (`qqq-backend-core` `actions.ai`, QRun-IO/qqq#1077)
- the developer-panel contract (QRun-IO/qqq#1076).

### Added
- **`qbit-assistant`**
  - `AssistantQBitProducer` and `AssistantQBitConfig`:
    - enable flag, plus the `QQQ_ASSISTANT_ENABLED` environment variable
    - provider name, model routing (Sonnet 5.5 at effort medium)
    - limits: 25 tool calls, 4 parallel, 50 rows, 500 characters per cell, 300 s
    - budgets, rate limits
    - retention of 90 / 30 / 365 days
    - owner key type and pricing.
  - Standard, visible tables: `assistantConversation`, `assistantMessage`, `assistantTurn`, `assistantTurnEvent`, `assistantToolCall`, `assistantFeedback`, `assistantPolicy` and `assistantUsageDaily`. User-owned tables are locked to their owner (`assistantUserId`), and admins read everything through `assistantUserIdAllAccess`.
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
