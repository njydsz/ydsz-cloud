-- =============================================================================
--  Seata AT 模式 undo_log 表
--  -----------------------------------------------------------------------
--  每个参与 Seata AT 分布式事务的数据库都需要创建此表。
--  Seata TC 在分支事务提交时自动写入 undo_log，回滚时读取并执行反向 SQL。
--
--  使用方式：
--    1. 确保每个业务数据库都执行此 DDL
--    2. 启动 Seata TC Server（端口 8091）
--    3. 在 application.yml 中设置 seata.enabled=true
--    4. 在跨服务写操作入口方法上添加 @GlobalTransactional
-- =============================================================================

-- PostgreSQL 语法
CREATE TABLE IF NOT EXISTS undo_log (
    id          SERIAL        NOT NULL,
    branch_id   BIGINT        NOT NULL,
    xid         VARCHAR(128)  NOT NULL,
    context     VARCHAR(128)  NOT NULL,
    rollback_info BYTEA       NOT NULL,
    log_status  INT           NOT NULL,
    log_created TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    log_modified TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ext         VARCHAR(100),
    CONSTRAINT pk_undo_log PRIMARY KEY (id),
    CONSTRAINT ux_undo_log UNIQUE (xid, branch_id)
);

CREATE INDEX IF NOT EXISTS idx_undo_log_created ON undo_log (log_created);

COMMENT ON TABLE undo_log IS 'Seata AT 模式 undo_log 表（分支事务回滚日志）';
COMMENT ON COLUMN undo_log.branch_id IS '分支事务 ID';
COMMENT ON COLUMN undo_log.xid IS '全局事务 ID';
COMMENT ON COLUMN undo_log.context IS '序列化上下文（如 pha 库类型）';
COMMENT ON COLUMN undo_log.rollback_info IS '回滚信息（前后镜像 JSON）';
COMMENT ON COLUMN undo_log.log_status IS '状态（0-正常, 1-全局已完成）';
