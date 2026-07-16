-- =====================================================
-- PMIS TCC 事务日志表 DDL
-- 模块: ydsz-common-seata
-- 数据库: PostgreSQL (兼容 MySQL 8.x)
-- =====================================================

-- TCC 事务日志表
-- 用于持久化 TCC 分支事务状态，解决空回滚/悬挂/幂等三大经典问题
CREATE TABLE IF NOT EXISTS tcc_transaction_log (
    id                  BIGSERIAL       NOT NULL PRIMARY KEY,
    xid                 VARCHAR(128)   NOT NULL,
    branch_id           VARCHAR(128)   NOT NULL,
    transaction_name    VARCHAR(256)   NOT NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'INIT',
    context_snapshot    TEXT,
    try_started_at      TIMESTAMP,
    try_completed_at    TIMESTAMP,
    finished_at         TIMESTAMP,
    retry_count         INT            NOT NULL DEFAULT 0,
    last_error          TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tcc_log UNIQUE (xid, branch_id)
);

COMMENT ON TABLE  tcc_transaction_log       IS 'TCC 分布式事务分支事务日志表（空回滚/悬挂/幂等保护）';
COMMENT ON COLUMN tcc_transaction_log.xid    IS '全局事务 ID（跨服务唯一标识）';
COMMENT ON COLUMN tcc_transaction_log.branch_id IS '分支事务 ID';
COMMENT ON COLUMN tcc_transaction_log.transaction_name IS '事务名称（用于日志和监控）';
COMMENT ON COLUMN tcc_transaction_log.status IS '分支状态：INIT/TRYING/TRIED/CONFIRMING/CONFIRMED/CANCELLING/CANCELLED';
COMMENT ON COLUMN tcc_transaction_log.context_snapshot IS '业务上下文快照（JSON，用于 Confirm/Cancel 恢复）';
COMMENT ON COLUMN tcc_transaction_log.try_started_at IS 'Try 阶段开始时间';
COMMENT ON COLUMN tcc_transaction_log.try_completed_at IS 'Try 阶段完成时间';
COMMENT ON COLUMN tcc_transaction_log.finished_at IS 'Confirm/Cancel 完成时间';
COMMENT ON COLUMN tcc_transaction_log.retry_count IS 'Confirm/Cancel 重试次数';
COMMENT ON COLUMN tcc_transaction_log.last_error IS '最近一次错误信息';

CREATE INDEX IF NOT EXISTS idx_tcc_log_status ON tcc_transaction_log(status);
CREATE INDEX IF NOT EXISTS idx_tcc_log_try_completed ON tcc_transaction_log(try_completed_at)
    WHERE status = 'TRIED';

ANALYZE tcc_transaction_log;
