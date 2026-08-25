-- ============================================================
-- V6__space_management.sql
-- 知识库空间管理（S3-P2-01）
-- ============================================================

-- 知识库空间表
CREATE TABLE IF NOT EXISTS ydsz_space (
    id              VARCHAR(32)     PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(512)    DEFAULT '',
    icon_url        VARCHAR(512)    DEFAULT '',
    cover_url       VARCHAR(512)    DEFAULT '',
    tenant_id       VARCHAR(64)     NOT NULL,
    owner_id        VARCHAR(64)     NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'active',  -- active / archived / deleted
    visibility      VARCHAR(16)     NOT NULL DEFAULT 'private', -- private / organization / public
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    member_count    INTEGER         NOT NULL DEFAULT 1,
    node_count      INTEGER         NOT NULL DEFAULT 0,
    quota_limit     BIGINT          DEFAULT NULL,  -- 空间独立配额（NULL 表示使用租户配额）
    quota_used      BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(64)     NOT NULL,
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64)     NOT NULL,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_time    TIMESTAMP       DEFAULT NULL
);

COMMENT ON TABLE ydsz_space IS '知识库空间（S3-P2-01：空间管理聚合根）';
COMMENT ON COLUMN ydsz_space.name IS '空间名称';
COMMENT ON COLUMN ydsz_space.owner_id IS '空间所有者（创建者）';
COMMENT ON COLUMN ydsz_space.status IS '空间状态：active-活跃 / archived-归档 / deleted-已删除';
COMMENT ON COLUMN ydsz_space.visibility IS '可见性：private-私有 / organization-组织 / public-公开';
COMMENT ON COLUMN ydsz_space.quota_limit IS '空间独立配额（字节，NULL 表示使用租户配额）';

-- 唯一索引：同一租户下空间名称唯一
CREATE UNIQUE INDEX uk_ydsz_space_tenant_name
    ON ydsz_space (tenant_id, name)
    WHERE deleted = FALSE;

-- 查询索引：按租户查空间列表
CREATE INDEX idx_ydsz_space_tenant_sort
    ON ydsz_space (tenant_id, sort_order)
    WHERE deleted = FALSE;

-- 所有者索引
CREATE INDEX idx_ydsz_space_owner
    ON ydsz_space (owner_id)
    WHERE deleted = FALSE;

-- 空间成员表
CREATE TABLE IF NOT EXISTS ydsz_space_member (
    id              VARCHAR(32)     PRIMARY KEY,
    space_id        VARCHAR(32)     NOT NULL,
    user_id         VARCHAR(64)     NOT NULL,
    role            VARCHAR(32)     NOT NULL,  -- owner / admin / editor / viewer
    tenant_id       VARCHAR(64)     NOT NULL,
    joined_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(64)     NOT NULL,
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64)     NOT NULL,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE ydsz_space_member IS '空间成员（S3-P2-01：空间角色管理）';
COMMENT ON COLUMN ydsz_space_member.role IS '角色：owner-所有者 / admin-管理员 / editor-编辑者 / viewer-查看者';

-- 唯一索引：同一用户在同一空间只有一个角色
CREATE UNIQUE INDEX uk_ydsz_space_member_space_user
    ON ydsz_space_member (space_id, user_id)
    WHERE deleted = FALSE;

-- 查询索引：按空间查成员列表
CREATE INDEX idx_ydsz_space_member_space_role
    ON ydsz_space_member (space_id, role)
    WHERE deleted = FALSE;

-- 用户索引：查询用户参与的空间
CREATE INDEX idx_ydsz_space_member_user
    ON ydsz_space_member (user_id)
    WHERE deleted = FALSE;

-- 空间模板表（S4-P3-02）
CREATE TABLE IF NOT EXISTS ydsz_space_template (
    id              VARCHAR(32)     PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(512)    DEFAULT '',
    category        VARCHAR(64)     DEFAULT 'general',  -- general / project / meeting / knowledge
    icon_url        VARCHAR(512)    DEFAULT '',
    tenant_id       VARCHAR(64)     NOT NULL,
    is_system       BOOLEAN         NOT NULL DEFAULT FALSE,  -- 系统内置模板不可删除
    is_public       BOOLEAN         NOT NULL DEFAULT TRUE,   -- 是否公开（所有租户可见）
    structure_json  TEXT            NOT NULL,  -- 模板结构 JSON（目录树定义）
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    usage_count     INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(64)     NOT NULL,
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64)     NOT NULL,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE ydsz_space_template IS '空间模板（S4-P3-02：文档模板体系）';
COMMENT ON COLUMN ydsz_space_template.category IS '模板分类：general-通用 / project-项目 / meeting-会议 / knowledge-知识库';
COMMENT ON COLUMN ydsz_space_template.structure_json IS '模板结构 JSON：定义目录树、初始页面等';

-- 查询索引：按租户和分类查模板
CREATE INDEX idx_ydsz_space_template_tenant_category
    ON ydsz_space_template (tenant_id, category)
    WHERE deleted = FALSE;

-- 系统模板索引
CREATE INDEX idx_ydsz_space_template_system
    ON ydsz_space_template (is_system, is_public)
    WHERE deleted = FALSE;
