-- Reference PostgreSQL DDL for the qbit-assistant tables (snake_case, as mapped by a
-- snake_case table customizer). Adjust to your conventions; the QBit only needs the
-- QQQ field names to map onto columns.

CREATE TABLE assistant_conversation
(
   id SERIAL PRIMARY KEY,
   user_id VARCHAR(255),
   title TEXT,
   max_classification VARCHAR(16),
   status VARCHAR(16),
   pinned BOOLEAN,
   last_message_at TIMESTAMP WITH TIME ZONE,
   unread_count INTEGER,
   context_json TEXT,
   total_tokens_in BIGINT,
   total_tokens_out BIGINT,
   total_cost_micros BIGINT,
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX assistant_conversation_user_id ON assistant_conversation (user_id);

CREATE TABLE assistant_message
(
   id SERIAL PRIMARY KEY,
   conversation_id INTEGER,
   user_id VARCHAR(255),
   parent_message_id INTEGER,
   role VARCHAR(16),
   status VARCHAR(16),
   turn_id INTEGER,
   seq INTEGER,
   model VARCHAR(200),
   parts_json TEXT,
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX assistant_message_user_id ON assistant_message (user_id);
CREATE INDEX assistant_message_conversation_id ON assistant_message (conversation_id);

CREATE TABLE assistant_turn
(
   id SERIAL PRIMARY KEY,
   conversation_id INTEGER,
   user_id VARCHAR(255),
   user_message_id INTEGER,
   assistant_message_id INTEGER,
   status VARCHAR(16),
   classification VARCHAR(16),
   classification_tables VARCHAR(500),
   stop_reason VARCHAR(64),
   error_code VARCHAR(32),
   cancel_reason VARCHAR(64),
   model VARCHAR(200),
   started_at TIMESTAMP WITH TIME ZONE,
   ended_at TIMESTAMP WITH TIME ZONE,
   tool_call_count INTEGER,
   model_call_count INTEGER,
   input_tokens BIGINT,
   output_tokens BIGINT,
   cache_read_tokens BIGINT,
   cache_write_tokens BIGINT,
   cost_micros BIGINT,
   provider_request_ids TEXT,
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX assistant_turn_user_id ON assistant_turn (user_id);
CREATE INDEX assistant_turn_conversation_id ON assistant_turn (conversation_id);

CREATE TABLE assistant_turn_event
(
   id SERIAL PRIMARY KEY,
   turn_id INTEGER,
   user_id VARCHAR(255),
   seq INTEGER,
   event_type VARCHAR(32),
   data_json TEXT,
   create_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX assistant_turn_event_user_id ON assistant_turn_event (user_id);
CREATE INDEX assistant_turn_event_turn ON assistant_turn_event (turn_id, seq);

CREATE TABLE assistant_tool_call
(
   id SERIAL PRIMARY KEY,
   turn_id INTEGER,
   conversation_id INTEGER,
   user_id VARCHAR(255),
   tool_call_id VARCHAR(128),
   tool_name VARCHAR(64),
   input_json TEXT,
   outcome VARCHAR(16),
   permission_check VARCHAR(255),
   table_name VARCHAR(128),
   process_name VARCHAR(128),
   widget_name VARCHAR(128),
   row_count INTEGER,
   duration_ms BIGINT,
   result_summary TEXT,
   result_json TEXT,
   redactions_json TEXT,
   ui_api_operation VARCHAR(255),
   http_status INTEGER,
   audit_href VARCHAR(500),
   pending_action_id INTEGER,
   create_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX assistant_tool_call_user_id ON assistant_tool_call (user_id);
CREATE INDEX assistant_tool_call_conversation_id ON assistant_tool_call (conversation_id);
CREATE INDEX assistant_tool_call_turn ON assistant_tool_call (turn_id);

CREATE TABLE assistant_feedback
(
   id SERIAL PRIMARY KEY,
   message_id INTEGER,
   conversation_id INTEGER,
   user_id VARCHAR(255),
   rating INTEGER,
   reason VARCHAR(64),
   comment TEXT,
   create_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX assistant_feedback_user_id ON assistant_feedback (user_id);
CREATE INDEX assistant_feedback_conversation_id ON assistant_feedback (conversation_id);

CREATE TABLE assistant_policy
(
   id SERIAL PRIMARY KEY,
   name VARCHAR(64),
   kill_switch BOOLEAN,
   kill_switch_reason VARCHAR(255),
   main_model VARCHAR(64),
   main_effort VARCHAR(16),
   user_daily_tokens_soft BIGINT,
   user_daily_tokens_hard BIGINT,
   user_daily_cost_micros_soft BIGINT,
   user_daily_cost_micros_hard BIGINT,
   instance_monthly_tokens_soft BIGINT,
   instance_monthly_tokens_hard BIGINT,
   instance_monthly_cost_micros_soft BIGINT,
   instance_monthly_cost_micros_hard BIGINT,
   writes_require_confirmation BOOLEAN,
   allowed_tools VARCHAR(1000),
   max_rows_per_call INTEGER,
   redact_pii BOOLEAN,
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE
);

CREATE TABLE assistant_usage_daily
(
   id SERIAL PRIMARY KEY,
   scope VARCHAR(16),
   user_id VARCHAR(255),
   usage_date DATE,
   turn_count INTEGER,
   input_tokens BIGINT,
   output_tokens BIGINT,
   cache_read_tokens BIGINT,
   cache_write_tokens BIGINT,
   cost_micros BIGINT,
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX assistant_usage_daily_user_id ON assistant_usage_daily (user_id);
CREATE INDEX assistant_usage_daily_date ON assistant_usage_daily (usage_date, scope);

-- 0.1.0-RC.4: provider settings, model prices and per-call usage.
-- api_key_status is filled when a row is read (never written); the API key itself
-- is never stored: api_key_env_name holds only the NAME of an environment variable.

CREATE TABLE assistant_provider
(
   id SERIAL PRIMARY KEY,
   name VARCHAR(100) NOT NULL UNIQUE,
   provider_type VARCHAR(50) NOT NULL,
   kind VARCHAR(30),
   location VARCHAR(10),
   hipaa_eligible BOOLEAN DEFAULT FALSE,
   region VARCHAR(50),
   cloud_project VARCHAR(100),
   api_version VARCHAR(30),
   connect_timeout_seconds INTEGER DEFAULT 10 CHECK (connect_timeout_seconds BETWEEN 1 AND 600),
   request_timeout_seconds INTEGER DEFAULT 120 CHECK (request_timeout_seconds BETWEEN 1 AND 600),
   last_discovery_at TIMESTAMP WITH TIME ZONE,
   last_discovery_status VARCHAR(16),
   last_discovery_error VARCHAR(500),
   model VARCHAR(200),
   enabled BOOLEAN,
   is_default BOOLEAN,
   base_url VARCHAR(255),
   tls_ca_bundle VARCHAR(100),
   api_key_env_name VARCHAR(100),
   api_key_status VARCHAR(200),
   monthly_cost_cap_usd NUMERIC(12, 2),
   max_output_tokens_per_request INTEGER,
   max_concurrent_requests INTEGER,
   last_test_at TIMESTAMP WITH TIME ZONE,
   last_test_status VARCHAR(16),
   last_test_latency_ms INTEGER,
   last_test_model VARCHAR(200),
   last_test_error VARCHAR(500),
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE
);

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

CREATE TABLE assistant_model_price
(
   id SERIAL PRIMARY KEY,
   provider_type VARCHAR(50) NOT NULL,
   model VARCHAR(100) NOT NULL,
   input_usd_per_m_tok NUMERIC(12, 4) NOT NULL,
   output_usd_per_m_tok NUMERIC(12, 4) NOT NULL,
   cache_read_usd_per_m_tok NUMERIC(12, 4),
   cache_write5m_usd_per_m_tok NUMERIC(12, 4),
   cache_write1h_usd_per_m_tok NUMERIC(12, 4),
   notes VARCHAR(255),
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE,
   UNIQUE (provider_type, model)
);

CREATE TABLE assistant_usage
(
   id SERIAL PRIMARY KEY,
   user_id VARCHAR(255),
   conversation_id INTEGER,
   turn_id INTEGER,
   provider_id INTEGER,
   provider_type VARCHAR(50),
   model VARCHAR(200),
   purpose VARCHAR(16),
   status VARCHAR(16),
   error_kind VARCHAR(32),
   http_status INTEGER,
   latency_ms INTEGER,
   input_tokens BIGINT,
   output_tokens BIGINT,
   cache_read_tokens BIGINT,
   cache_write_tokens BIGINT,
   cost_micros BIGINT,
   request_id VARCHAR(100),
   create_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX assistant_usage_create_date ON assistant_usage (create_date);
CREATE INDEX assistant_usage_provider ON assistant_usage (provider_id, create_date);
CREATE INDEX assistant_usage_conversation ON assistant_usage (conversation_id);
CREATE INDEX assistant_usage_user_id ON assistant_usage (user_id);

-- Anthropic list prices, USD per million tokens (cached 2026-10-06). Cache writes:
-- 1.25x input (5 minute) and 2x input (1 hour). Re-check before budgeting.
INSERT INTO assistant_model_price (provider_type, model, input_usd_per_m_tok, output_usd_per_m_tok, cache_read_usd_per_m_tok, cache_write5m_usd_per_m_tok, cache_write1h_usd_per_m_tok, notes, create_date, modify_date)
VALUES
   ('anthropic', 'claude-sonnet-5-5', 2.00, 10.00, 0.20, 2.50, 4.00, 'Anthropic list price, 2026-10', NOW(), NOW()),
   ('anthropic', 'claude-opus-5-5', 4.00, 20.00, 0.20, 5.00, 8.00, 'Anthropic list price, 2026-10', NOW(), NOW()),
   ('anthropic', 'claude-haiku-5-5', 0.10, 0.50, 0.01, 0.125, 0.20, 'Anthropic list price, 2026-10 (prompts up to 100K tokens)', NOW(), NOW()),
   ('anthropic', 'claude-fable-5-1', 10.00, 50.00, 0.25, 12.50, 20.00, 'Anthropic list price, 2026-10', NOW(), NOW()),
   ('anthropic', 'claude-opus-5', 5.00, 25.00, 0.50, 6.25, 10.00, 'Anthropic list price, 2026-10', NOW(), NOW()),
   ('anthropic', 'claude-sonnet-5', 2.00, 10.00, 0.20, 2.50, 4.00, 'Anthropic list price, 2026-10', NOW(), NOW()),
   ('anthropic', 'claude-opus-4-8', 5.00, 25.00, 0.50, 6.25, 10.00, 'Anthropic list price, 2026-10', NOW(), NOW()),
   ('anthropic', 'claude-sonnet-4-6', 3.00, 15.00, 0.30, 3.75, 6.00, 'Anthropic list price, 2026-10', NOW(), NOW()),
   ('anthropic', 'claude-haiku-4-5', 1.00, 5.00, 0.10, 1.25, 2.00, 'Anthropic list price, 2026-10', NOW(), NOW());

-- 0.1.0-RC.6: the assistant's data access per table / process, and writes waiting
-- for the user's confirmation. Existing databases: add the tool-call and policy
-- columns above with ALTER TABLE ... ADD COLUMN IF NOT EXISTS.
CREATE TABLE assistant_data_access
(
   id SERIAL PRIMARY KEY,
   object_type VARCHAR(16) NOT NULL,
   object_name VARCHAR(128) NOT NULL,
   mode VARCHAR(16) NOT NULL,
   notes VARCHAR(500),
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE,
   CONSTRAINT assistant_data_access_object UNIQUE (object_type, object_name)
);

CREATE TABLE assistant_pending_action
(
   id SERIAL PRIMARY KEY,
   conversation_id INTEGER,
   turn_id INTEGER,
   user_id VARCHAR(255),
   tool_call_id VARCHAR(128),
   tool_name VARCHAR(64),
   ui_api_operation VARCHAR(255),
   table_name VARCHAR(128),
   record_id VARCHAR(128),
   params_json TEXT,
   summary TEXT,
   status VARCHAR(16),
   http_status INTEGER,
   result_summary TEXT,
   expires_at TIMESTAMP WITH TIME ZONE,
   decided_at TIMESTAMP WITH TIME ZONE,
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX assistant_pending_action_user_id ON assistant_pending_action (user_id);
CREATE INDEX assistant_pending_action_conversation_id ON assistant_pending_action (conversation_id);

-- 0.1.0-RC.6: profiles (persona) and skills. Text the model reads; never tools or access.
CREATE TABLE assistant_profile
(
   id SERIAL PRIMARY KEY,
   name VARCHAR(64) NOT NULL UNIQUE,
   display_name VARCHAR(64) NOT NULL,
   icon VARCHAR(64),
   personality TEXT,
   greeting VARCHAR(500),
   starter_prompts TEXT,
   is_default BOOLEAN,
   enabled BOOLEAN,
   app_name VARCHAR(128),
   permission_name VARCHAR(128),
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE
);

CREATE TABLE assistant_skill
(
   id SERIAL PRIMARY KEY,
   name VARCHAR(64) NOT NULL UNIQUE,
   description VARCHAR(500),
   instructions TEXT NOT NULL,
   enabled BOOLEAN,
   trigger_keywords VARCHAR(500),
   app_names VARCHAR(500),
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE
);

CREATE TABLE assistant_profile_skill
(
   id SERIAL PRIMARY KEY,
   profile_id INTEGER NOT NULL,
   skill_id INTEGER NOT NULL,
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE,
   CONSTRAINT assistant_profile_skill_pair UNIQUE (profile_id, skill_id)
);

CREATE TABLE assistant_data_audit
(
   id SERIAL PRIMARY KEY,
   user_id VARCHAR(255),
   conversation_id INTEGER,
   turn_id INTEGER,
   provider_id INTEGER,
   provider_name VARCHAR(200),
   model VARCHAR(200),
   hipaa_eligible BOOLEAN,
   classification VARCHAR(16),
   tables VARCHAR(500),
   decision VARCHAR(16),
   reason VARCHAR(1000),
   create_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX assistant_data_audit_user_id ON assistant_data_audit (user_id);
CREATE INDEX assistant_data_audit_conversation_id ON assistant_data_audit (conversation_id);
CREATE INDEX assistant_data_audit_turn_id ON assistant_data_audit (turn_id);

-- P-d: data-driven budgets (UTC periods, USD or whole-token allowances).
CREATE TABLE assistant_budget
(
   id SERIAL PRIMARY KEY,
   name VARCHAR(200) NOT NULL,
   enabled BOOLEAN DEFAULT TRUE,
   scope_type VARCHAR(16) NOT NULL,
   scope_value VARCHAR(200),
   period VARCHAR(16) NOT NULL,
   metric VARCHAR(16) NOT NULL,
   limit_value NUMERIC NOT NULL CHECK (limit_value > 0),
   warn_percent INTEGER DEFAULT 80 CHECK (warn_percent >= 0 AND warn_percent < 100),
   at_limit_action VARCHAR(16) DEFAULT 'DOWNGRADE',
   downgrade_model_id INTEGER REFERENCES assistant_model(id),
   hard_ceiling_percent INTEGER DEFAULT 120 CHECK (hard_ceiling_percent >= 100),
   notes VARCHAR(2000),
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE
);
CREATE TABLE assistant_budget_override
(
   id SERIAL PRIMARY KEY,
   budget_id INTEGER NOT NULL REFERENCES assistant_budget(id),
   scope_value VARCHAR(200),
   extra_value NUMERIC,
   unlimited_until TIMESTAMP WITH TIME ZONE,
   reason VARCHAR(1000) NOT NULL,
   granted_by VARCHAR(200),
   expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
   create_date TIMESTAMP WITH TIME ZONE,
   modify_date TIMESTAMP WITH TIME ZONE,
   CHECK ((extra_value > 0 AND unlimited_until IS NULL) OR (extra_value IS NULL AND unlimited_until IS NOT NULL))
);
CREATE INDEX assistant_budget_override_budget_id ON assistant_budget_override(budget_id);
CREATE TABLE assistant_budget_event
(
   id SERIAL PRIMARY KEY,
   budget_id INTEGER NOT NULL REFERENCES assistant_budget(id),
   scope_key VARCHAR(400) NOT NULL,
   period_start TIMESTAMP WITH TIME ZONE NOT NULL,
   state VARCHAR(16) NOT NULL,
   used_value NUMERIC,
   limit_value NUMERIC,
   user_id VARCHAR(200),
   create_date TIMESTAMP WITH TIME ZONE,
   UNIQUE (budget_id, scope_key, period_start, state)
);
CREATE INDEX assistant_usage_budget_period ON assistant_usage(create_date, user_id, provider_id, model);

-- Private projection: standard admin permissions, AI NONE, no owner lock.
-- No conversation identifiers, text, request ids or credentials are copied.
CREATE TABLE assistant_budget_usage
(
   id SERIAL PRIMARY KEY,
   usage_id INTEGER NOT NULL UNIQUE,
   user_id VARCHAR(255),
   provider_id INTEGER,
   model VARCHAR(200),
   occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
   cost_micros BIGINT,
   input_tokens BIGINT,
   output_tokens BIGINT,
   cache_read_tokens BIGINT,
   cache_write_tokens BIGINT
);
CREATE INDEX assistant_budget_usage_period ON assistant_budget_usage(occurred_at, provider_id, model);
-- Run once before enabling P-d, with writes paused, to include existing usage.
INSERT INTO assistant_budget_usage
   (usage_id, user_id, provider_id, model, occurred_at, cost_micros, input_tokens, output_tokens, cache_read_tokens, cache_write_tokens)
SELECT id, user_id, provider_id, model, create_date, cost_micros, input_tokens, output_tokens, cache_read_tokens, cache_write_tokens
FROM assistant_usage
ON CONFLICT (usage_id) DO NOTHING;


-- P-e: profile routing, bounded fallback chains, provider health and turn outcome.
ALTER TABLE assistant_profile ADD COLUMN routing_mode VARCHAR(16) DEFAULT 'OFF'
   CHECK (routing_mode IN ('OFF', 'HEURISTIC', 'CLASSIFIER'));
ALTER TABLE assistant_profile ADD COLUMN simple_model_id INTEGER REFERENCES assistant_model(id);
ALTER TABLE assistant_profile ADD COLUMN complex_model_id INTEGER REFERENCES assistant_model(id);
ALTER TABLE assistant_profile ADD COLUMN classifier_model_id INTEGER REFERENCES assistant_model(id);
ALTER TABLE assistant_model ADD COLUMN fallback_model_id INTEGER REFERENCES assistant_model(id);
ALTER TABLE assistant_provider ADD COLUMN health_state VARCHAR(8) CHECK (health_state IN ('UP', 'DOWN'));
ALTER TABLE assistant_provider ADD COLUMN health_checked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE assistant_provider ADD COLUMN down_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE assistant_turn ADD COLUMN route_decision VARCHAR(16);
ALTER TABLE assistant_turn ADD COLUMN route_source VARCHAR(16);
ALTER TABLE assistant_turn ADD COLUMN fallback_count INTEGER DEFAULT 0;
ALTER TABLE assistant_turn ADD COLUMN provider_id INTEGER REFERENCES assistant_provider(id);
