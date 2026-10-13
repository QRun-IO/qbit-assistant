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
