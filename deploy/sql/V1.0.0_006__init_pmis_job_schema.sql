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

COMMENT ON TABLE pmis_job IS '动态定时任务定义表: 支持运行时增删改触发频率的定时任务(Quartz/XXL-JOB)';
COMMENT ON COLUMN pmis_job.id IS '主键 ID';
COMMENT ON COLUMN pmis_job.job_name IS '任务名称(展示用)';
COMMENT ON COLUMN pmis_job.job_group IS '任务分组(如 DEFAULT/RECONCILE/ALERT)';
COMMENT ON COLUMN pmis_job.job_key IS '任务唯一 KEY(调度器使用)';
COMMENT ON COLUMN pmis_job.handler IS '任务处理器 Bean 名称(Spring Bean)';
COMMENT ON COLUMN pmis_job.cron_expression IS 'Cron 表达式(如 0 0 2 * * ? = 每日 02:00)';
COMMENT ON COLUMN pmis_job.params_json IS '任务参数 JSON';
COMMENT ON COLUMN pmis_job.status IS '任务状态: NORMAL 正常 / PAUSED 暂停 / ERROR 异常 / COMPLETE 一次性任务完成';
COMMENT ON COLUMN pmis_job.remark IS '任务说明';
COMMENT ON COLUMN pmis_job.next_fire_time IS '下次触发时间';
COMMENT ON COLUMN pmis_job.last_fire_time IS '上次触发时间';
COMMENT ON COLUMN pmis_job.fire_count IS '累计触发次数';
COMMENT ON COLUMN pmis_job.success_count IS '成功执行次数';
COMMENT ON COLUMN pmis_job.fail_count IS '失败次数(超过阈值告警)';
COMMENT ON COLUMN pmis_job.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_job.create_by IS '创建人 ID';
COMMENT ON COLUMN pmis_job.create_time IS '创建时间';
COMMENT ON COLUMN pmis_job.update_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_job.update_time IS '最后修改时间';
COMMENT ON COLUMN pmis_job.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

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

COMMENT ON TABLE pmis_job_log IS '任务执行日志: 每次任务执行的耗时/入参/出参/异常,用于排障与审计';
COMMENT ON COLUMN pmis_job_log.id IS '主键 ID';
COMMENT ON COLUMN pmis_job_log.job_id IS '任务 ID(关联 pmis_job.id)';
COMMENT ON COLUMN pmis_job_log.job_key IS '任务 KEY(冗余,避免连表)';
COMMENT ON COLUMN pmis_job_log.start_time IS '任务开始时间';
COMMENT ON COLUMN pmis_job_log.end_time IS '任务结束时间';
COMMENT ON COLUMN pmis_job_log.duration_ms IS '任务执行耗时(毫秒)';
COMMENT ON COLUMN pmis_job_log.status IS '执行状态: RUNNING 进行中 / SUCCESS 成功 / FAILED 失败';
COMMENT ON COLUMN pmis_job_log.error_message IS '异常堆栈(失败时填充)';
COMMENT ON COLUMN pmis_job_log.params_json IS '执行参数 JSON';
COMMENT ON COLUMN pmis_job_log.result_json IS '执行结果 JSON';
COMMENT ON COLUMN pmis_job_log.trace_id IS '链路追踪 ID(SkyWalking/TLog)';
COMMENT ON COLUMN pmis_job_log.create_time IS '日志写入时间';
COMMENT ON COLUMN pmis_job_log.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX idx_pjl_job_id ON pmis_job_log(job_id);
CREATE INDEX idx_pjl_job_key ON pmis_job_log(job_key);
CREATE INDEX idx_pjl_status ON pmis_job_log(status);
CREATE INDEX idx_pjl_start_time ON pmis_job_log(start_time);
