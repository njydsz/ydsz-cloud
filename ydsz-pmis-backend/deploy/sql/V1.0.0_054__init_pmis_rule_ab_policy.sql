-- AB Test 自动回滚策略表（P1-10）
-- 每条启用 canary 的规则可以配置自动回滚策略：
-- - error_rate_threshold：canary 桶错误率超过该比例则触发回滚（0.0~1.0）
-- - min_sample_size：最小样本数（避免数据不足时误判）
-- - auto_rollback_enabled：是否启用自动回滚
-- - rollback_action：AUTO（自动回滚）/ NOTIFY（仅通知）
-- - check_window_minutes：监控窗口（分钟），默认 60
-- - last_evaluated_at / last_rollback_at：审计字段

CREATE TABLE IF NOT EXISTS pmis_rule_ab_policy (
    id                          BIGSERIAL PRIMARY KEY,
    rule_code                   VARCHAR(128) NOT NULL UNIQUE,
    auto_rollback_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    rollback_action             VARCHAR(16)  NOT NULL DEFAULT 'AUTO',
    error_rate_threshold        NUMERIC(5,4) NOT NULL DEFAULT 0.3000,
    min_sample_size             INTEGER      NOT NULL DEFAULT 100,
    check_window_minutes        INTEGER      NOT NULL DEFAULT 60,
    notify_channels             VARCHAR(256) DEFAULT 'IN_APP,EMAIL',
    description                 VARCHAR(256),
    last_evaluated_at           TIMESTAMP,
    last_rollback_at            TIMESTAMP,
    created_by                  VARCHAR(64),
    created_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  VARCHAR(64),
    updated_at                  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ab_policy_rule_code
    ON pmis_rule_ab_policy (rule_code);

COMMENT ON TABLE  pmis_rule_ab_policy IS 'AB Test 自动回滚策略表（P1-10）';
COMMENT ON COLUMN pmis_rule_ab_policy.auto_rollback_enabled IS '是否启用自动回滚';
COMMENT ON COLUMN pmis_rule_ab_policy.rollback_action IS '回滚动作：AUTO 自动回滚 / NOTIFY 仅通知 Owner';
COMMENT ON COLUMN pmis_rule_ab_policy.error_rate_threshold IS 'canary 桶错误率阈值（0~1.0），超过则触发';
COMMENT ON COLUMN pmis_rule_ab_policy.min_sample_size IS '最小样本数，避免数据不足时误判';
COMMENT ON COLUMN pmis_rule_ab_policy.check_window_minutes IS '监控窗口（分钟）';

-- AB Test 回滚历史表（审计）
CREATE TABLE IF NOT EXISTS pmis_rule_ab_rollback (
    id              BIGSERIAL PRIMARY KEY,
    rule_code       VARCHAR(128) NOT NULL,
    trigger_reason  VARCHAR(64)  NOT NULL,  -- ERROR_RATE / MANUAL / OWNER_REQUEST
    error_rate      NUMERIC(5,4),
    sample_size     BIGINT,
    from_canary     BOOLEAN      NOT NULL,
    operator        VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    notify_status   VARCHAR(32),  -- SENT / FAILED / SKIPPED
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ab_rollback_rule_code
    ON pmis_rule_ab_rollback (rule_code);
CREATE INDEX IF NOT EXISTS idx_ab_rollback_created_at
    ON pmis_rule_ab_rollback (created_at);

COMMENT ON TABLE  pmis_rule_ab_rollback IS 'AB Test 自动回滚历史（P1-10）';
COMMENT ON COLUMN pmis_rule_ab_rollback.trigger_reason IS '触发原因：ERROR_RATE=错误率超阈值 / MANUAL=人工 / OWNER_REQUEST=Owner 请求';
COMMENT ON COLUMN pmis_rule_ab_rollback.from_canary IS 'true=已从 canary 切换回主版本 / false=仅通知未回滚';
COMMENT ON COLUMN pmis_rule_ab_rollback.notify_status IS 'Owner 通知状态：SENT/FAILED/SKIPPED';
