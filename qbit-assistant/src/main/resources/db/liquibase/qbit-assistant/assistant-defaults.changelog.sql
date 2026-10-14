-- liquibase formatted sql

-- qbit-assistant starter defaults: a working assistant out of the box.
-- Include it from the host's master changelog AFTER the changesets that create
-- the assistant_* tables (docs/schema-postgres.sql):
--
--   - include: { file: db/liquibase/qbit-assistant/assistant-defaults.changelog.sql }
--
-- Rules for this file:
--   * insert-only: every row is inserted only when its natural key is absent,
--     so an administrator's edits always survive and nothing is ever updated;
--   * no secrets: environment variable NAMES only, never values;
--   * portable SQL (PostgreSQL and H2): INSERT ... SELECT ... WHERE NOT EXISTS;
--   * every changeset has a precondition on its table, so a host that has not
--     created a table yet simply skips it (MARK_RAN).
-- AssistantDefaultsChangelogTest runs this file twice against a fresh schema.

-- changeset qbit-assistant:defaults-1
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM assistant_policy
-- comment: The single policy row, only when the host has none. Nulls inherit the code configuration (models, config budgets). Writes wait for the user's confirmation, PII is redacted, 50 rows per call.
INSERT INTO assistant_policy (name, kill_switch, writes_require_confirmation, redact_pii, max_rows_per_call, create_date, modify_date)
SELECT 'default', FALSE, TRUE, TRUE, 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM assistant_policy);
-- rollback DELETE FROM assistant_policy WHERE name = 'default';

-- changeset qbit-assistant:defaults-2
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM assistant_profile WHERE name = 'default'
-- comment: The default profile (persona): used for every app and user that no more specific profile matches.
INSERT INTO assistant_profile (name, display_name, icon, personality, greeting, starter_prompts, is_default, enabled, create_date, modify_date)
SELECT 'default', 'Assistant', 'auto_awesome',
   'Be concise, friendly and plain-spoken. Lead with the answer, then the detail that supports it. When something is ambiguous, ask one short question instead of guessing. Offer a link to the list or record you used so the user can check it.',
   'Hi! I can find and count records, explain a record and its history, and open the right screen for you. What would you like to know?',
   'What can you help me with?
Which tables can I look at?
What changed in the last 7 days?',
   TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM assistant_profile WHERE name = 'default');
-- rollback DELETE FROM assistant_profile WHERE name = 'default';

-- changeset qbit-assistant:defaults-3
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM assistant_skill WHERE name = 'find-and-count'
-- comment: Starter skill. Skills are know-how text only: they never grant tools, data or permissions.
INSERT INTO assistant_skill (name, description, instructions, enabled, trigger_keywords, create_date, modify_date)
SELECT 'find-and-count', 'Finding, filtering and counting records.',
   'When the user asks to find, list or count records:
1. If you do not already know the table and field names, call list_tables and describe_table first. Never guess names.
2. For "how many" questions use count_records; for lists use query_records with a filter, not search_records.
3. Say in one short phrase which filter you used (for example "open orders created this month").
4. Always include a filterLinks item for the list you counted, so the user can open exactly those records.
5. If the result was truncated, say so and offer the link instead of listing more.',
   TRUE, 'find,show,list,which,how many,count,filter,search,look up', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM assistant_skill WHERE name = 'find-and-count');
-- rollback DELETE FROM assistant_skill WHERE name = 'find-and-count';

-- changeset qbit-assistant:defaults-4
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM assistant_skill WHERE name = 'explain-a-record'
-- comment: Starter skill: summarizing one record and its change history.
INSERT INTO assistant_skill (name, description, instructions, enabled, trigger_keywords, create_date, modify_date)
SELECT 'explain-a-record', 'Summarizing a record and who changed it.',
   'When the user asks about one record (what it is, its status, what changed, who changed it):
1. Read it with get_record. If they ask what changed or who changed it, also call get_record_history.
2. Summarize in plain language: what the record is, its current state, and the few fields that matter for the question. Use the field labels, not the field names.
3. For history, list changes newest first as "date - who - field: old -> new", at most ten, and say if there are more.
4. Link to the record.',
   TRUE, 'explain,summarize,summary,tell me about,what is,status,history,changed,who changed,audit', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM assistant_skill WHERE name = 'explain-a-record');
-- rollback DELETE FROM assistant_skill WHERE name = 'explain-a-record';

-- changeset qbit-assistant:defaults-5
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM assistant_skill WHERE name = 'safe-changes'
-- comment: Starter skill: proposing changes that the user confirms.
INSERT INTO assistant_skill (name, description, instructions, enabled, trigger_keywords, create_date, modify_date)
SELECT 'safe-changes', 'Proposing creates, updates and deletes safely.',
   'When the user asks you to create, change or delete records:
1. If you have no create_record, update_record or delete_record tool for that table, say the assistant cannot change it and offer to open the screen where they can.
2. Read the record first, then restate exactly what will change (field: old -> new) before proposing it.
3. Propose one change at a time unless the user clearly asked for several. Changes wait for the user to confirm them in the panel; never say a change is done until a tool result says so.
4. For many rows from a file, use the bulk load tools and always run bulk_load_dry_run and show its summary before bulk_load_run.',
   TRUE, 'change,update,set,edit,create,add,new,delete,remove,fix,rename,import,load', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM assistant_skill WHERE name = 'safe-changes');
-- rollback DELETE FROM assistant_skill WHERE name = 'safe-changes';

-- changeset qbit-assistant:defaults-6
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM assistant_skill WHERE name = 'access-questions'
-- comment: Starter skill: explaining missing or denied data without guessing.
INSERT INTO assistant_skill (name, description, instructions, enabled, trigger_keywords, create_date, modify_date)
SELECT 'access-questions', 'Explaining why data is missing or not available.',
   'When the user cannot find something, or a tool returns DENIED or NOT_FOUND:
1. Say plainly that the information is not available to them through the assistant. Do not guess what it contains and do not try other tools to get around it.
2. Explain the likely reason in one sentence: their permissions, a filter that excludes it, or the table being off for the assistant.
3. Suggest the next step: check the filter, open the list screen, or ask an administrator for access.',
   TRUE, 'cannot see,can''t see,missing,not found,denied,access,permission,not allowed,hidden', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM assistant_skill WHERE name = 'access-questions');
-- rollback DELETE FROM assistant_skill WHERE name = 'access-questions';

-- changeset qbit-assistant:defaults-7
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-table-exists tableName:assistant_profile_skill
-- comment: Offer the starter skills through the default profile (skills reach the model only through a profile). Pairs that already exist are left alone.
INSERT INTO assistant_profile_skill (profile_id, skill_id, create_date, modify_date)
SELECT p.id, s.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM assistant_profile p, assistant_skill s
WHERE p.name = 'default'
  AND s.name IN ('find-and-count', 'explain-a-record', 'safe-changes', 'access-questions')
  AND NOT EXISTS (SELECT 1 FROM assistant_profile_skill ps WHERE ps.profile_id = p.id AND ps.skill_id = s.id);
-- rollback DELETE FROM assistant_profile_skill WHERE profile_id IN (SELECT id FROM assistant_profile WHERE name = 'default') AND skill_id IN (SELECT id FROM assistant_skill WHERE name IN ('find-and-count', 'explain-a-record', 'safe-changes', 'access-questions'));

-- changeset qbit-assistant:defaults-8
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM assistant_budget WHERE name = 'Monthly spend (whole instance)'
-- comment: A monthly cost budget for the whole instance: warn at 80%, switch to the cheapest allowed model (local first) at 100%, stop at 120%. Change the limit to suit; overrides raise it for a while.
INSERT INTO assistant_budget (name, enabled, scope_type, period, metric, limit_value, warn_percent, at_limit_action, hard_ceiling_percent, notes, create_date, modify_date)
SELECT 'Monthly spend (whole instance)', TRUE, 'INSTANCE', 'MONTH', 'COST_USD', 50, 80, 'DOWNGRADE', 120,
   'Starter budget. Warns everyone at 80% of $50 a month, switches to the cheapest allowed model at 100% (or stops when there is none), and stops at 120%. Set Downgrade Model to choose the fallback; add an override to raise it temporarily.',
   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM assistant_budget WHERE name = 'Monthly spend (whole instance)');
-- rollback DELETE FROM assistant_budget WHERE name = 'Monthly spend (whole instance)';

-- changeset qbit-assistant:defaults-9
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-table-exists tableName:assistant_model_price
-- comment: Anthropic first-party list prices, USD per million tokens (2026-10). Rows that already exist (by type and model) are left alone. Empty cache prices use the standard multipliers (read 0.1x, 5 minute write 1.25x, 1 hour write 2x input).
INSERT INTO assistant_model_price (provider_type, model, input_usd_per_m_tok, output_usd_per_m_tok, cache_read_usd_per_m_tok, cache_write5m_usd_per_m_tok, cache_write1h_usd_per_m_tok, notes, create_date, modify_date)
SELECT v.provider_type, v.model, v.input_price, v.output_price, v.cache_read, v.cache_write5m, v.cache_write1h, v.notes, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
   ('anthropic', 'claude-fable-5-1', 10.00, 50.00, 0.25, 12.50, 20.00, 'Anthropic list price, 2026-10'),
   ('anthropic', 'claude-fable-5', 10.00, 50.00, CAST(NULL AS NUMERIC), CAST(NULL AS NUMERIC), CAST(NULL AS NUMERIC), 'Anthropic list price, 2026-10'),
   ('anthropic', 'claude-opus-5-5', 4.00, 20.00, 0.20, 5.00, 8.00, 'Anthropic list price, 2026-10'),
   ('anthropic', 'claude-opus-5', 5.00, 25.00, 0.50, 6.25, 10.00, 'Anthropic list price, 2026-10'),
   ('anthropic', 'claude-opus-4-8', 5.00, 25.00, 0.50, 6.25, 10.00, 'Anthropic list price, 2026-10'),
   ('anthropic', 'claude-opus-4-7', 5.00, 25.00, 0.50, 6.25, 10.00, 'Anthropic list price, 2026-10'),
   ('anthropic', 'claude-opus-4-6', 5.00, 25.00, 0.50, 6.25, 10.00, 'Anthropic list price, 2026-10'),
   ('anthropic', 'claude-sonnet-5-5', 2.00, 10.00, 0.20, 2.50, 4.00, 'Anthropic list price, 2026-10'),
   ('anthropic', 'claude-sonnet-5', 2.00, 10.00, 0.20, 2.50, 4.00, 'Anthropic list price, 2026-10'),
   ('anthropic', 'claude-sonnet-4-6', 3.00, 15.00, 0.30, 3.75, 6.00, 'Anthropic list price, 2026-10'),
   ('anthropic', 'claude-haiku-5-5', 0.10, 0.50, 0.01, 0.125, 0.20, 'Anthropic list price, 2026-10 (prompts up to 100K tokens)'),
   ('anthropic', 'claude-haiku-4-5', 1.00, 5.00, 0.10, 1.25, 2.00, 'Anthropic list price, 2026-10')
) AS v (provider_type, model, input_price, output_price, cache_read, cache_write5m, cache_write1h, notes)
WHERE NOT EXISTS (SELECT 1 FROM assistant_model_price p WHERE p.provider_type = v.provider_type AND p.model = v.model);
-- rollback SELECT 1; -- prices may have been edited; keep them

-- changeset qbit-assistant:defaults-10
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM assistant_scheduled_task WHERE name = 'Example: weekly change summary'
-- comment: A disabled example scheduled task that shows the shape of one. It never runs until an administrator sets the run-as user and an allowed credential variable and turns it on.
INSERT INTO assistant_scheduled_task (name, enabled, prompt, run_as_user_id, credential_env_name, profile_id, cron_expression, timezone, output_mode, write_mode_requested, max_cost_usd_per_run, create_date, modify_date)
SELECT 'Example: weekly change summary', FALSE,
   'Summarize what changed in the last 7 days in the tables I use most: counts of new and changed records per table, and anything that looks unusual. Include links to the lists.',
   'set-the-run-as-user-id', 'ASSISTANT_SCHEDULED_TOKEN', p.id, '0 0 8 ? * MON', 'UTC', 'RECORD', FALSE, 0.50,
   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (SELECT MIN(id) AS id FROM assistant_profile WHERE name = 'default') p
WHERE NOT EXISTS (SELECT 1 FROM assistant_scheduled_task WHERE name = 'Example: weekly change summary');
-- rollback DELETE FROM assistant_scheduled_task WHERE name = 'Example: weekly change summary' AND enabled = FALSE;
