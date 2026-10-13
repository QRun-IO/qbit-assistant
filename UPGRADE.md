# Upgrading qbit-assistant

## 0.1.0-RC.3 to 0.1.0-RC.4: provider settings, prices, per-call usage, app home

Three new tables. Create them before you deploy (see `docs/schema-postgres.sql`, which also seeds the Anthropic prices):
- `assistant_provider` (with the `api_key_status` column: it is filled on read and never written, but the backend selects it)
- `assistant_model_price`
- `assistant_usage`

Then, to keep answering exactly as before:
1. Register the provider type: `config.withProviderType(new AnthropicProviderType())`. Keep `withModelProvider(AnthropicModelProvider.fromEnvironment())` as the fallback.
2. Add one provider row (or seed it in your migration): type `anthropic`, model = your current main model, enabled, default, `apiKeyEnvName` = `ANTHROPIC_API_KEY` (or leave it empty for the default). No key value is ever written.
3. Run **Test connection** on it.

Until a row exists, the provider registered in code answers, as in RC.3. Other changes: the app label is **Assistant**; the nav no longer lists the retention process (it is a table action on conversations) or the audit tables (they are child lists). Grants on `assistant.admin` cover everything new.

## 0.1.0-RC.1 to 0.1.0-RC.3: snake_case names

The rename shipped in 0.1.0-RC.3. 0.1.0-RC.2 on Maven Central is an accidental republish of the RC.1 code (see CHANGELOG.md), so upgrading from RC.2 is the same as upgrading from RC.1.

RC.3 applies the QRun-IO naming rule (owner decision, 2026-10-12): *"table names should always be lowercase and use _, and qbits should always be prefixed with their name"*. The QQQ table names, the owner security key type and the possible-value source change. Permissions do not. **This is a metadata-only change**: no table, column or index in your database changes, and no Liquibase or DDL step is needed.

### What changed

| Kind | RC.1 (and RC.2) name | RC.3 name |
|---|---|---|
| Table | `assistantConversation` | `assistant_conversation` |
| Table | `assistantMessage` | `assistant_message` |
| Table | `assistantTurn` | `assistant_turn` |
| Table | `assistantTurnEvent` | `assistant_turn_event` |
| Table | `assistantToolCall` | `assistant_tool_call` |
| Table | `assistantFeedback` | `assistant_feedback` |
| Table | `assistantPolicy` | `assistant_policy` |
| Table | `assistantUsageDaily` | `assistant_usage_daily` |
| Possible-value source (conversations) | `assistantConversation` | `assistant_conversation` |
| Permissions | `assistant.hasAccess`, `assistant.admin` | unchanged (QQQ's standard `<base>.hasAccess` suffix; the `assistant` base already follows the rule) |
| Security key type | `assistantUserId` | `assistant_user_id` |
| All-access key | `assistantUserIdAllAccess` | `assistant_user_id_all_access` |

Unchanged: Java class names, field names (`userId`, `conversationId`, ...), the `assistantAdmin` app, the `assistantRetention` process, the `/qqq/v1/assistant` routes and the `assistant` supplemental instance meta-data.

The old names stay available as `@Deprecated` constants for one release, so migration code can refer to them: `<Entity>.LEGACY_TABLE_NAME`, `AssistantQBitConfig.LEGACY_DEFAULT_OWNER_KEY_TYPE` and `AssistantMetaDataBuilder.LEGACY_ALL_ACCESS_SUFFIX`. They will be removed in 0.2.0.

### Physical tables

`docs/schema-postgres.sql` already creates `assistant_*` tables. If your RDBMS backend mapped the old QQQ names to those tables (for example with `inferBackendName`), the new QQQ names now equal the physical names and nothing else is needed; the mapping becomes a no-op. If you created tables literally named `assistantConversation` and so on, either rename them:

```sql
ALTER TABLE "assistantConversation" RENAME TO assistant_conversation;
ALTER TABLE "assistantMessage"      RENAME TO assistant_message;
ALTER TABLE "assistantTurn"         RENAME TO assistant_turn;
ALTER TABLE "assistantTurnEvent"    RENAME TO assistant_turn_event;
ALTER TABLE "assistantToolCall"     RENAME TO assistant_tool_call;
ALTER TABLE "assistantFeedback"     RENAME TO assistant_feedback;
ALTER TABLE "assistantPolicy"       RENAME TO assistant_policy;
ALTER TABLE "assistantUsageDaily"   RENAME TO assistant_usage_daily;
```

or keep them and set `RDBMSTableBackendDetails.tableName` to the old name in your table customizer.

### Stored references to update

QQQ stores table names, permission names and key names outside this qbit. Update them in the same deployment. The examples are for Postgres with QQQ's default snake_case backend names; adjust the table and column names to your schema.

1. **Security keys.** Permission grants (`assistant.hasAccess`, `assistant.admin`) need no change. If your authentication customizer grants the all-access key itself rather than calling `AssistantSecurity.applySessionKeys`, grant `assistant_user_id_all_access`. If you set `ownerSecurityKeyType` explicitly, the all-access key is now `<keyType>_all_access`.

2. **Saved views and saved reports** on the assistant tables:

   ```sql
   UPDATE saved_view SET table_name = CASE table_name
      WHEN 'assistantConversation' THEN 'assistant_conversation'
      WHEN 'assistantMessage'      THEN 'assistant_message'
      WHEN 'assistantTurn'         THEN 'assistant_turn'
      WHEN 'assistantTurnEvent'    THEN 'assistant_turn_event'
      WHEN 'assistantToolCall'     THEN 'assistant_tool_call'
      WHEN 'assistantFeedback'     THEN 'assistant_feedback'
      WHEN 'assistantPolicy'       THEN 'assistant_policy'
      WHEN 'assistantUsageDaily'   THEN 'assistant_usage_daily'
      ELSE table_name END
    WHERE table_name IN ('assistantConversation', 'assistantMessage', 'assistantTurn', 'assistantTurnEvent',
                         'assistantToolCall', 'assistantFeedback', 'assistantPolicy', 'assistantUsageDaily');
   ```

   Run the same statement against `saved_report` (and `quick_saved_view` / `shared_saved_view` only if they store a table name in your schema).

3. **Audit table names.** QQQ audits reference tables through the `audit_table` lookup. Rename the rows so old and new audits stay under one table:

   ```sql
   UPDATE audit_table SET name = CASE name
      WHEN 'assistantConversation' THEN 'assistant_conversation'
      WHEN 'assistantMessage'      THEN 'assistant_message'
      WHEN 'assistantTurn'         THEN 'assistant_turn'
      WHEN 'assistantTurnEvent'    THEN 'assistant_turn_event'
      WHEN 'assistantToolCall'     THEN 'assistant_tool_call'
      WHEN 'assistantFeedback'     THEN 'assistant_feedback'
      WHEN 'assistantPolicy'       THEN 'assistant_policy'
      WHEN 'assistantUsageDaily'   THEN 'assistant_usage_daily'
      ELSE name END
    WHERE name IN ('assistantConversation', 'assistantMessage', 'assistantTurn', 'assistantTurnEvent',
                   'assistantToolCall', 'assistantFeedback', 'assistantPolicy', 'assistantUsageDaily');
   ```

   If a new-name row was already created (the app ran RC.3 before this update), repoint `audit.audit_table_id` from the old row to the new one first, then delete the old row.

4. **Anything else that stores a table name**: frontend links or bookmarks to `/app/.../assistantConversation`, Quick Search index rows (`quick_search_index.table_name`; reindex instead of editing), ESB topics or reports you configured by table name.

### Order

Deploy RC.3 and run the statements above in the same maintenance window. Until the update runs, saved views and reports on the assistant tables do not open, and admins who get the all-access key from their own customizer only see their own rows.

## 0.1.0-RC.6: tools through the UI API

Database (see `docs/schema-postgres.sql`):
- new tables `assistant_data_access`, `assistant_pending_action`, `assistant_profile`, `assistant_skill` and `assistant_profile_skill`;
- new columns `assistant_tool_call.ui_api_operation`, `http_status`, `audit_href`, `pending_action_id`;
- new columns `assistant_policy.writes_require_confirmation`, `allowed_tools`, `max_rows_per_call`, `redact_pii`.

Runtime: the tools call this server's own `/qqq/v1` on `127.0.0.1` and the port the first assistant request arrived on. If the server is not reachable that way (a different bind address, a TLS-only listener), set `withUiApiBaseUrl(...)`. Grant `assistant.admin` holders the new tables through the qbit's existing admin permission (no new permission names).

Behavior: nothing is writable until an admin adds a READ_WRITE data access row; before that the assistant reads exactly what it did before, now through the UI API.

## P-a: provider and model registry

Before starting the new version, database-backed hosts add the provider columns
and model sub-table below. Memory-backed hosts need no migration. Existing rows
derive kind and location from the adapter and keep their model behavior until
models are registered. Keys remain environment-variable references.

```sql
ALTER TABLE assistant_provider
   ADD COLUMN kind VARCHAR(30),
   ADD COLUMN tls_ca_bundle VARCHAR(100),
   ADD COLUMN location VARCHAR(10),
   ADD COLUMN hipaa_eligible BOOLEAN DEFAULT FALSE,
   ADD COLUMN region VARCHAR(50),
   ADD COLUMN api_version VARCHAR(30),
   ADD COLUMN connect_timeout_seconds INTEGER DEFAULT 10 CHECK (connect_timeout_seconds BETWEEN 1 AND 600),
   ADD COLUMN request_timeout_seconds INTEGER DEFAULT 120 CHECK (request_timeout_seconds BETWEEN 1 AND 600),
   ADD COLUMN last_discovery_at TIMESTAMP WITH TIME ZONE,
   ADD COLUMN last_discovery_status VARCHAR(16),
   ADD COLUMN last_discovery_error VARCHAR(500);
ALTER TABLE assistant_provider ALTER COLUMN model TYPE VARCHAR(200);
ALTER TABLE assistant_provider ALTER COLUMN last_test_model TYPE VARCHAR(200);
ALTER TABLE assistant_usage ALTER COLUMN model TYPE VARCHAR(200);
ALTER TABLE assistant_message ALTER COLUMN model TYPE VARCHAR(200);
ALTER TABLE assistant_turn ALTER COLUMN model TYPE VARCHAR(200);

CREATE TABLE assistant_model
(
   id SERIAL PRIMARY KEY,
   provider_id INTEGER NOT NULL REFERENCES assistant_provider(id),
   model_id VARCHAR(200) NOT NULL,
   display_name VARCHAR(200),
   enabled BOOLEAN,
   supports_tools BOOLEAN,
   supports_vision BOOLEAN,
   supports_streaming BOOLEAN,
   supports_reasoning_effort BOOLEAN,
   context_window_tokens INTEGER,
   max_output_tokens INTEGER,
   input_usd_per_m_tok NUMERIC(12, 4),
   output_usd_per_m_tok NUMERIC(12, 4),
   source VARCHAR(16),
   discovered_at TIMESTAMP WITH TIME ZONE,
   last_test_at TIMESTAMP WITH TIME ZONE,
   last_test_status VARCHAR(16),
   last_test_latency_ms INTEGER,
   last_test_error VARCHAR(500),
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE,
   UNIQUE (provider_id, model_id)
);
```

Register the optional `qbit-assistant-provider-openai` module and
`OpenAiCompatibleProviderType` to offer its kinds. Azure and generic endpoints
require an explicit `allowedBaseUrls` entry; local defaults may be keyless.
Discover models preserves existing rows. Cloud catalogs larger than 20 are added
disabled; enable the models to offer before leaving the provider's model empty.
HIPAA eligibility records an administrator's fact; P-a does not implement PHI routing.

Custom CA bundles are server configuration, never database content. Register them
with `withTrustedCaBundle(name, pem)` or `withTrustedCaBundleFile(name, path)` at
startup, then set the HTTPS provider row's `tlsCaBundle` to that name. Empty
references retain JVM default trust. The bundle adds trust without replacing JVM
roots; hostname verification remains enabled. If the P-a columns were already
applied, add only `tls_ca_bundle VARCHAR(100)` to `assistant_provider`.


## P-b: AWS Bedrock and Google Vertex

Before starting this version, database-backed hosts that already applied P-a add:

```sql
ALTER TABLE assistant_provider ADD COLUMN cloud_project VARCHAR(100);
```

Fresh installations use `docs/schema-postgres.sql`; memory-backed hosts need no
migration. Existing providers leave the new column null. Vertex requires the GCP
project id in `cloudProject`; Bedrock and Vertex both require `region` and reject
`apiKeyEnvName`. They default to CLOUD; `hipaaEligible` remains a manual admin fact.

Add the optional `qbit-assistant-provider-bedrock` and/or
`qbit-assistant-provider-vertex` module at the QBit version and register
`BedrockProviderType` / `VertexProviderType`. Credentials come from the server's
AWS default chain / Google Application Default Credentials. No credentials are
stored in provider rows. Hosts using AWS IRSA or web identity supply
`software.amazon.awssdk:sts` on their own runtime classpath (matching the
transitive AWS SDK version); this QBit does not add it. See README Providers for
configuration, endpoint overrides and static discovery limitations.
