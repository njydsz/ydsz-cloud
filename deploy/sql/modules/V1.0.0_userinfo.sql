-- ====================================================================
-- System.Collections.Hashtable[userinfo]
-- Module: userinfo
-- Version: V1.0.0
-- Target: PostgreSQL 18
-- Description: 鏈枃浠剁敱 deploy/sql/V1.0.0.sql 鎷嗗垎鐢熸垚
--   浠呬緵鍗曠嫭鍒濆鍖栧搴旀ā鍧楁椂浣跨敤; 瀹屾暣鍒濆鍖栬浣跨敤 V1.0.0.sql
-- ====================================================================

-- ============================ [014] init pmis admin full perm ============================

-- ====================================================================
-- 9. 初始化菜单权限 + 角色授权 (admin 拥有全部权限)
-- ====================================================================

-- 一. 初始化菜单权限
-- 拆成多步插入：先插入顶层节点（parent_id=0），再插入二级子菜单，
-- 最后插入三级按钮权限。每一步都通过 perm_code 关联父节点。
-- 关键：PostgreSQL 在单条 INSERT VALUES 中，所有子查询都在语句开始时求值，
--       看不到同语句中正在插入的行；因此必须分多语句执行。

-- 步骤 1：插入顶层节点
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    (0, 'dashboard',  '仪表盘',   'MENU', '/dashboard',  'dashboard/index', 'odometer',  1, 1, 'ENABLED', 0),
    (0, 'system',     '系统管理', 'MENU', '/system',     'Layout',          'setting',   2, 1, 'ENABLED', 0),
    (0, 'business',   '业务管理', 'MENU', '/business',   'Layout',          'briefcase', 3, 1, 'ENABLED', 0),
    (0, 'execution',  '项目执行', 'MENU', '/execution',  'Layout',          'cpu',       4, 1, 'ENABLED', 0),
    (0, 'finance',    '财务收支', 'MENU', '/finance',    'Layout',          'credit-card', 5, 1, 'ENABLED', 0),
    (0, 'report',     '经营报表', 'MENU', '/report',     'Layout',          'data-analysis', 6, 1, 'ENABLED', 0),
    (0, 'ai',         '智能助手', 'MENU', '/ai',         'Layout',          'magic-stick',  7, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2a：插入系统管理子菜单
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:user',     '用户管理',     'MENU', '/system/user',     'system/user/index',     'user',     1, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:role',     '角色管理',     'MENU', '/system/role',     'system/role/index',     'avatar',   2, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:menu',     '菜单管理',     'MENU', '/system/menu',     'system/menu/index',     'menu',     3, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:dept',     '部门管理',     'MENU', '/system/dept',     'system/dept/index',     'office-building', 4, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:dict',     '数据字典',     'MENU', '/system/dict',     'system/dict/index',     'collection', 5, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:job-level','职级管理',     'MENU', '/system/job-level','system/job-level/index', 'medal',    6, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:config',   '参数配置',     'MENU', '/system/config',   'system/config/index',   'tools',    7, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2b：插入业务根子菜单
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'business'), 'business:opportunity', '商机管理', 'MENU', '/business/opportunity', 'business/opportunity/index', 'lightbulb', 1, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'business'), 'business:initiation',  '立项管理', 'MENU', '/business/initiation',  'business/initiation/index',  'document', 2, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'business'), 'business:contract',     '合同管理', 'MENU', '/business/contract',     'business/contract/index',     'tickets', 3, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'business'), 'business:change',       '变更管理', 'MENU', '/business/change',       'business/change/index',       'refresh', 4, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2c：插入执行根子菜单
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:wbs',         'WBS 任务',  'MENU', '/execution/wbs',         'execution/wbs/index',         'list',     1, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:timesheet',   '工时管理',  'MENU', '/execution/timesheet',   'execution/timesheet/index',   'timer',    2, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:attendance',  '考勤管理',  'MENU', '/execution/attendance',  'execution/attendance/index',  'calendar', 3, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:cost',        '成本管理',  'MENU', '/execution/cost',        'execution/cost/index',        'money',    4, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:revenue',     '收入管理',  'MENU', '/execution/revenue',     'execution/revenue/index',     'wallet',   5, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:risk',        '风险登记',  'MENU', '/execution/risk',        'execution/risk/index',        'warning',  6, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:profit',      '利润分析',  'MENU', '/execution/profit',      'execution/profit/index',      'data-line',7, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:delivery',    '交付管理',  'MENU', '/execution/delivery',    'execution/delivery/index',    'box',      8, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2d：插入财务根子菜单
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'finance'), 'finance:invoice',  '发票管理', 'MENU', '/finance/invoice',  'finance/invoice/index',  'document-copy', 1, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'finance'), 'finance:payment',  '回款管理', 'MENU', '/finance/payment',  'finance/payment/index',  'bank-card',    2, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2e：插入报表根子菜单
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'report'), 'report:profit',    '项目利润',   'MENU', '/report/profit',    'report/profit/index',    'pie-chart',    1, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'report'), 'report:cost',      '成本明细',   'MENU', '/report/cost',      'report/cost/index',      'data-board',   2, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'report'), 'report:lifecycle', '生命周期台账', 'MENU', '/report/lifecycle', 'report/lifecycle/index', 'connection',   3, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2f：插入 AI 根子菜单
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'ai'), 'ai:agents',  'AI Agents', 'MENU', '/ai/agents', 'ai/agents/index', 'chat-dot-round', 1, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 3：插入按钮级权限（依赖步骤 2 的二级菜单）
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:user'), 'auth:user:create', '新增用户', 'BUTTON', null, null, null, 1, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:user'), 'auth:user:update', '编辑用户', 'BUTTON', null, null, null, 2, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:user'), 'auth:user:delete', '删除用户', 'BUTTON', null, null, null, 3, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:user'), 'auth:user:reset',  '重置密码', 'BUTTON', null, null, null, 4, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:role'), 'auth:role:create', '新增角色', 'BUTTON', null, null, null, 1, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:role'), 'auth:role:update', '编辑角色', 'BUTTON', null, null, null, 2, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:role'), 'auth:role:delete', '删除角色', 'BUTTON', null, null, null, 3, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:role'), 'auth:role:assign', '分配权限', 'BUTTON', null, null, null, 4, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:menu'), 'auth:perm:create', '新增菜单', 'BUTTON', null, null, null, 1, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:menu'), 'auth:perm:update', '编辑菜单', 'BUTTON', null, null, null, 2, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:menu'), 'auth:perm:delete', '删除菜单', 'BUTTON', null, null, null, 3, 0, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 二. SUPER_ADMIN 角色绑定所有权限
INSERT INTO pmis_role_permission (role_id, permission_id)
SELECT
    (SELECT id FROM pmis_role WHERE role_code = 'SUPER_ADMIN'),
    p.id
FROM pmis_permission p
WHERE p.deleted = 0
  AND p.status = 'ENABLED'
ON CONFLICT DO NOTHING;

-- 三. admin 用户绑定到 SUPER_ADMIN 角色
INSERT INTO pmis_user_role (user_id, role_id, created_by)
SELECT
    (SELECT id FROM pmis_user_account WHERE username = 'admin'),
    (SELECT id FROM pmis_role WHERE role_code = 'SUPER_ADMIN'),
    0
ON CONFLICT DO NOTHING;

-- --------------------------------------------------------------------

-- ============================ [016] init pmis security ============================

-- ============================================================
-- V1.0.0_016  权限安全体系  脚本
-- ============================================================
-- 说明：批次 13 权限安全体系
-- 1) 数据权限：pmis_user_account 增加 data_scope / custom_dept_ids 字段
-- 2) 登录审计：pmis_login_audit
-- 3) 双因素认证：pmis_user_2fa
-- 4) 数据导出审计：pmis_data_export_audit
-- 5) 敏感操作复核：pmis_sensitive_operation
-- 6) 会话管理：pmis_user_session
-- ============================================================

-- ----------------------------
-- 1) 增强用户账号表 (已优化内联至 V1.0.0_001 pmis_user_account 定义)
-- ----------------------------
-- data_scope / custom_dept_ids / mfa_enabled / mfa_type / last_pwd_change_at / pwd_change_count
-- 已在 V1.0.0_001 中以最终结构内联(含 CHECK 约束),此处不再重复 ADD COLUMN

-- ----------------------------
-- 2) 登录审计
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_login_audit (
    id              VARCHAR(20) PRIMARY KEY,
    username        VARCHAR(64)   NOT NULL,
    user_id         VARCHAR(20),
    login_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    login_ip        VARCHAR(64),
    user_agent      VARCHAR(512),
    status          VARCHAR(16)   NOT NULL,
    fail_reason     VARCHAR(64),
    mfa_used        BOOLEAN       NOT NULL DEFAULT FALSE,
    mfa_success     BOOLEAN,
    trace_id        VARCHAR(20),
    tenant_id       VARCHAR(20)        NOT NULL DEFAULT '1',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_login_audit_status    CHECK (status IN ('SUCCESS','FAIL','LOCKED','MFA_REQUIRED')),
    CONSTRAINT ck_login_audit_deleted   CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_login_audit IS '登录审计日志表: 等保 2.0 要求,登录成功/失败全留存,支持溯源审计';
COMMENT ON COLUMN pmis_login_audit.username IS '登录用户名: 失败时也可记录,便于排查撞库';
COMMENT ON COLUMN pmis_login_audit.user_id IS '登录用户 ID: 成功时记录,失败可为 NULL';
COMMENT ON COLUMN pmis_login_audit.login_at IS '登录时间';
COMMENT ON COLUMN pmis_login_audit.login_ip IS '登录 IP: 用于异常登录检测';
COMMENT ON COLUMN pmis_login_audit.user_agent IS '浏览器 UA: 用于设备指纹';
COMMENT ON COLUMN pmis_login_audit.status IS '状态: SUCCESS 成功 / FAIL 失败 / LOCKED 锁定 / MFA_REQUIRED 待 MFA';
COMMENT ON COLUMN pmis_login_audit.fail_reason IS '失败原因: 密码错误/账号锁定/MFA 失败等';
COMMENT ON COLUMN pmis_login_audit.mfa_used IS '是否使用 MFA: true=已启用并使用';
COMMENT ON COLUMN pmis_login_audit.mfa_success IS 'MFA 是否通过: NULL=未使用,true=通过,false=失败';
COMMENT ON COLUMN pmis_login_audit.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_login_audit.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_login_audit.deleted IS '逻辑删除: 0=未删除,1=已删除';
-- 复合/部分索引(替代零散的 idx_login_audit_*)
CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_user_at
    ON pmis_login_audit(tenant_id, username, login_at DESC)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_ip_at
    ON pmis_login_audit(tenant_id, login_ip, login_at DESC)
    WHERE deleted = 0 AND login_ip IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_status_at
    ON pmis_login_audit(tenant_id, status, login_at DESC)
    WHERE deleted = 0;

-- ----------------------------
-- 3) 双因素认证
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_user_2fa (
    id              VARCHAR(20) PRIMARY KEY,
    user_id         VARCHAR(20)        NOT NULL,
    mfa_type        VARCHAR(16)   NOT NULL DEFAULT 'TOTP',
    secret          VARCHAR(128)  NOT NULL,
    binding_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at    TIMESTAMPTZ,
    backup_codes    TEXT,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    tenant_id       VARCHAR(20)        NOT NULL DEFAULT '1',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_2fa_uid_type     UNIQUE (user_id, mfa_type, deleted),
    CONSTRAINT ck_user_2fa_type         CHECK (mfa_type IN ('TOTP','SMS','EMAIL')),
    CONSTRAINT ck_user_2fa_deleted      CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_user_2fa IS '用户双因素认证表: 基于 TOTP（Time-based OTP）的双因素认证,使用 constant-time 比对防时序攻击';
COMMENT ON COLUMN pmis_user_2fa.user_id IS '用户 ID';
COMMENT ON COLUMN pmis_user_2fa.mfa_type IS 'MFA 类型: TOTP 时间型 / SMS 短信 / EMAIL 邮件';
COMMENT ON COLUMN pmis_user_2fa.secret IS 'TOTP 密钥: Base32 编码,扫描二维码';
COMMENT ON COLUMN pmis_user_2fa.binding_at IS '绑定时间';
COMMENT ON COLUMN pmis_user_2fa.last_used_at IS '最近使用时间';
COMMENT ON COLUMN pmis_user_2fa.backup_codes IS '备份码（密文）: 一次性,小写 hex 存储,已使用标记为 _used_<timestamp>';
COMMENT ON COLUMN pmis_user_2fa.enabled IS '是否启用: true=启用';
COMMENT ON COLUMN pmis_user_2fa.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_user_2fa.deleted IS '逻辑删除: 0=未删除,1=已删除';
CREATE INDEX IF NOT EXISTS idx_user_2fa_tenant_user
    ON pmis_user_2fa(tenant_id, user_id)
    WHERE deleted = 0;

-- ----------------------------
-- 4) 数据导出审计
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_data_export_audit (
    id              VARCHAR(20) PRIMARY KEY,
    user_id         VARCHAR(20)        NOT NULL,
    username        VARCHAR(64)   NOT NULL,
    export_module   VARCHAR(64)   NOT NULL,
    export_action   VARCHAR(64)   NOT NULL,
    biz_type        VARCHAR(32),
    row_count       INT           NOT NULL DEFAULT 0,
    file_name       VARCHAR(256),
    file_size       BIGINT,
    export_format   VARCHAR(16),
    query_summary   TEXT,
    trace_id        VARCHAR(20),
    client_ip       VARCHAR(64),
    tenant_id       VARCHAR(20)        NOT NULL DEFAULT '1',
    exported_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_dea_export_action      CHECK (export_action IN ('EXPORT','PRINT','DOWNLOAD')),
    CONSTRAINT ck_dea_export_format      CHECK (export_format IS NULL OR export_format IN ('XLSX','CSV','PDF','JSON','XML')),
    CONSTRAINT ck_dea_row_count_nonneg   CHECK (row_count >= 0),
    CONSTRAINT ck_dea_file_size_nonneg   CHECK (file_size IS NULL OR file_size >= 0),
    CONSTRAINT ck_dea_deleted            CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_data_export_audit IS '数据导出审计表: 合同/财务/薪酬等敏感数据导出全留存,@DataExportAudit 自动捕获';
COMMENT ON COLUMN pmis_data_export_audit.user_id IS '导出用户 ID';
COMMENT ON COLUMN pmis_data_export_audit.username IS '导出用户姓名（冗余）';
COMMENT ON COLUMN pmis_data_export_audit.export_module IS '导出模块: PROJECT/EXECUTION/FINANCE 等';
COMMENT ON COLUMN pmis_data_export_audit.export_action IS '导出动作: EXPORT 导出 / PRINT 打印 / DOWNLOAD 下载';
COMMENT ON COLUMN pmis_data_export_audit.biz_type IS '业务类型';
COMMENT ON COLUMN pmis_data_export_audit.row_count IS '导出行数: 自动检测 Collection/Number,作为审计基数';
COMMENT ON COLUMN pmis_data_export_audit.file_name IS '导出文件名';
COMMENT ON COLUMN pmis_data_export_audit.file_size IS '文件大小(字节)';
COMMENT ON COLUMN pmis_data_export_audit.export_format IS '导出格式: XLSX/CSV/PDF';
COMMENT ON COLUMN pmis_data_export_audit.query_summary IS '查询条件摘要: 用于审计导出范围';
COMMENT ON COLUMN pmis_data_export_audit.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_data_export_audit.client_ip IS '客户端 IP';
COMMENT ON COLUMN pmis_data_export_audit.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_data_export_audit.exported_at IS '导出时间';
COMMENT ON COLUMN pmis_data_export_audit.deleted IS '逻辑删除: 0=未删除,1=已删除';
-- 复合/部分索引(替代零散的 idx_export_audit_*)
CREATE INDEX IF NOT EXISTS idx_dea_tenant_user_at
    ON pmis_data_export_audit(tenant_id, user_id, exported_at DESC)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_dea_tenant_module_at
    ON pmis_data_export_audit(tenant_id, export_module, exported_at DESC)
    WHERE deleted = 0;

-- ----------------------------
-- 5) 敏感操作二次确认
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_sensitive_operation (
    id              VARCHAR(20) PRIMARY KEY,
    user_id         VARCHAR(20)        NOT NULL,
    username        VARCHAR(64)   NOT NULL,
    operation_code  VARCHAR(64)   NOT NULL,
    operation_name  VARCHAR(128)  NOT NULL,
    biz_type        VARCHAR(32),
    biz_id          VARCHAR(20),
    re_auth_method  VARCHAR(16)   NOT NULL,
    re_auth_token   VARCHAR(256),
    verified_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at       TIMESTAMPTZ   NOT NULL,
    client_ip       VARCHAR(64),
    trace_id        VARCHAR(20),
    tenant_id       VARCHAR(20)        NOT NULL DEFAULT '1',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_sensitive_op_method    CHECK (re_auth_method IN ('PASSWORD','MFA','SMS')),
    CONSTRAINT ck_sensitive_op_expire    CHECK (expire_at >= verified_at),
    CONSTRAINT ck_sensitive_op_deleted   CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_sensitive_operation IS '敏感操作二次确认记录表: @RequireReAuth 注解触发的二次认证,token 一次性消费,防重放';
COMMENT ON COLUMN pmis_sensitive_operation.user_id IS '操作用户 ID';
COMMENT ON COLUMN pmis_sensitive_operation.username IS '操作用户姓名（冗余）';
COMMENT ON COLUMN pmis_sensitive_operation.operation_code IS '操作编码: 例如 USER_DELETE / CONTRACT_REVERSE';
COMMENT ON COLUMN pmis_sensitive_operation.operation_name IS '操作名称';
COMMENT ON COLUMN pmis_sensitive_operation.biz_type IS '业务类型';
COMMENT ON COLUMN pmis_sensitive_operation.biz_id IS '业务对象 ID';
COMMENT ON COLUMN pmis_sensitive_operation.re_auth_method IS '二次认证方式: PASSWORD 密码 / MFA / SMS';
COMMENT ON COLUMN pmis_sensitive_operation.re_auth_token IS '二次认证 Token: Redis Key 一次性消费';
COMMENT ON COLUMN pmis_sensitive_operation.verified_at IS '验证时间';
COMMENT ON COLUMN pmis_sensitive_operation.expire_at IS 'Token 过期时间';
COMMENT ON COLUMN pmis_sensitive_operation.client_ip IS '客户端 IP';
COMMENT ON COLUMN pmis_sensitive_operation.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_sensitive_operation.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_sensitive_operation.deleted IS '逻辑删除: 0=未删除,1=已删除';
-- 复合/部分索引(替代零散的 idx_sensitive_op_*)
CREATE INDEX IF NOT EXISTS idx_sensitive_op_tenant_user_at
    ON pmis_sensitive_operation(tenant_id, user_id, verified_at DESC)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_sensitive_op_tenant_code_at
    ON pmis_sensitive_operation(tenant_id, operation_code, verified_at DESC)
    WHERE deleted = 0;

-- ----------------------------
-- 6) 用户会话（单点登录/强制下线）
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_user_session (
    id              VARCHAR(20) PRIMARY KEY,
    user_id         VARCHAR(20)        NOT NULL,
    session_id      VARCHAR(64)   NOT NULL,
    token_jti       VARCHAR(64),
    login_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at  TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at       TIMESTAMPTZ   NOT NULL,
    client_ip       VARCHAR(64),
    user_agent      VARCHAR(512),
    device_type     VARCHAR(32),
    status          VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    logout_at       TIMESTAMPTZ,
    logout_reason   VARCHAR(64),
    trace_id        VARCHAR(20),
    tenant_id       VARCHAR(20)        NOT NULL DEFAULT '1',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_session_id        UNIQUE (session_id),
    CONSTRAINT ck_user_session_status    CHECK (status IN ('ACTIVE','EXPIRED','KICKED','LOGOUT')),
    CONSTRAINT ck_user_session_device    CHECK (device_type IS NULL OR device_type IN ('WEB','IOS','ANDROID','DESKTOP','API')),
    CONSTRAINT ck_user_session_expire    CHECK (expire_at > login_at),
    CONSTRAINT ck_user_session_deleted   CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_user_session IS '用户活跃会话表: 单点登录/强制下线管理,SessionService 维护生命周期';
COMMENT ON COLUMN pmis_user_session.user_id IS '用户 ID';
COMMENT ON COLUMN pmis_user_session.session_id IS '会话 ID: 唯一';
COMMENT ON COLUMN pmis_user_session.token_jti IS 'JWT ID: 用于 token 失效';
COMMENT ON COLUMN pmis_user_session.login_at IS '登录时间';
COMMENT ON COLUMN pmis_user_session.last_active_at IS '最近活跃时间';
COMMENT ON COLUMN pmis_user_session.expire_at IS '过期时间';
COMMENT ON COLUMN pmis_user_session.client_ip IS '客户端 IP';
COMMENT ON COLUMN pmis_user_session.user_agent IS '浏览器 UA';
COMMENT ON COLUMN pmis_user_session.device_type IS '设备类型: WEB / IOS / ANDROID / DESKTOP';
COMMENT ON COLUMN pmis_user_session.status IS '会话状态: ACTIVE 活跃 / EXPIRED 过期 / KICKED 踢出 / LOGOUT 主动登出';
COMMENT ON COLUMN pmis_user_session.logout_at IS '登出时间';
COMMENT ON COLUMN pmis_user_session.logout_reason IS '登出原因: USER_LOGOUT / ADMIN_KICK / EXPIRED';
COMMENT ON COLUMN pmis_user_session.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_user_session.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_user_session.deleted IS '逻辑删除: 0=未删除,1=已删除';
-- 复合/部分索引(替代零散的 idx_user_session_*)
CREATE INDEX IF NOT EXISTS idx_user_session_tenant_user_status
    ON pmis_user_session(tenant_id, user_id, status)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_user_session_tenant_expire
    ON pmis_user_session(tenant_id, expire_at)
    WHERE deleted = 0 AND status = 'ACTIVE';

-- --------------------------------------------------------------------

-- ============================ [039] init pmis attendance schema ============================

-- =====================================================
-- PMIS 批次12 DDL：考勤管理(出勤/加班/请假)
-- 版本: V1.0.0_015
-- 描述: 出勤(pmis_attendance) + 加班(pmis_overtime) + 请假(pmis_leave)
-- =====================================================

-- =====================================================
-- 1. 出勤记录表 pmis_attendance
-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_attendance (
    id                  VARCHAR(20) PRIMARY KEY,
    employee_id         VARCHAR(20)       NOT NULL,
    employee_name       VARCHAR(64),
    attendance_date     DATE         NOT NULL,
    check_in_time       TIMESTAMPTZ,
    check_out_time      TIMESTAMPTZ,
    work_hours          NUMERIC(5,2) NOT NULL DEFAULT 0.0,
    overtime_hours      NUMERIC(5,2) NOT NULL DEFAULT 0.0,
    status              VARCHAR(32)  NOT NULL DEFAULT 'NORMAL',  -- NORMAL/LATE/EARLY/ABSENT/LEAVE/OVERTIME
    work_type           VARCHAR(16)  NOT NULL DEFAULT 'WORKDAY',  -- WORKDAY/WEEKEND/HOLIDAY
    remark              TEXT,
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_pa_emp_date         UNIQUE (employee_id, attendance_date, deleted),
    CONSTRAINT ck_pa_status           CHECK (status IN ('NORMAL','LATE','EARLY','ABSENT','LEAVE','OVERTIME')),
    CONSTRAINT ck_pa_work_type        CHECK (work_type IN ('WORKDAY','WEEKEND','HOLIDAY')),
    CONSTRAINT ck_pa_work_hours       CHECK (work_hours >= 0),
    CONSTRAINT ck_pa_overtime_hours   CHECK (overtime_hours >= 0),
    CONSTRAINT ck_pa_check_range      CHECK (check_in_time IS NULL OR check_out_time IS NULL OR check_out_time >= check_in_time),
    CONSTRAINT ck_pa_deleted          CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_attendance IS '员工出勤记录表: 每日打卡 + 工作时长统计,支撑项目工时分配';
COMMENT ON COLUMN pmis_attendance.employee_id IS '员工 ID';
COMMENT ON COLUMN pmis_attendance.employee_name IS '员工姓名（冗余）';
COMMENT ON COLUMN pmis_attendance.attendance_date IS '出勤日期';
COMMENT ON COLUMN pmis_attendance.check_in_time IS '上班打卡时间';
COMMENT ON COLUMN pmis_attendance.check_out_time IS '下班打卡时间';
COMMENT ON COLUMN pmis_attendance.work_hours IS '工作时长(小时)';
COMMENT ON COLUMN pmis_attendance.overtime_hours IS '加班时长(小时)';
COMMENT ON COLUMN pmis_attendance.status IS '出勤状态: NORMAL 正常 / LATE 迟到 / EARLY 早退 / ABSENT 缺勤 / LEAVE 请假 / OVERTIME 加班';
COMMENT ON COLUMN pmis_attendance.work_type IS '日期类型: WORKDAY 工作日 / WEEKEND 周末 / HOLIDAY 节假日';
COMMENT ON COLUMN pmis_attendance.remark IS '备注';
COMMENT ON COLUMN pmis_attendance.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_attendance.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_attendance.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pa_tenant_emp_date
    ON pmis_attendance(tenant_id, employee_id, attendance_date DESC)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pa_tenant_date
    ON pmis_attendance(tenant_id, attendance_date DESC);
CREATE INDEX IF NOT EXISTS idx_pa_tenant_status
    ON pmis_attendance(tenant_id, status)
    WHERE deleted = 0;

-- =====================================================
-- 2. 加班申请表 pmis_overtime
-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_overtime (
    id                  VARCHAR(20) PRIMARY KEY,
    overtime_code       VARCHAR(64)  NOT NULL,
    employee_id         VARCHAR(20)       NOT NULL,
    employee_name       VARCHAR(64),
    overtime_date       DATE         NOT NULL,
    start_time          TIMESTAMPTZ  NOT NULL,
    end_time            TIMESTAMPTZ  NOT NULL,
    overtime_hours      NUMERIC(5,2) NOT NULL,
    overtime_type       VARCHAR(32)  NOT NULL,                   -- WORKDAY/WEEKEND/HOLIDAY
    pay_rate            NUMERIC(5,2) NOT NULL DEFAULT 1.5,       -- 1.5/2.0/3.0 倍
    reason              TEXT,
    approval_id         VARCHAR(20),
    approval_status     VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELLED
    approver_id         VARCHAR(20),
    approver_name       VARCHAR(64),
    approval_time       TIMESTAMPTZ,
    approval_remark     TEXT,
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_pot_code                UNIQUE (overtime_code, deleted),
    CONSTRAINT ck_pot_overtime_type       CHECK (overtime_type IN ('WORKDAY','WEEKEND','HOLIDAY')),
    CONSTRAINT ck_pot_approval_status     CHECK (approval_status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_pot_hours_positive      CHECK (overtime_hours > 0),
    CONSTRAINT ck_pot_pay_rate            CHECK (pay_rate IN (1.5, 2.0, 3.0)),
    CONSTRAINT ck_pot_time_range          CHECK (end_time > start_time),
    CONSTRAINT ck_pot_deleted             CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_overtime IS '加班申请表: WORKDAY 1.5x / WEEKEND 2.0x / HOLIDAY 3.0x 法定倍数';
COMMENT ON COLUMN pmis_overtime.overtime_code IS '加班单号: 业务唯一,如 OT-2026-001';
COMMENT ON COLUMN pmis_overtime.employee_id IS '员工 ID';
COMMENT ON COLUMN pmis_overtime.employee_name IS '员工姓名（冗余）';
COMMENT ON COLUMN pmis_overtime.overtime_date IS '加班日期';
COMMENT ON COLUMN pmis_overtime.start_time IS '加班开始时间';
COMMENT ON COLUMN pmis_overtime.end_time IS '加班结束时间';
COMMENT ON COLUMN pmis_overtime.overtime_hours IS '加班时长(小时)';
COMMENT ON COLUMN pmis_overtime.overtime_type IS '加班类型: WORKDAY 工作日 / WEEKEND 周末 / HOLIDAY 节假日';
COMMENT ON COLUMN pmis_overtime.pay_rate IS '加班倍数: 1.5/2.0/3.0 倍,用于薪资计算';
COMMENT ON COLUMN pmis_overtime.reason IS '加班原因';
COMMENT ON COLUMN pmis_overtime.approval_id IS '审批流实例 ID: 关联工作流引擎';
COMMENT ON COLUMN pmis_overtime.approval_status IS '审批状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVED 已批准 / REJECTED 已驳回 / CANCELLED 已取消';
COMMENT ON COLUMN pmis_overtime.approver_id IS '审批人 ID';
COMMENT ON COLUMN pmis_overtime.approver_name IS '审批人姓名（冗余）';
COMMENT ON COLUMN pmis_overtime.approval_time IS '审批时间';
COMMENT ON COLUMN pmis_overtime.approval_remark IS '审批意见';
COMMENT ON COLUMN pmis_overtime.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_overtime.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_overtime.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pot_tenant_emp_date
    ON pmis_overtime(tenant_id, employee_id, overtime_date DESC)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pot_tenant_date
    ON pmis_overtime(tenant_id, overtime_date DESC);
CREATE INDEX IF NOT EXISTS idx_pot_tenant_status
    ON pmis_overtime(tenant_id, approval_status)
    WHERE deleted = 0;

-- =====================================================
-- 3. 请假申请表 pmis_leave
-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_leave (
    id                  VARCHAR(20) PRIMARY KEY,
    leave_code          VARCHAR(64)  NOT NULL,
    employee_id         VARCHAR(20)       NOT NULL,
    employee_name       VARCHAR(64),
    leave_type          VARCHAR(32)  NOT NULL,                   -- ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY/BEREAVEMENT/OTHER
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    leave_days          NUMERIC(5,2) NOT NULL,
    reason              TEXT,
    attachment_url      VARCHAR(512),
    approval_id         VARCHAR(20),
    approval_status     VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELLED
    approver_id         VARCHAR(20),
    approver_name       VARCHAR(64),
    approval_time       TIMESTAMPTZ,
    approval_remark     TEXT,
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_pl_code                UNIQUE (leave_code, deleted),
    CONSTRAINT ck_pl_leave_type          CHECK (leave_type IN ('ANNUAL','SICK','PERSONAL','MARRIAGE','MATERNITY','BEREAVEMENT','OTHER')),
    CONSTRAINT ck_pl_approval_status     CHECK (approval_status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_pl_days_positive       CHECK (leave_days > 0),
    CONSTRAINT ck_pl_date_range          CHECK (end_date >= start_date),
    CONSTRAINT ck_pl_deleted             CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_leave IS '请假申请表: 7 种假期类型,自动算 leave_days';
COMMENT ON COLUMN pmis_leave.leave_code IS '请假单号: 业务唯一,如 LV-2026-001';
COMMENT ON COLUMN pmis_leave.employee_id IS '员工 ID';
COMMENT ON COLUMN pmis_leave.employee_name IS '员工姓名（冗余）';
COMMENT ON COLUMN pmis_leave.leave_type IS '假期类型: ANNUAL 年假 / SICK 病假 / PERSONAL 事假 / MARRIAGE 婚假 / MATERNITY 产假 / BEREAVEMENT 丧假 / OTHER 其他';
COMMENT ON COLUMN pmis_leave.start_date IS '请假开始日期';
COMMENT ON COLUMN pmis_leave.end_date IS '请假结束日期';
COMMENT ON COLUMN pmis_leave.leave_days IS '请假天数(天)';
COMMENT ON COLUMN pmis_leave.reason IS '请假原因';
COMMENT ON COLUMN pmis_leave.attachment_url IS '证明附件 URL: 病假条/结婚证等';
COMMENT ON COLUMN pmis_leave.approval_id IS '审批流实例 ID';
COMMENT ON COLUMN pmis_leave.approval_status IS '审批状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVED 已批准 / REJECTED 已驳回 / CANCELLED 已取消';
COMMENT ON COLUMN pmis_leave.approver_id IS '审批人 ID';
COMMENT ON COLUMN pmis_leave.approver_name IS '审批人姓名（冗余）';
COMMENT ON COLUMN pmis_leave.approval_time IS '审批时间';
COMMENT ON COLUMN pmis_leave.approval_remark IS '审批意见';
COMMENT ON COLUMN pmis_leave.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_leave.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_leave.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pl_tenant_emp
    ON pmis_leave(tenant_id, employee_id)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pl_tenant_date_range
    ON pmis_leave(tenant_id, start_date, end_date)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pl_tenant_type
    ON pmis_leave(tenant_id, leave_type)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pl_tenant_status
    ON pmis_leave(tenant_id, approval_status)
    WHERE deleted = 0;

-- --------------------------------------------------------------------

-- ====================================================================
-- V1.0.0_040 已优化内联至 V1.0.0_001 的 pmis_operation_log 定义中
-- (before_data / after_data / biz_type / biz_id 已内联,并升级为 JSONB)
-- ====================================================================

