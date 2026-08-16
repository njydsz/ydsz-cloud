-- ============================================================
-- V3: 分享链接增强（访问日志 + 定向分享 + 到期提醒）
-- ============================================================

-- 1. 分享访问日志表
CREATE TABLE IF NOT EXISTS nw_share_access_log (
    id              VARCHAR(64) PRIMARY KEY,
    share_id        VARCHAR(64) NOT NULL,
    share_code      VARCHAR(64) NOT NULL,
    file_node_id    VARCHAR(64) NOT NULL,
    visitor_id      VARCHAR(64),
    visitor_name    VARCHAR(100),
    visitor_ip      VARCHAR(64),
    user_agent      VARCHAR(500),
    access_type     VARCHAR(20) NOT NULL DEFAULT 'VIEW',
    access_status   VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    fail_reason     VARCHAR(200),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted         SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE nw_share_access_log IS '分享链接访问日志';
COMMENT ON COLUMN nw_share_access_log.share_id IS '分享链接 ID';
COMMENT ON COLUMN nw_share_access_log.share_code IS '分享码';
COMMENT ON COLUMN nw_share_access_log.visitor_id IS '访问者用户 ID（匿名为空）';
COMMENT ON COLUMN nw_share_access_log.visitor_ip IS '访问者 IP 地址';
COMMENT ON COLUMN nw_share_access_log.user_agent IS '访问者 User-Agent';
COMMENT ON COLUMN nw_share_access_log.access_type IS '访问类型：VIEW/DOWNLOAD/EDIT';
COMMENT ON COLUMN nw_share_access_log.access_status IS '访问状态：SUCCESS/FAIL';
COMMENT ON COLUMN nw_share_access_log.fail_reason IS '失败原因';

-- 访问日志复合索引
CREATE INDEX IF NOT EXISTS idx_nw_share_access_log_share_id
    ON nw_share_access_log (share_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_nw_share_access_log_created
    ON nw_share_access_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_nw_share_access_log_visitor
    ON nw_share_access_log (visitor_id, created_at DESC);

-- 2. 分享目标用户表（定向分享）
CREATE TABLE IF NOT EXISTS nw_share_recipient (
    id              VARCHAR(64) PRIMARY KEY,
    share_id        VARCHAR(64) NOT NULL,
    recipient_type  VARCHAR(20) NOT NULL DEFAULT 'USER',
    recipient_id    VARCHAR(64) NOT NULL,
    recipient_name  VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    viewed_at       TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted         SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE nw_share_recipient IS '分享目标用户（定向分享）';
COMMENT ON COLUMN nw_share_recipient.share_id IS '分享链接 ID';
COMMENT ON COLUMN nw_share_recipient.recipient_type IS '接收者类型：USER/DEPT/ROLE';
COMMENT ON COLUMN nw_share_recipient.recipient_id IS '接收者 ID';
COMMENT ON COLUMN nw_share_recipient.status IS '状态：ACTIVE/VIEWED/REVOKED';

-- 目标用户复合索引
CREATE INDEX IF NOT EXISTS idx_nw_share_recipient_share
    ON nw_share_recipient (share_id, deleted);
CREATE INDEX IF NOT EXISTS idx_nw_share_recipient_user
    ON nw_share_recipient (recipient_id, status, deleted);

-- 3. 分享链接表新增字段
ALTER TABLE nw_share_link ADD COLUMN IF NOT EXISTS share_target_type VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE nw_share_link ADD COLUMN IF NOT EXISTS reminder_sent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE nw_share_link ADD COLUMN IF NOT EXISTS title VARCHAR(200);

COMMENT ON COLUMN nw_share_link.share_target_type IS '分享目标类型：PUBLIC/USER/DEPT';
COMMENT ON COLUMN nw_share_link.reminder_sent IS '到期提醒是否已发送';
COMMENT ON COLUMN nw_share_link.title IS '分享标题（可选）';

-- 分享链接查询优化索引
CREATE INDEX IF NOT EXISTS idx_nw_share_link_expire_reminder
    ON nw_share_link (status, expire_time, reminder_sent)
    WHERE status = 'active' AND deleted = 0;
