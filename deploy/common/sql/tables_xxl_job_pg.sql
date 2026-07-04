# =============================================================================
#  XXL-Job 2.4 数据库初始化脚本
# -----------------------------------------------------------------------------
#  PostgreSQL 兼容版本（已适配 PMIS 主库 PostgreSQL 18）
#  使用:    psql -U pmis -d ydsz_pmis -f tables_xxl_job_pg.sql
# =============================================================================

CREATE TABLE xxl_job_info (
    id                   BIGSERIAL    NOT NULL,
    job_group            INT          NOT NULL,
    job_desc             VARCHAR(255)  NOT NULL,
    add_time             TIMESTAMP    DEFAULT NULL,
    update_time          TIMESTAMP    DEFAULT NULL,
    author               VARCHAR(64)  DEFAULT NULL,
    alarm_email          VARCHAR(255) DEFAULT NULL,
    schedule_type        VARCHAR(50)  NOT NULL DEFAULT 'NONE',
    schedule_conf        VARCHAR(128) DEFAULT NULL,
    misfire_strategy     VARCHAR(50)  NOT NULL DEFAULT 'DO_NOTHING',
    executor_route_strategy VARCHAR(50)  DEFAULT NULL,
    executor_handler     VARCHAR(255) DEFAULT NULL,
    executor_param       VARCHAR(512) DEFAULT NULL,
    executor_block_strategy VARCHAR(50)  DEFAULT NULL,
    executor_timeout     INT          NOT NULL DEFAULT '0',
    executor_fail_retry_count INT     NOT NULL DEFAULT '0',
    glue_type            VARCHAR(50)  NOT NULL,
    glue_source          TEXT,
    glue_remark          VARCHAR(128) DEFAULT NULL,
    glue_updatetime      TIMESTAMP    DEFAULT NULL,
    child_jobid          VARCHAR(255) DEFAULT NULL,
    trigger_status       SMALLINT     NOT NULL DEFAULT '0',
    trigger_last_time    BIGINT       NOT NULL DEFAULT '0',
    trigger_next_time    BIGINT       NOT NULL DEFAULT '0',
    PRIMARY KEY (id)
);
CREATE INDEX idx_jobinfo_trigger_status ON xxl_job_info(trigger_status);
CREATE INDEX idx_jobinfo_jobgroup       ON xxl_job_info(job_group);

CREATE TABLE xxl_job_log (
    id                        BIGSERIAL    NOT NULL,
    job_group                 INT          NOT NULL,
    job_id                    INT          NOT NULL,
    executor_address          VARCHAR(255) DEFAULT NULL,
    executor_handler          VARCHAR(255) DEFAULT NULL,
    executor_param            VARCHAR(512) DEFAULT NULL,
    executor_sharding_param   VARCHAR(20)  DEFAULT NULL,
    executor_fail_retry_count INT          NOT NULL DEFAULT '0',
    trigger_time              TIMESTAMP    DEFAULT NULL,
    trigger_code              INT          NOT NULL,
    trigger_msg               TEXT,
    handle_time               TIMESTAMP    DEFAULT NULL,
    handle_code               INT          NOT NULL,
    handle_msg                TEXT,
    alarm_status              SMALLINT     NOT NULL DEFAULT '0',
    PRIMARY KEY (id)
);
CREATE INDEX idx_joblog_jobid ON xxl_job_log(job_id);
CREATE INDEX idx_joblog_trigger_time ON xxl_job_log(trigger_time);

CREATE TABLE xxl_job_log_report (
    id            BIGSERIAL   NOT NULL,
    trigger_day   TIMESTAMP   DEFAULT NULL,
    running_count INT         NOT NULL DEFAULT '0',
    suc_count     INT         NOT NULL DEFAULT '0',
    fail_count    INT         NOT NULL DEFAULT '0',
    update_time   TIMESTAMP   DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX i_trigger_day ON xxl_job_log_report(trigger_day);

CREATE TABLE xxl_job_logglue (
    id          BIGSERIAL    NOT NULL,
    job_id      INT          NOT NULL,
    glue_type   VARCHAR(50)  DEFAULT NULL,
    glue_source TEXT,
    glue_remark VARCHAR(128) NOT NULL,
    add_time    TIMESTAMP    DEFAULT NULL,
    update_time TIMESTAMP    DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_logglue_job_id ON xxl_job_logglue(job_id);

CREATE TABLE xxl_job_registry (
    id             BIGSERIAL    NOT NULL,
    registry_group VARCHAR(50)  NOT NULL,
    registry_key   VARCHAR(255) NOT NULL,
    registry_value VARCHAR(255) NOT NULL,
    update_time    TIMESTAMP    DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_registry_group_key ON xxl_job_registry(registry_group, registry_key);

CREATE TABLE xxl_job_group (
    id           BIGSERIAL    NOT NULL,
    app_name     VARCHAR(64)  NOT NULL,
    title        VARCHAR(12)  NOT NULL,
    address_type SMALLINT     NOT NULL DEFAULT '0',
    address_list TEXT,
    update_time  TIMESTAMP    DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE xxl_job_user (
    id         BIGSERIAL    NOT NULL,
    username   VARCHAR(50)  NOT NULL,
    password   VARCHAR(50)  NOT NULL,
    role       SMALLINT     NOT NULL,
    permission VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX i_username ON xxl_job_user(username);

CREATE TABLE xxl_job_lock (
    lock_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (lock_name)
);

INSERT INTO xxl_job_lock (lock_name) VALUES ('schedule_lock');

-- 默认账号: admin / 123456
INSERT INTO xxl_job_user (username, password, role, permission) VALUES ('admin', 'e10adc3949ba59abbe56e057f20f883e', 1, NULL);

INSERT INTO xxl_job_info (job_group, job_desc, add_time, update_time, author, schedule_type, schedule_conf, misfire_strategy, executor_route_strategy, executor_handler, executor_param, executor_block_strategy, executor_timeout, executor_fail_retry_count, glue_type, glue_remark, glue_updatetime, trigger_status, trigger_last_time, trigger_next_time) VALUES (1, '示例任务', NOW(), NOW(), 'admin', 'CRON', '0 0 0 * * ?', 'DO_NOTHING', 'FIRST', 'demoJobHandler', '', 'SERIAL_EXECUTION', 0, 0, 'BEAN', 'GLUE代码初始化', NOW(), 0, 0, 0);
