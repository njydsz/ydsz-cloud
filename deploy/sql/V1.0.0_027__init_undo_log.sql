-- Seata AT 模式 undo_log 表
-- 用于分布式事务回滚
CREATE TABLE IF NOT EXISTS undo_log (
    id            BIGSERIAL    PRIMARY KEY,
    branch_id     BIGINT       NOT NULL,
    xid           VARCHAR(100) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info BYTEA        NOT NULL,
    log_status    INT          NOT NULL,
    log_created   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    log_modified  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_undo_log UNIQUE (xid, branch_id)
);

COMMENT ON TABLE undo_log IS 'Seata AT 模式分布式事务回滚日志表';
COMMENT ON COLUMN undo_log.id IS '主键';
COMMENT ON COLUMN undo_log.branch_id IS '分支事务ID';
COMMENT ON COLUMN undo_log.xid IS '全局事务ID';
COMMENT ON COLUMN undo_log.context IS '事务上下文';
COMMENT ON COLUMN undo_log.rollback_info IS '回滚信息';
COMMENT ON COLUMN undo_log.log_status IS '日志状态';
COMMENT ON COLUMN undo_log.log_created IS '创建时间';
COMMENT ON COLUMN undo_log.log_modified IS '修改时间';
