-- ----------------------------------------------------------------------------
-- 模块名   : ydsz-userinfo（用户中心模块）
-- 说明     : 基于 ydsz-userinfo-infra 实体类与既有 SQL 整理的完整建表脚本
--            （用户/组织/角色/菜单/岗位、关联关系、登录与密码历史、
--              认证策略、社交/SAML/OAuth2/WebAuthn、安全告警）
-- 日期     : 2026-08-25
-- @author  : ydsz-team
-- ----------------------------------------------------------------------------

-- ============================================================================
-- 用户 / 组织主表
-- ============================================================================

-- 用户账号主表（用户中心核心实体，BCrypt 密码 + AES 字段级加密敏感信息）
CREATE TABLE IF NOT EXISTS ydsz_user_account (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    username        VARCHAR(64)     NOT NULL COMMENT '登录用户名（全局唯一）',
    password        VARCHAR(255)    NOT NULL COMMENT '登录密码（BCrypt 加密，禁止明文存储/返回）',
    real_name       VARCHAR(512)    DEFAULT NULL COMMENT '真实姓名（AES-256-GCM 加密存储，密文不可用于条件查询）',
    phone           VARCHAR(128)    DEFAULT NULL COMMENT '手机号（用于短信验证/找回密码，脱敏返回）',
    email           VARCHAR(128)    DEFAULT NULL COMMENT '邮箱（用于通知/找回密码，脱敏返回）',
    avatar          VARCHAR(1024)   DEFAULT NULL COMMENT '头像 URL',
    status          VARCHAR(32)     NOT NULL DEFAULT '1' COMMENT '账号状态（0=禁用，1=启用；另兼容 ENABLED/DISABLED/PENDING/SUSPENDED/RESIGNED 生命周期值）',
    user_type       VARCHAR(32)     DEFAULT NULL COMMENT '用户类型（PLATFORM/ISV/TENANT_ADMIN/REGULAR 等）',
    company_id      VARCHAR(32)     DEFAULT NULL COMMENT '所属公司 ID（关联 ydsz_company.id）',
    last_login_at   DATETIME        DEFAULT NULL COMMENT '最近登录时间',
    last_login_ip   VARCHAR(64)     DEFAULT NULL COMMENT '最近登录 IP',
    login_fail_count INT            NOT NULL DEFAULT 0 COMMENT '连续登录失败次数（达到阈值触发账号锁定）',
    locked_until    DATETIME        DEFAULT NULL COMMENT '账号锁定截止时间（解锁后自动清零 login_fail_count）',
    dept_id         VARCHAR(32)     DEFAULT NULL COMMENT '所属部门 ID（关联 ydsz_department.id，支持 dept: 审批人展开）',
    leader_id       VARCHAR(32)     DEFAULT NULL COMMENT '直属上级用户 ID（关联 ydsz_user_account.id，支持 leader: 审批人展开）',
    position_code   VARCHAR(32)     DEFAULT NULL COMMENT '岗位编码（如 PM/DEV/QA/SA，支持 position: 审批人展开）',
    ban_type        VARCHAR(32)     DEFAULT NULL COMMENT '封禁类型（TEMPORARY/PERMANENT，NULL 表示未封禁）',
    ban_reason      VARCHAR(512)    DEFAULT NULL COMMENT '封禁原因',
    ban_expire_at   DATETIME        DEFAULT NULL COMMENT '封禁到期时间（临时封禁使用，永久封禁为 NULL）',
    banned_by       VARCHAR(64)     DEFAULT NULL COMMENT '封禁操作人标识',
    banned_at       DATETIME        DEFAULT NULL COMMENT '封禁操作时间',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_username UNIQUE (username),
    INDEX idx_phone (phone),
    INDEX idx_dept_id (dept_id),
    INDEX idx_company_id (company_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号主表';

-- 公司表（组织架构最高级单位，支持集团-子公司多级架构）
CREATE TABLE IF NOT EXISTS ydsz_company (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    company_name    VARCHAR(128)    NOT NULL COMMENT '公司名称（前端展示）',
    company_code    VARCHAR(64)     NOT NULL COMMENT '公司编码（业务侧引用，全局唯一，建议格式 COMP_XXX）',
    parent_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '上级公司 ID（支持集团-子公司多级架构，"0"=顶级公司）',
    contact_person  VARCHAR(64)     DEFAULT NULL COMMENT '联系人姓名',
    contact_phone   VARCHAR(128)    DEFAULT NULL COMMENT '联系电话',
    address         VARCHAR(512)    DEFAULT NULL COMMENT '注册地址',
    status          VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态（ENABLED/DISABLED，禁用后公司下所有部门和用户均无法登录）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_company_code UNIQUE (company_code),
    INDEX idx_parent_id (parent_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司表';

-- 部门表（组织架构部门节点，无限级树形结构）
CREATE TABLE IF NOT EXISTS ydsz_department (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    parent_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '父部门 ID（根节点为 "0"，支持无限级树形结构）',
    dept_name       VARCHAR(128)    NOT NULL COMMENT '部门名称（前端展示）',
    dept_code       VARCHAR(64)     NOT NULL COMMENT '部门编码（业务侧引用，全局唯一，建议格式 DEPT_XXX）',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '部门描述（说明部门职责与归属）',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '同级排序序号（升序）',
    leader_id       VARCHAR(32)     DEFAULT NULL COMMENT '部门负责人用户 ID（关联 ydsz_user_account.id，支持 leader: 审批人展开）',
    status          VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态（ENABLED/DISABLED，禁用后部门下用户无法被分配新角色）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_dept_code UNIQUE (dept_code),
    INDEX idx_parent_id (parent_id),
    INDEX idx_leader_id (leader_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- ============================================================================
-- 角色 / 菜单 / 岗位 / 语言主表
-- ============================================================================

-- 角色表（RBAC 核心实体，内置角色保护 + 数据权限范围）
CREATE TABLE IF NOT EXISTS ydsz_role (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离，"0"=平台级角色，其它值为租户级角色）',
    role_code       VARCHAR(64)     NOT NULL COMMENT '角色编码（业务侧引用，全局唯一，建议格式 ROLE_XXX）',
    role_name       VARCHAR(128)    NOT NULL COMMENT '角色名称（前端展示）',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '角色描述（说明该角色的业务定位与适用场景）',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '同级排序序号（升序）',
    built_in        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否内置角色（1=内置，禁止删除/修改编码，如 SUPER_ADMIN/TENANT_ADMIN/AUDITOR/GUEST）',
    data_scope      VARCHAR(32)     DEFAULT NULL COMMENT '数据权限范围（ALL/DEPT_AND_CHILD/DEPT/SELF/CUSTOM）',
    status          VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态（ENABLED/DISABLED，禁用后拥有该角色的用户暂时无法访问系统）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_role_code UNIQUE (role_code, tenant_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 菜单/权限表（RBAC 最细粒度权限点：目录/菜单/按钮，无限级树形结构）
CREATE TABLE IF NOT EXISTS ydsz_menu (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    parent_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '父菜单 ID（根节点为 "0"，支持无限级树形结构）',
    menu_name       VARCHAR(128)    NOT NULL COMMENT '菜单名称（前端展示）',
    menu_code       VARCHAR(64)     NOT NULL COMMENT '菜单编码（业务侧引用，全局唯一）',
    menu_type       VARCHAR(32)     NOT NULL COMMENT '菜单类型（DIR=目录/MENU=菜单/BUTTON=按钮）',
    path            VARCHAR(255)    DEFAULT NULL COMMENT '前端路由路径（menuType=MENU 时使用）',
    component       VARCHAR(255)    DEFAULT NULL COMMENT '前端组件路径（menuType=MENU 时使用，如 system/user/index）',
    icon            VARCHAR(128)    DEFAULT NULL COMMENT '菜单图标（Iconify/Element Plus 图标名）',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '同级排序序号（升序）',
    permission_code VARCHAR(128)    DEFAULT NULL COMMENT '权限码（如 system:user:create，被后端 @AuthApiPermission 引用）',
    visible         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否前端可见（1=可见，0=隐藏但仍参与鉴权）',
    status          VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态（ENABLED/DISABLED）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_menu_code UNIQUE (menu_code),
    INDEX idx_parent_id (parent_id),
    INDEX idx_permission_code (permission_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单/权限表';

-- 岗位表（职责维度：PM/DEV/QA/SA 等，区别于部门与角色）
CREATE TABLE IF NOT EXISTS ydsz_post (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    post_name       VARCHAR(128)    NOT NULL COMMENT '岗位名称（前端展示，如「项目经理」「后端开发工程师」）',
    post_code       VARCHAR(64)     NOT NULL COMMENT '岗位编码（业务侧引用，全局唯一，如 PM/DEV/QA/SA）',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '岗位描述（说明岗位的工作职责与任职要求）',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '同级排序序号（升序）',
    status          VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态（ENABLED/DISABLED，禁用后岗位不可再被分配给新用户）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_post_code UNIQUE (post_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位表';

-- 语言配置表（系统支持的语言种类及默认语言标识，用于 i18n 国际化）
CREATE TABLE IF NOT EXISTS ydsz_language (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    language_code   VARCHAR(32)     NOT NULL COMMENT '语言编码（ISO 639-1 + 区域码，如 zh-CN/en-US/ja-JP/zh-TW）',
    language_name   VARCHAR(128)    NOT NULL COMMENT '语言名称（前端展示，如「简体中文」「English」）',
    is_default      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否默认语言（1=是，0=否，系统全局仅允许 1 个默认语言）',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序序号（升序，决定语言切换器展示顺序）',
    status          VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态（ENABLED/DISABLED，禁用后前端语言切换器隐藏该选项）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_language_code UNIQUE (language_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='语言配置表';

-- ============================================================================
-- 关联关系表（RBAC / 组织多对多中间表）
-- ============================================================================

-- 用户-角色关联表（RBAC 多对多中间表，用户多角色权限取并集）
CREATE TABLE IF NOT EXISTS ydsz_user_role (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    user_id         VARCHAR(32)     NOT NULL COMMENT '用户 ID（关联 ydsz_user_account.id）',
    role_id         VARCHAR(32)     NOT NULL COMMENT '角色 ID（关联 ydsz_role.id）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- 用户-岗位关联表（用户可兼任多岗位，主岗位由 ydsz_user_account.position_code 维护）
CREATE TABLE IF NOT EXISTS ydsz_user_post (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    user_id         VARCHAR(32)     NOT NULL COMMENT '用户 ID（关联 ydsz_user_account.id）',
    post_id         VARCHAR(32)     NOT NULL COMMENT '岗位 ID（关联 ydsz_post.id）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_post_id (post_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-岗位关联表';

-- 用户-部门关联表（支持兼岗，一个用户仅一个主部门由 Service 层事务保证）
CREATE TABLE IF NOT EXISTS ydsz_user_dept (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    user_id         VARCHAR(32)     NOT NULL COMMENT '用户 ID（关联 ydsz_user_account.id）',
    dept_id         VARCHAR(32)     NOT NULL COMMENT '部门 ID（关联 ydsz_department.id）',
    is_primary      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否主部门（1=是，0=否，一个用户只能有一个主部门，由 Service 层事务保证）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_dept_id (dept_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-部门关联表';

-- 公司-部门关联表（组织结构维度：一个部门可被多个公司共享）
CREATE TABLE IF NOT EXISTS ydsz_company_dept (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    company_id      VARCHAR(32)     NOT NULL COMMENT '公司 ID（关联 ydsz_company.id）',
    dept_id         VARCHAR(32)     NOT NULL COMMENT '部门 ID（关联 ydsz_department.id）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_company_id (company_id),
    INDEX idx_dept_id (dept_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司-部门关联表';

-- 角色-权限关联表（permission_id 实际指向 ydsz_menu.id，按钮级权限 menu_id 可为空）
CREATE TABLE IF NOT EXISTS ydsz_role_permission (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    role_id         VARCHAR(32)     NOT NULL COMMENT '角色 ID（关联 ydsz_role.id）',
    permission_id   VARCHAR(32)     NOT NULL COMMENT '权限 ID（实际指向 ydsz_menu.id，语义上为权限点而非菜单节点）',
    menu_id         VARCHAR(32)     DEFAULT NULL COMMENT '关联菜单 ID（可空，纯按钮级权限无对应菜单节点）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id),
    INDEX idx_menu_id (menu_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联表';

-- ============================================================================
-- 历史记录表（登录历史 / 密码历史）
-- ============================================================================

-- 用户登录历史表（记录每次登录尝试，用于安全审计/异常登录检测，建议保留 90 天）
CREATE TABLE IF NOT EXISTS ydsz_user_login_history (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    user_id         VARCHAR(32)     DEFAULT NULL COMMENT '用户 ID（关联 ydsz_user_account.id）',
    username        VARCHAR(64)     DEFAULT NULL COMMENT '用户名（冗余存储，即使用户被删除也可追溯）',
    login_ip        VARCHAR(64)     DEFAULT NULL COMMENT '登录 IP 地址',
    login_result    VARCHAR(32)     NOT NULL COMMENT '登录结果（SUCCESS/FAILED）',
    fail_reason     VARCHAR(255)    DEFAULT NULL COMMENT '失败原因（成功时为 NULL）',
    user_agent      VARCHAR(512)    DEFAULT NULL COMMENT '用户代理（浏览器/设备信息）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    INDEX idx_user_id_created_at (user_id, created_at),
    INDEX idx_ip (login_ip),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录历史表';

-- 密码历史表（防止短期内重复使用旧密码，仅保留最近 N 条记录）
CREATE TABLE IF NOT EXISTS ydsz_user_password_history (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    user_id         VARCHAR(32)     NOT NULL COMMENT '用户 ID（关联 ydsz_user_account.id）',
    password_hash   VARCHAR(255)    NOT NULL COMMENT 'BCrypt 加密后的历史密码哈希',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（该密码被设置的日期）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0=未删除，1=已删除，用于软删除兼容）',
    INDEX idx_user_id_created_at (user_id, created_at),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码历史表';

-- ============================================================================
-- 认证策略 / 社交 / SAML / OAuth2 配置表（基于既有 SQL 整合）
-- ============================================================================

-- ----------------------------------------------------------------------------
--  认证策略配置表 ydsz_auth_policy
-- ----------------------------------------------------------------------------
--  存储租户级认证策略（P3-1 多租户认证域隔离增强）
--
--  支持不同租户独立配置：密码策略、MFA策略、验证码策略、允许的身份提供者等
--
--  索引设计：
--    - uk_tenant_id: 租户 ID 唯一索引
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_auth_policy (
    id VARCHAR(64) PRIMARY KEY COMMENT '策略 ID（UUID）',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户 ID（NULL 表示全局默认策略）',
    name VARCHAR(64) NOT NULL COMMENT '策略名称',
    password_min_length INT DEFAULT 8 COMMENT '密码最小长度（≥ 6）',
    password_require_uppercase BOOLEAN DEFAULT TRUE COMMENT '密码必须包含大写字母',
    password_require_digit BOOLEAN DEFAULT TRUE COMMENT '密码必须包含数字',
    mfa_enabled BOOLEAN DEFAULT FALSE COMMENT '是否启用双因素认证',
    captcha_enabled BOOLEAN DEFAULT TRUE COMMENT '登录是否启用图形验证码',
    allowed_identity_providers VARCHAR(256) DEFAULT 'LOCAL' COMMENT '允许的身份提供者类型（逗号分隔：LOCAL/LDAP/SAML/OAUTH2）',
    max_sessions_per_user INT DEFAULT 3 COMMENT '每个用户最大会话数',
    session_timeout_seconds INT DEFAULT 7200 COMMENT '会话超时时间（秒）',
    remark VARCHAR(256) DEFAULT NULL COMMENT '备注说明',

    -- 通用字段（ydsz-common-jdbc MpBaseEntity）
    status VARCHAR(32) DEFAULT NULL COMMENT '状态标识',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建者用户 ID',
    updated_by VARCHAR(64) DEFAULT NULL COMMENT '更新者用户 ID',
    revision INT DEFAULT 0 COMMENT '乐观锁版本号',

    -- 索引
    UNIQUE INDEX uk_tenant_id (`tenant_id`) COMMENT '租户 ID 唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认证策略配置表';

-- 插入全局默认策略
INSERT INTO ydsz_auth_policy (id, tenant_id, name, password_min_length, password_require_uppercase, password_require_digit, mfa_enabled, captcha_enabled, allowed_identity_providers, max_sessions_per_user, session_timeout_seconds, remark, deleted, revision)
VALUES ('default-policy-001', NULL, '全局默认认证策略', 8, TRUE, TRUE, FALSE, TRUE, 'LOCAL', 3, 7200, '系统全局默认策略，租户未配置时继承', FALSE, 0)
ON DUPLICATE KEY UPDATE name = name;

-- ----------------------------------------------------------------------------
--  社交平台客户端配置表 ydsz_social_client
-- ----------------------------------------------------------------------------
--  存储社交平台 OAuth2 应用的客户端配置（P1-1 热更新支持）
--
--  配置优先级：数据库 > application.yml（DB 有值时覆盖 YAML）
--
--  索引设计：
--    - uk_platform: 平台标识唯一索引
--    - idx_status: 状态索引
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_social_client (
    id VARCHAR(64) PRIMARY KEY COMMENT '配置 ID（UUID）',
    platform VARCHAR(32) NOT NULL COMMENT '平台标识（GITHUB/DINGTALK/ENTERPRISE_WECHAT/FEISHU 等）',
    platform_name VARCHAR(64) DEFAULT NULL COMMENT '平台显示名称',
    app_id VARCHAR(128) NOT NULL COMMENT '应用 ID（平台分配的 appId）',
    app_secret VARCHAR(256) NOT NULL COMMENT '应用密钥（BCrypt 加密存储）',
    scope VARCHAR(256) DEFAULT NULL COMMENT 'OAuth2 授权范围（scope）',
    redirect_uri VARCHAR(512) DEFAULT NULL COMMENT '授权回调地址（redirectUri）',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    sort_order INT DEFAULT 100 COMMENT '排序权重（越小越靠前）',
    remark VARCHAR(256) DEFAULT NULL COMMENT '备注说明',

    -- 通用字段（ydsz-common-jdbc MpBaseEntity）
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户 ID',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建者用户 ID',
    updated_by VARCHAR(64) DEFAULT NULL COMMENT '更新者用户 ID',
    revision INT DEFAULT 0 COMMENT '乐观锁版本号',

    -- 索引
    UNIQUE INDEX uk_platform (`platform`) COMMENT '平台标识唯一索引',
    INDEX idx_status (`status`) COMMENT '状态索引',
    INDEX idx_tenant_deleted (`tenant_id`, `deleted`) COMMENT '租户+删除标记索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交平台客户端配置表';

-- ----------------------------------------------------------------------------
--  SAML 身份提供者配置表 ydsz_saml_idp_config
-- ----------------------------------------------------------------------------
--  存储 SAML 2.0 Identity Provider 的元数据和证书（P2-1 多租户）
--
--  支持多个 IdP 注册，每个租户可配置独立的 SAML IdP
--
--  索引设计：
--    - uk_entity_id: Entity ID 唯一索引
--    - idx_status: 状态索引
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_saml_idp_config (
    id VARCHAR(64) PRIMARY KEY COMMENT '配置 ID（UUID）',
    name VARCHAR(64) NOT NULL COMMENT 'IdP 显示名称',
    entity_id VARCHAR(512) NOT NULL COMMENT 'IdP Entity ID（SAML 协议中 IdP 的唯一标识 URI）',
    sso_url VARCHAR(512) DEFAULT NULL COMMENT 'IdP SSO 端点 URL',
    certificate TEXT DEFAULT NULL COMMENT 'IdP 公钥证书（PEM 格式，用于验证 SAML Response 签名）',
    email_attribute VARCHAR(64) DEFAULT 'email' COMMENT '用户邮箱对应的 SAML Attribute 名称',
    display_name_attribute VARCHAR(64) DEFAULT 'displayName' COMMENT '用户显示名称对应的 SAML Attribute 名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    sort_order INT DEFAULT 100 COMMENT '排序权重（越小越靠前）',
    remark VARCHAR(256) DEFAULT NULL COMMENT '备注说明',

    -- 通用字段（ydsz-common-jdbc MpBaseEntity）
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户 ID',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建者用户 ID',
    updated_by VARCHAR(64) DEFAULT NULL COMMENT '更新者用户 ID',
    revision INT DEFAULT 0 COMMENT '乐观锁版本号',

    -- 索引
    UNIQUE INDEX uk_entity_id (`entity_id`) COMMENT 'Entity ID 唯一索引',
    INDEX idx_status (`status`) COMMENT '状态索引',
    INDEX idx_tenant_deleted (`tenant_id`, `deleted`) COMMENT '租户+删除标记索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SAML 2.0 身份提供者配置表';

-- ----------------------------------------------------------------------------
--  OAuth2 应用注册表 ydsz_oauth2_application
-- ----------------------------------------------------------------------------
--  存储 OAuth2 客户端应用注册信息，由 OAuth2ApplicationService 写入
--
--  索引设计：
--    - uk_client_id: clientId 唯一索引
--    - idx_status: 状态索引
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_oauth2_application (
    id VARCHAR(64) PRIMARY KEY COMMENT '应用 ID（UUID）',
    client_id VARCHAR(128) NOT NULL COMMENT '客户端 ID（唯一标识）',
    client_name VARCHAR(256) NOT NULL COMMENT '应用名称',
    client_secret VARCHAR(256) NOT NULL COMMENT '客户端密钥（BCrypt 加密存储）',
    client_type VARCHAR(16) NOT NULL COMMENT '客户端类型：CONFIDENTIAL/PUBLIC',
    redirect_uris JSON NOT NULL COMMENT '授权回调地址白名单（JSON 数组）',
    allowed_scopes JSON DEFAULT NULL COMMENT '允许申请的权限范围（JSON 数组）',
    allowed_audiences JSON DEFAULT NULL COMMENT '允许的受众（JSON 数组）',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '应用状态：ENABLED/DISABLED',
    description VARCHAR(512) DEFAULT NULL COMMENT '应用描述',
    icon_url VARCHAR(512) DEFAULT NULL COMMENT '应用图标 URL',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建者用户 ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 通用字段（ydsz-common-jdbc MpBaseEntity）
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户 ID',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建者用户 ID',
    updated_by VARCHAR(64) DEFAULT NULL COMMENT '更新者用户 ID',
    revision INT DEFAULT 0 COMMENT '乐观锁版本号',

    -- 索引
    UNIQUE INDEX uk_client_id (`client_id`) COMMENT 'clientId 唯一索引',
    INDEX idx_status (`status`) COMMENT '状态索引',
    INDEX idx_tenant_deleted (`tenant_id`, `deleted`) COMMENT '租户+删除标记索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2 应用注册表';

-- 社交账号绑定表（用户与第三方社交平台的绑定关系，令牌 AES-256-GCM 加密存储）
CREATE TABLE IF NOT EXISTS ydsz_social_account (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    user_id         VARCHAR(32)     NOT NULL COMMENT '关联用户 ID（关联 ydsz_user_account.id）',
    platform        VARCHAR(32)     NOT NULL COMMENT '平台标识（WECHAT/DINGTALK/ENTERPRISE_WECHAT/GITHUB）',
    open_id         VARCHAR(128)    NOT NULL COMMENT '平台用户唯一标识',
    union_id        VARCHAR(128)    DEFAULT NULL COMMENT '平台统一应用标识（可选，微信系平台返回）',
    nickname        VARCHAR(128)    DEFAULT NULL COMMENT '社交昵称（平台侧显示名）',
    avatar_url      VARCHAR(1024)   DEFAULT NULL COMMENT '头像 URL',
    access_token    VARCHAR(1024)   DEFAULT NULL COMMENT '访问令牌（AES-256-GCM 加密存储，密文不可用于条件查询）',
    refresh_token   VARCHAR(1024)   DEFAULT NULL COMMENT '刷新令牌（AES-256-GCM 加密存储，部分平台不返回 refresh_token）',
    expires_at      DATETIME        DEFAULT NULL COMMENT '令牌过期时间',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_platform_open_id UNIQUE (platform, open_id),
    INDEX idx_user_id (user_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社交账号绑定表';

-- ============================================================================
-- 安全告警 / WebAuthn 凭证
-- ============================================================================

-- ----------------------------------------------------------------------------
--  安全告警表 ydsz_security_alert
-- ----------------------------------------------------------------------------
--  存储安全告警事件记录，由 SecurityAlertService 写入，由管理员通过
--  SecurityAlertController API 查询和处理
--
--  索引设计：
--    - idx_status_risk: 状态+风险等级复合索引（待处理告警查询）
--    - idx_type_time: 告警类型+创建时间复合索引（告警去重统计）
--    - idx_user_id: 用户 ID 索引（按用户查询告警历史）
--    - idx_source_ip: 来源 IP 索引（IP 维度告警统计）
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_security_alert (
    id VARCHAR(64) PRIMARY KEY COMMENT '告警 ID（UUID）',
    alert_type VARCHAR(32) NOT NULL COMMENT '告警类型：ACCOUNT_LOCKED/ACCOUNT_BANNED/MFA_FAILED/BRUTE_FORCE/ANOMALOUS_LOGIN/PASSWORD_SPRAY',
    risk_level VARCHAR(16) NOT NULL COMMENT '风险等级：LOW/MEDIUM/HIGH/CRITICAL',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '关联用户 ID',
    username VARCHAR(128) DEFAULT NULL COMMENT '关联用户名',
    source_ip VARCHAR(64) DEFAULT NULL COMMENT '来源 IP',
    title VARCHAR(256) NOT NULL COMMENT '告警标题',
    content TEXT COMMENT '告警内容',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '告警状态：PENDING/ACKNOWLEDGED/RESOLVED/IGNORED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    handled_at TIMESTAMP DEFAULT NULL COMMENT '处理时间',
    handler_note VARCHAR(512) DEFAULT NULL COMMENT '处理备注',

    -- 通用字段（ydsz-common-jdbc MpBaseEntity）
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户 ID',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建者用户 ID',
    updated_by VARCHAR(64) DEFAULT NULL COMMENT '更新者用户 ID',
    revision INT DEFAULT 0 COMMENT '乐观锁版本号',

    -- 索引
    INDEX idx_status_risk (`status`, `risk_level`) COMMENT '状态+风险等级复合索引',
    INDEX idx_type_time (`alert_type`, `created_at`) COMMENT '告警类型+创建时间复合索引',
    INDEX idx_user_id (`user_id`) COMMENT '用户 ID 索引',
    INDEX idx_source_ip (`source_ip`) COMMENT '来源 IP 索引',
    INDEX idx_tenant_deleted (`tenant_id`, `deleted`) COMMENT '租户+删除标记索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全告警表';

-- WebAuthn 凭证表（用户注册的公钥凭证，用于 FIDO2 无密码认证）
CREATE TABLE IF NOT EXISTS ydsz_user_credential (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID（自增）',
    credential_id   VARCHAR(512)    NOT NULL COMMENT '凭证 ID（Base64URL 编码）',
    user_id         VARCHAR(32)     NOT NULL COMMENT '用户 ID（关联 ydsz_user_account.id）',
    public_key      VARCHAR(1024)   NOT NULL COMMENT '公钥（COSE 密钥格式，Base64URL 编码）',
    sign_count      BIGINT          NOT NULL DEFAULT 0 COMMENT '签名计数器（防克隆检测）',
    credential_type VARCHAR(32)     DEFAULT NULL COMMENT '凭证类型（如 public-key）',
    aaguid          VARCHAR(64)     DEFAULT NULL COMMENT 'AAGUID（认证器唯一标识）',
    display_name    VARCHAR(128)    DEFAULT NULL COMMENT '凭证友好名称',
    registered_at   DATETIME        DEFAULT NULL COMMENT '注册时间',
    last_used_at    DATETIME        DEFAULT NULL COMMENT '最后使用时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '删除标记（软删除，0=未删除，1=已删除）',
    CONSTRAINT uk_credential_id UNIQUE (credential_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WebAuthn 凭证表';
