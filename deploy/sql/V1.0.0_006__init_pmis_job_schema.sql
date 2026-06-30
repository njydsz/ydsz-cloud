-- =====================================================
-- PMIS 任务调度模块 DDL
-- 版本: V1.0.0_006
-- 描述: 动态定时任务定义与执行日志
-- =====================================================

DROP TABLE IF EXISTS pmis_job;
CREATE TABLE pmis_job (
    id              BIGSERIAL PRIMARY KEY,
    job_name        VARCHAR(128) NOT NULL,
    job_group       VARCHAR(64)  NOT NULL DEFAULT 'DEFAULT',
    job_key         VARCHAR(128) NOT NULL,
    handler         VARCHAR(256) NOT NULL,
    cron_expression VARCHAR(128) NOT NULL,
    params_json     TEXT,
    status          VARCHAR(32)  NOT NULL DEFAULT 'NORMAL',
    remark          VARCHAR(512),
    next_fire_time  TIMESTAMP,
    last_fire_time  TIMESTAMP,
    fire_count      BIGINT       NOT NULL DEFAULT 0,
    success_count   BIGINT       NOT NULL DEFAULT 0,
    fail_count      BIGINT       NOT NULL DEFAULT 0,
    tenant_id       BIGINT       DEFAULT 1,
    create_by       BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_job IS '动态定时任务定义表';
COMMENT ON COLUMN pmis_job.job_key IS '任务唯一 KEY(用于调度)';
COMMENT ON COLUMN pmis_job.handler IS '任务处理器 Bean 名称';
COMMENT ON COLUMN pmis_job.cron_expression IS 'Cron 表达式';
COMMENT ON COLUMN pmis_job.status IS '状态: NORMAL/PAUSED/ERROR';
COMMENT ON COLUMN pmis_job.fire_count IS '总触发次数';
COMMENT ON COLUMN pmis_job.success_count IS '成功次数';
COMMENT ON COLUMN pmis_job.fail_count IS '失败次数';

CREATE UNIQUE INDEX uk_pmis_job_key ON pmis_job(job_key);
CREATE INDEX idx_pmis_job_status ON pmis_job(status);
CREATE INDEX idx_pmis_job_group ON pmis_job(job_group);
CREATE INDEX idx_pmis_job_tenant ON pmis_job(tenant_id);

DROP TABLE IF EXISTS pmis_job_log;
CREATE TABLE pmis_job_log (
    id              BIGSERIAL PRIMARY KEY,
    job_id          BIGINT       NOT NULL,
    job_key         VARCHAR(128) NOT NULL,
    start_time      TIMESTAMP    NOT NULL,
    end_time        TIMESTAMP,
    duration_ms     BIGINT,
    status          VARCHAR(32)  NOT NULL,
    error_message   TEXT,
    params_json     TEXT,
    result_json     TEXT,
    trace_id        VARCHAR(64),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_job_log IS '任务执行日志';
COMMENT ON COLUMN pmis_job_log.status IS '状态: RUNNING/SUCCESS/FAILED';

CREATE INDEX idx_pjl_job_id ON pmis_job_log(job_id);
CREATE INDEX idx_pjl_job_key ON pmis_job_log(job_key);
CREATE INDEX idx_pjl_status ON pmis_job_log(status);
CREATE INDEX idx_pjl_start_time ON pmis_job_log(start_time);
