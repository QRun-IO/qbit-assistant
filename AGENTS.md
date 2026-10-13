# AGENTS.md

This file provides guidance to Codex when working with code in this repository.

## Project Overview

**QBit Assistant** adds an in-app AI assistant to any QQQ application. It owns the chat backend:
- conversations and audit tables
- the turn engine (the agent loop)
- the `/qqq/v1/assistant` routes, with SSE
- budgets, rate limits and the kill switch
- the model-provider SPI.

The panel UI lives in `qqq-frontend-next`. Data access goes only through the **AI tool kernel in QQQ core** (`com.kingsrook.qqq.backend.core.actions.ai.AiToolKernel`).

- Design spec: `QRun-IO/qqq` `docs/specs/2026-10-11-qqq-assistant-qbit-design.md`. The owner decisions D1 to D6 at its top are binding.
- Phase 1 is read and navigate only: no write tools and no approval cards.

## Build Commands

```bash
./mvnw verify                                                   # all modules, tests, JaCoCo gate (70% instructions / 90% classes)
./mvnw verify -Dqqq.snapshot.version=4.1.0-ASSISTDEV-SNAPSHOT   # against a locally installed QQQ build with the kernel (+ developer panels)
./mvnw test -pl qbit-assistant -Dtest=RedTeamTest
ANTHROPIC_API_KEY=... ./mvnw verify -Dassistant.live=true -Dtest.excludedGroups= -Dgroups=live -Djacoco.skip=true -pl qbit-assistant-provider-anthropic -am
```

Always use the wrapper: Maven 3.10 cannot read the parent POM. The committed default QQQ version (`qqq.snapshot.version` in the root POM) must stay resolvable from Maven Central. Use a local kernel build only through `-D`.

## Modules

- `qbit-assistant` (`com.kingsrook.qbits.assistant`):
  - `AssistantQBitProducer`, `AssistantQBitConfig` (with `AssistantModelRouting` and `AssistantBudgets`), `AssistantRuntime`, `AssistantSecurity`.
  - `metadata/`: tables, owner key type, the Assistant app (home + main tables in the nav), the retention process, the settings customizer (key-reference checks, masked key status), and the `assistant.admin` custom permission checker.
  - `home/`: the app home widget (`AssistantHomeData` loads, `AssistantHomeView` renders flow-layout HTML).
  - `model/`: entities for the `assistant*` tables.
  - `persistence/AssistantStore`: the **only** code that calls QQQ data actions, and only on the QBit's own tables, under the user's session.
  - `engine/`: `TurnEngine` (the loop), `ContextBuilder` (cache-prefix order), `SystemPrompt`, `PolicyService` (policy row, budgets), `RateLimiter`, the `TurnEvent` / `TurnEventLog` stream, and `MessageParts`.
  - `api/`: `AssistantApi` (transport-free) and `AssistantRouteProvider` (Javalin, SSE).
  - `provider/`: the provider-neutral SPI, plus `fake/ScriptedModelProvider` for tests, evals and demos.
  - `developer/`: the record Developer-screen panel, on the core developer-panel contract.
  - `processes/`: `AssistantRetentionStep` and `TestConnectionProcess` (record action on `assistant_provider`).
  - `engine/ProviderService` (active provider from `assistant_provider` rows, key references, prices), `UsageRecorder` (one `assistant_usage` row per model call), `ConnectionTest`.
- `qbit-assistant-provider-anthropic`: `AnthropicModelProvider` and `AnthropicMapping` (anthropic-java streaming).

## Invariants (do not break)

- **Sessions:**
  - Never use `QSystemUserSession` or construct a `QSession` in main code (ArchUnit).
  - Every route uses `ExecutorSessionUtils.setupSession` (the user's own session).
  - `AssistantSecurity` only ever adds the user's OWN id key, or the all-access key for `assistant.admin` holders.
- **Data access:**
  - Only `persistence/` calls QueryAction, InsertAction and the other data actions (ArchUnit).
  - Tools run only through `AiToolKernel.execute`.
  - Panel API queries also filter `userId = me`, so an admin's all-access key never mixes other users' conversations into the panel.
- **Caching:** the request prefix stays byte-stable. The order is tools (kernel-sorted), then system block 1 (rules + primer + app digest, LONG/1h), then system block 2 (user digest + date, SHORT/5m), then messages. Exactly one SHORT hint goes on the newest message. Don't put timestamps or ids in the system prompt.
- **Tool results:**
  - Parallel tool results go back in ONE user message, in call order.
  - Errors (DENIED, NOT_FOUND, INVALID_INPUT, ERROR) are `is_error` results.
  - Unknown tools and over-limit calls also get error results.
- **Keys:** never stored in a table. `assistant_provider.apiKeyEnvName` holds only an allowed environment variable NAME; `apiKeyStatus` is computed on read (last 4 at most) and stripped on save.
- **Logging:** never log message content, prompts, tool results or keys. Structured end-of-turn log only.
- **Thinking:** thinking is never streamed or stored. The Anthropic adapter keeps the raw assistant message (with signatures) only in memory, as `providerState`, for the tool loop.
- **Links:** only `/app` or `/app/...` hrefs are emitted (`TurnEngine.isAppHref`).
- **Kill switch:** read fresh before every model call and every tool step.

## Tests

- `testing/` holds the fixture app on the memory backend:
  - blog, page, media and analytics tables, plus audits
  - sentinel values in hidden and password fields
  - a prompt-injection record
  - MOCK auth where the user-id prefix picks the permissions.
- `testing/` also holds `TurnRunner`, `GoldenTasks` and `EvalSupport`. They are published as a test-jar for the provider's live evals.
- Tags:
  - `golden` (`GoldenEvalTest`) and `redteam` (`RedTeamTest`) run in every build. Any failure fails the build.
  - `live` is excluded unless you pass `-Dtest.excludedGroups= -Dgroups=live` and set the key.

## Code Style

QQQ style:
- 3-space indent, braces on the next line, `return (x);`
- flower-box javadoc on every class and method
- fluent `withX` setters
- wrapper types.

## Release

- CircleCI qqq-orb 0.6.8.
- `develop` publishes SNAPSHOTs.
- `release/*` publishes RCs; the orb bumps `<revision>` in the root POM.
- `v*` tags publish releases.
- Both modules and the parent POM publish. The flatten plugin resolves `${revision}`.
