-- Reference PostgreSQL DDL for the qbit-assistant tables (snake_case, as mapped by a
-- snake_case table customizer). Adjust to your conventions; the QBit only needs the
-- QQQ field names to map onto columns.

CREATE TABLE assistant_conversation
(
   id SERIAL PRIMARY KEY,
   user_id VARCHAR(255),
   title VARCHAR(255),
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
   model VARCHAR(64),
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
   stop_reason VARCHAR(64),
   error_code VARCHAR(32),
   cancel_reason VARCHAR(64),
   model VARCHAR(64),
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
   result_summary VARCHAR(500),
   result_json TEXT,
   redactions_json TEXT,
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
