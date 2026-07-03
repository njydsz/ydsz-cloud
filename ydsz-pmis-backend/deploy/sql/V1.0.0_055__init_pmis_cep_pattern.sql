-- 复杂事件处理模式表（P2-13）
-- 每条记录代表一个 CEP 模式（TIME_WINDOW / SEQUENCE / AGGREGATE / ABSENCE）
-- pattern_json: CEPPattern 序列化的 JSON，包含步骤、过滤条件、阈值等
-- enabled: 是否启用
-- 触发条件：CEPEngine.feed(event) 后由引擎自动评估

CREATE TABLE IF NOT EXISTS pmis_cep_pattern (
    id              BIGSERIAL PRIMARY KEY,
    pattern_id      VARCHAR(128) NOT NULL UNIQUE,
    pattern_type    VARCHAR(32)  NOT NULL,
    rule_code       VARCHAR(128),
    pattern_name    VARCHAR(256) NOT NULL,
    pattern_json    TEXT         NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    description     VARCHAR(512),
    hit_count       BIGINT       NOT NULL DEFAULT 0,
    last_hit_at     TIMESTAMP,
    created_by      VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cep_pattern_rule_code
    ON pmis_cep_pattern (rule_code);
CREATE INDEX IF NOT EXISTS idx_cep_pattern_enabled
    ON pmis_cep_pattern (enabled);

COMMENT ON TABLE  pmis_cep_pattern IS '复杂事件处理模式表（P2-13）';
COMMENT ON COLUMN pmis_cep_pattern.pattern_id   IS '模式 ID（业务唯一）';
COMMENT ON COLUMN pmis_cep_pattern.pattern_type IS '模式类型：TIME_WINDOW/SEQUENCE/AGGREGATE/ABSENCE';
COMMENT ON COLUMN pmis_cep_pattern.rule_code    IS '命中后触发的规则编码';
COMMENT ON COLUMN pmis_cep_pattern.pattern_json IS '模式定义 JSON（CEPPattern 序列化）';

-- 复杂事件命中历史（用于审计和回放）
CREATE TABLE IF NOT EXISTS pmis_cep_hit (
    id              BIGSERIAL PRIMARY KEY,
    pattern_id      VARCHAR(128) NOT NULL,
    rule_code       VARCHAR(128),
    partition_key   VARCHAR(128) NOT NULL,
    trigger_type    VARCHAR(64),
    metric_value    NUMERIC(18,4),
    event_count     INTEGER,
    context_json    TEXT,
    hit_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cep_hit_pattern
    ON pmis_cep_hit (pattern_id);
CREATE INDEX IF NOT EXISTS idx_cep_hit_hit_at
    ON pmis_cep_hit (hit_at);
CREATE INDEX IF NOT EXISTS idx_cep_hit_partition
    ON pmis_cep_hit (partition_key);

COMMENT ON TABLE  pmis_cep_hit IS '复杂事件命中历史（P2-13）';
