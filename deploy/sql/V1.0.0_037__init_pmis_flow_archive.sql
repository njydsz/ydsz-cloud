-- ============================================================
-- V1.0.0_037  P2-3 流程历史归档表
-- ============================================================
-- 说明：流程实例/任务/变量归档到冷存储表，减小主表压力。
--   - pmis_flow_his_instance：归档的流程实例（已完成且超过 retention 天数）
--   - pmis_flow_his_variable：归档的流程变量（独立表，instance 归档时同步迁移）
--   - 触发：FlowHistoryArchiveJobHandler 每天 03:00 扫描
--   - 默认归档阈值：30 天（可在 pmis_job.params 配置）
-- ============================================================

-- 归档实例表（结构与 pmis_flow_instance 一致 + archived_at 字段）
DROP TABLE IF EXISTS pmis_flow_his_instance;
CREATE TABLE pmis_flow_his_instance (
    id                 BIGSERIAL    PRIMARY KEY,
    flow_code          VARCHAR(64)  NOT NULL,
    flow_name          VARCHAR(128),
    definition_id      BIGINT,
    flow_version       VARCHAR(20),
    business_type      VARCHAR(64),
    business_id        VARCHAR(64),
    business_no        VARCHAR(64),
    title              VARCHAR(256),
    initiator_id       BIGINT,
    initiator_name     VARCHAR(64),
    current_node_code  VARCHAR(64),
    current_node_name  VARCHAR(128),
    variable           TEXT,
    flow_status        VARCHAR(16)  NOT NULL,
    activity_status    SMALLINT     NOT NULL DEFAULT 1,
    start_at           TIMESTAMP,
    end_at             TIMESTAMP,
    duration_ms        BIGINT,
    created_by         BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL,
    updated_by         BIGINT       NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP    NOT NULL,
    archived_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id          BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id  VARCHAR(64)
);

COMMENT ON TABLE  pmis_flow_his_instance IS '流程实例归档表: 已完成且超过 retention 天数的实例迁移至此';
COMMENT ON COLUMN pmis_flow_his_instance.archived_at IS '归档时间';

CREATE INDEX idx_pfhi_business   ON pmis_flow_his_instance(business_type, business_id);
CREATE INDEX idx_pfhi_flow_code  ON pmis_flow_his_instance(flow_code);
CREATE INDEX idx_pfhi_flow_status ON pmis_flow_his_instance(flow_status);
CREATE INDEX idx_pfhi_initiator  ON pmis_flow_his_instance(initiator_id);
CREATE INDEX idx_pfhi_end_at     ON pmis_flow_his_instance(end_at);
CREATE INDEX idx_pfhi_tenant     ON pmis_flow_his_instance(tenant_id);
CREATE INDEX idx_pfhi_archived_at ON pmis_flow_his_instance(archived_at);

-- 归档变量表（用于归档 instance 时同步迁移 variable 字段中的大 JSON）
DROP TABLE IF EXISTS pmis_flow_his_variable;
CREATE TABLE pmis_flow_his_variable (
    id            BIGSERIAL    PRIMARY KEY,
    instance_id   BIGINT       NOT NULL,
    var_key       VARCHAR(128) NOT NULL,
    var_value     TEXT,
    archived_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE pmis_flow_his_variable IS '流程变量归档表: instance.variable JSON 拆分到独立行';

CREATE INDEX idx_pfhv_instance ON pmis_flow_his_variable(instance_id);
CREATE INDEX idx_pfhv_key      ON pmis_flow_his_variable(instance_id, var_key);

-- 归档统计视图（管理员可见：实例总数/已归档/未归档）
DROP VIEW IF EXISTS pmis_view_flow_archive_stats;
CREATE VIEW pmis_view_flow_archive_stats AS
SELECT
    COALESCE(main.flow_code, his.flow_code)   AS flow_code,
    COALESCE(main.tenant_id, his.tenant_id)   AS tenant_id,
    COALESCE(main.cnt_main, 0)                AS active_count,
    COALESCE(his.cnt_his, 0)                  AS archived_count
FROM
    (SELECT flow_code, tenant_id, COUNT(*) AS cnt_main
     FROM pmis_flow_instance
     WHERE deleted = 0
     GROUP BY flow_code, tenant_id) main
FULL OUTER JOIN
    (SELECT flow_code, tenant_id, COUNT(*) AS cnt_his
     FROM pmis_flow_his_instance
     GROUP BY flow_code, tenant_id) his
    ON main.flow_code = his.flow_code AND main.tenant_id = his.tenant_id;

COMMENT ON VIEW pmis_view_flow_archive_stats IS '流程归档统计: active_count 主表实例数 / archived_count 已归档实例数';

-- 注册归档任务到 pmis_job（每日 03:00 触发，阈值 30 天）
INSERT INTO pmis_job (job_name, handler, cron, status, params, tenant_id, created_at, updated_at, deleted)
VALUES
    ('flowHistoryArchiveJobHandler', 'flowHistoryArchiveJobHandler', '0 0 3 * * ?', 'NORMAL', '{"days":30,"batchSize":100,"maxProcessMs":30000}', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (job_name, tenant_id) DO NOTHING;
