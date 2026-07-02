-- 审计日志增加变更前后数据字段
ALTER TABLE pmis_operation_log ADD COLUMN IF NOT EXISTS before_data TEXT;
ALTER TABLE pmis_operation_log ADD COLUMN IF NOT EXISTS after_data TEXT;

COMMENT ON COLUMN pmis_operation_log.before_data IS '变更前数据（JSON 格式）';
COMMENT ON COLUMN pmis_operation_log.after_data IS '变更后数据（JSON 格式）';
