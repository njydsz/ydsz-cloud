-- =============================================================
-- V1.0.0_058__init_pmis_flow_third_party.sql
-- 三方审批账号映射表 + 回调日志表
--
-- P0-2: 三方审批 SDK（钉钉/飞书/企微）回调接入
--   1. pmis_flow_third_party_account — 系统用户与三方平台账号的映射关系，
--      并缓存 access_token / refresh_token（加密存储），供回调时反查系统用户。
--   2. pmis_flow_third_party_log — 三方审批回调原始数据落库，便于重放/排障/对账。
--
-- 兼容性：
--   - 全部使用 IF NOT EXISTS，可重复执行
--   - 审计字段与 BaseDO 对齐（created_by/created_at/updated_by/updated_at/deleted）
--   - tenant_id 默认值 1，单租户部署不影响数据
-- =============================================================

-- -------------------------------------------
-- 1. 三方审批账号映射表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_third_party_account (
    id                 BIGSERIAL       PRIMARY KEY,
    user_id            BIGINT          NOT NULL,
    platform           VARCHAR(20)     NOT NULL,
    open_id            VARCHAR(128),
    union_id           VARCHAR(128),
    corp_id            VARCHAR(128),
    agent_id           VARCHAR(128),
    access_token       VARCHAR(512),
    refresh_token      VARCHAR(512),
    token_expire_at    TIMESTAMP,
    status             VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    tenant_id          BIGINT          NOT NULL DEFAULT 1,
    created_by         BIGINT,
    created_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT,
    updated_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT        NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_flow_third_party_account IS 'P0-2: 三方审批账号映射表（钉钉/飞书/企微）';
COMMENT ON COLUMN pmis_flow_third_party_account.user_id IS '系统用户 ID';
COMMENT ON COLUMN pmis_flow_third_party_account.platform IS '平台: DINGTALK/FEISHU/WECOM';
COMMENT ON COLUMN pmis_flow_third_party_account.open_id IS '三方 openId';
COMMENT ON COLUMN pmis_flow_third_party_account.union_id IS '三方 unionId';
COMMENT ON COLUMN pmis_flow_third_party_account.corp_id IS '企业 ID';
COMMENT ON COLUMN pmis_flow_third_party_account.agent_id IS '应用 ID';
COMMENT ON COLUMN pmis_flow_third_party_account.access_token IS '访问令牌(加密存储)';
COMMENT ON COLUMN pmis_flow_third_party_account.refresh_token IS '刷新令牌(加密存储)';
COMMENT ON COLUMN pmis_flow_third_party_account.token_expire_at IS '令牌过期时间';
COMMENT ON COLUMN pmis_flow_third_party_account.status IS '状态: ACTIVE/INACTIVE/REVOKED';
COMMENT ON COLUMN pmis_flow_third_party_account.deleted IS '逻辑删除标记 0=未删 1=已删';

-- 唯一约束：同一用户在同一平台仅绑定一个账号
CREATE UNIQUE INDEX IF NOT EXISTS uk_third_party_user_platform
    ON pmis_flow_third_party_account(user_id, platform)
    WHERE deleted = 0;

-- 唯一约束：同一平台同一 openId 仅映射一个系统用户
CREATE UNIQUE INDEX IF NOT EXISTS uk_third_party_platform_openid
    ON pmis_flow_third_party_account(platform, open_id)
    WHERE deleted = 0 AND open_id IS NOT NULL;

-- 索引：按平台 + unionId 反查
CREATE INDEX IF NOT EXISTS idx_third_party_union
    ON pmis_flow_third_party_account(platform, union_id);

-- -------------------------------------------
-- 2. 三方审批回调日志表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_third_party_log (
    id                  BIGSERIAL       PRIMARY KEY,
    platform            VARCHAR(20)     NOT NULL,
    event_type          VARCHAR(64)     NOT NULL,
    process_instance_id VARCHAR(128),
    business_type       VARCHAR(64),
    business_id         VARCHAR(128),
    callback_data       TEXT,
    handle_status       VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    error_msg           VARCHAR(512),
    tenant_id           BIGINT          NOT NULL DEFAULT 1,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE pmis_flow_third_party_log IS 'P0-2: 三方审批回调日志表';
COMMENT ON COLUMN pmis_flow_third_party_log.platform IS '平台: DINGTALK/FEISHU/WECOM';
COMMENT ON COLUMN pmis_flow_third_party_log.event_type IS '事件类型';
COMMENT ON COLUMN pmis_flow_third_party_log.process_instance_id IS '三方流程实例 ID';
COMMENT ON COLUMN pmis_flow_third_party_log.business_type IS '业务类型';
COMMENT ON COLUMN pmis_flow_third_party_log.business_id IS '业务 ID';
COMMENT ON COLUMN pmis_flow_third_party_log.callback_data IS '回调原始数据';
COMMENT ON COLUMN pmis_flow_third_party_log.handle_status IS '处理状态: PENDING/SUCCESS/FAIL';
COMMENT ON COLUMN pmis_flow_third_party_log.error_msg IS '处理失败原因';

-- 索引：按平台 + 时间查询回调日志
CREATE INDEX IF NOT EXISTS idx_third_party_log_platform
    ON pmis_flow_third_party_log(platform, created_at);

-- 索引：按处理状态扫描（PENDING 重试）
CREATE INDEX IF NOT EXISTS idx_third_party_log_status
    ON pmis_flow_third_party_log(handle_status, created_at);
