-- ====================================================================
--  Seata AT 模式 undo_log 表
--  --------------------------------------------------------------------
--  说明：
--    1) AT 模式依赖此表保存 before/after 镜像，用于分支事务回滚
--    2) 必须在每个业务库（pmis / pmis_bill / pmis_archive ...）都建
--    3) 配套 Nacos 配置：data-id = seata-client.properties
--    4) 配套脚本：deploy/seata/verify-seata.sh 会自动检查本表存在
--  --------------------------------------------------------------------
--  版本：V1.0.0_027
--  适用：PostgreSQL 16+
-- ====================================================================

-- ---------- 表结构 ----------
-- id            主键自增
-- branch_id     分支事务 ID（Seata 生成）
-- xid           全局事务 ID（跨服务唯一）
-- context       事务上下文（序列化信息）
-- rollback_info 回滚信息（before/after 镜像 ZIP 压缩）
-- log_status    日志状态 0=正常 1=全局完成 2=全局回滚
-- log_created   创建时间
-- log_modified  最后修改时间
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

-- ---------- 字段注释 ----------
COMMENT ON TABLE  undo_log             IS 'Seata AT 模式分布式事务回滚日志表（每个业务库都需要）';
COMMENT ON COLUMN undo_log.id          IS '主键 ID';
COMMENT ON COLUMN undo_log.branch_id   IS '分支事务 ID（Seata 内部生成）';
COMMENT ON COLUMN undo_log.xid         IS '全局事务 ID（跨服务唯一标识）';
COMMENT ON COLUMN undo_log.context     IS '事务上下文（序列化信息，如应用名、分组等）';
COMMENT ON COLUMN undo_log.rollback_info IS '回滚信息（ZIP 压缩的 before/after 镜像，Base64 编码）';
COMMENT ON COLUMN undo_log.log_status  IS '日志状态：0=正常 1=全局完成 2=全局回滚';
COMMENT ON COLUMN undo_log.log_created IS '创建时间';
COMMENT ON COLUMN undo_log.log_modified IS '最后修改时间';

-- ---------- 性能索引 ----------
-- 建议添加以下索引（百万行级别可显著提升回滚扫描性能）
-- CREATE INDEX IF NOT EXISTS idx_undo_log_xid ON undo_log (xid);
-- CREATE INDEX IF NOT EXISTS idx_undo_log_status_modified ON undo_log (log_status, log_modified);
