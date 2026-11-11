-- Raw historical prompts/results are intentionally removed to enforce metadata-only auditing.
ALTER TABLE ai_tool_invocation_audit ALTER COLUMN prompt_or_query DROP NOT NULL;
ALTER TABLE ai_tool_invocation_audit ADD COLUMN actor_user_id UUID;
ALTER TABLE ai_tool_invocation_audit ADD COLUMN correlation_id VARCHAR(100);
ALTER TABLE ai_tool_invocation_audit ADD COLUMN outcome VARCHAR(32);
UPDATE ai_tool_invocation_audit SET prompt_or_query = NULL, input_parameters = NULL,
    output_result = NULL, tool_name = 'LEGACY', caller_persona = 'LEGACY', outcome = 'LEGACY_REDACTED';
CREATE INDEX idx_ai_audit_actor_created ON ai_tool_invocation_audit(actor_user_id, created_at);
