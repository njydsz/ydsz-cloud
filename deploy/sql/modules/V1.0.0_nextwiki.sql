-- ============================================================
-- NextWiki 网盘知识库服务 - 数据库表结构（V1.0.0）
-- 模块: ydsz-nextwiki
-- 物理 Mapper 路径：ydsz-nextwiki/ydsz-nextwiki-infra/.../mapper/
-- 说明: 网盘文件管理、版本控制、分享ACL、标签、回收站、配额
-- 任何 schema 调整请直接编辑本文件，禁止新增增量脚本
-- ============================================================

-- 1. 文件节点表
CREATE TABLE IF NOT EXISTS nw_file_node (
    id              VARCHAR(32) PRIMARY KEY,
    parent_id       VARCHAR(32) NOT NULL DEFAULT '0',
    name            VARCHAR(255) NOT NULL,
    node_type       VARCHAR(10) NOT NULL DEFAULT 'file',
    suffix          VARCHAR(20),
    size            BIGINT NOT NULL DEFAULT 0,
    storage_key     VARCHAR(512),
    bucket_name     VARCHAR(128),
    mime_type       VARCHAR(128),
    path            VARCHAR(1024) NOT NULL DEFAULT '/',
    level           INT NOT NULL DEFAULT 0,
    sort            INT NOT NULL DEFAULT 0,
    current_version INT NOT NULL DEFAULT 0,
    file_hash       VARCHAR(64),
    thumbnail_key   VARCHAR(512),
    preview_ready   BOOLEAN NOT NULL DEFAULT FALSE,
    starred         BOOLEAN NOT NULL DEFAULT FALSE,
    share_status    VARCHAR(10) NOT NULL DEFAULT 'private',
    deleted_time    TIMESTAMP,
    original_path   VARCHAR(1024),
    status          VARCHAR(20) DEFAULT 'active',
    created_by      VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revision        INT NOT NULL DEFAULT 0,
    deleted         INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE nw_file_node IS '网盘文件节点（文件/文件夹统一表示）';
COMMENT ON COLUMN nw_file_node.node_type IS '节点类型: folder / file';
COMMENT ON COLUMN nw_file_node.path IS '目录路径（闭包路径，以/结尾）';
COMMENT ON COLUMN nw_file_node.share_status IS '共享状态: private / shared / public';
COMMENT ON COLUMN nw_file_node.deleted_time IS '逻辑删除时间（回收站功能）';

CREATE INDEX IF NOT EXISTS idx_file_node_parent ON nw_file_node (parent_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_file_node_path ON nw_file_node (path) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_file_node_hash ON nw_file_node (file_hash) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_file_node_created_by ON nw_file_node (created_by) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_file_node_starred ON nw_file_node (created_by, starred) WHERE deleted = 0 AND starred = TRUE;

-- 2. 文件版本表
CREATE TABLE IF NOT EXISTS nw_file_version (
    id              VARCHAR(32) PRIMARY KEY,
    file_node_id    VARCHAR(32) NOT NULL,
    version_number  INT NOT NULL,
    storage_key     VARCHAR(512) NOT NULL,
    size            BIGINT NOT NULL,
    file_hash       VARCHAR(64),
    mime_type       VARCHAR(128),
    remark          VARCHAR(500),
    change_type     VARCHAR(20) NOT NULL DEFAULT 'update',
    is_active       BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(20) DEFAULT 'active',
    created_by      VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revision        INT NOT NULL DEFAULT 0,
    deleted         INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE nw_file_version IS '文件版本历史';

CREATE INDEX IF NOT EXISTS idx_file_version_node ON nw_file_version (file_node_id);
CREATE INDEX IF NOT EXISTS idx_file_version_active ON nw_file_version (file_node_id, is_active) WHERE deleted = 0;

-- 3. 分享链接表
CREATE TABLE IF NOT EXISTS nw_share_link (
    id                VARCHAR(32) PRIMARY KEY,
    file_node_id      VARCHAR(32) NOT NULL,
    share_code        VARCHAR(32) NOT NULL UNIQUE,
    extract_code      VARCHAR(4),
    share_type        VARCHAR(10) NOT NULL DEFAULT 'view',
    expire_time       TIMESTAMP,
    max_access_count  INT,
    access_count      INT NOT NULL DEFAULT 0,
    status            VARCHAR(10) NOT NULL DEFAULT 'active',
    password          VARCHAR(128),
    created_by        VARCHAR(64),
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64),
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revision          INT NOT NULL DEFAULT 0,
    deleted           INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE nw_share_link IS '文件分享链接';
COMMENT ON COLUMN nw_share_link.share_type IS '分享类型: view / download / edit';
COMMENT ON COLUMN nw_share_link.password IS 'BCrypt 加密的访问密码';

CREATE INDEX IF NOT EXISTS idx_share_link_file ON nw_share_link (file_node_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_share_link_user ON nw_share_link (created_by) WHERE deleted = 0 AND status = 'active';

-- 4. 文件 ACL 权限表
CREATE TABLE IF NOT EXISTS nw_file_acl (
    id              VARCHAR(32) PRIMARY KEY,
    file_node_id    VARCHAR(32) NOT NULL,
    grantee_type    VARCHAR(10) NOT NULL,
    grantee_id      VARCHAR(64) NOT NULL,
    permission_mask INT NOT NULL DEFAULT 1,
    inherited       BOOLEAN NOT NULL DEFAULT FALSE,
    is_owner        BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(20) DEFAULT 'active',
    created_by      VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revision        INT NOT NULL DEFAULT 0,
    deleted         INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE nw_file_acl IS '文件级 ACL 权限';
COMMENT ON COLUMN nw_file_acl.grantee_type IS '授权对象类型: user / role / group / tenant';
COMMENT ON COLUMN nw_file_acl.permission_mask IS '权限位掩码: read=1,write=2,delete=4,share=8,download=16';

CREATE INDEX IF NOT EXISTS idx_file_acl_node ON nw_file_acl (file_node_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_file_acl_grantee ON nw_file_acl (grantee_type, grantee_id) WHERE deleted = 0;

-- 5. 标签表
CREATE TABLE IF NOT EXISTS nw_tag (
    id              VARCHAR(32) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    color           VARCHAR(10) DEFAULT '#1890ff',
    type            VARCHAR(10) NOT NULL DEFAULT 'manual',
    usage_count     INT NOT NULL DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'active',
    created_by      VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revision        INT NOT NULL DEFAULT 0,
    deleted         INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE nw_tag IS '文件标签';

-- 6. 文件-标签关联表
CREATE TABLE IF NOT EXISTS nw_file_tag (
    id              VARCHAR(32) PRIMARY KEY,
    file_node_id    VARCHAR(32) NOT NULL,
    tag_id          VARCHAR(32) NOT NULL,
    status          VARCHAR(20) DEFAULT 'active',
    created_by      VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revision        INT NOT NULL DEFAULT 0,
    deleted         INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE nw_file_tag IS '文件-标签关联（多对多）';

CREATE INDEX IF NOT EXISTS idx_file_tag_node ON nw_file_tag (file_node_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_file_tag_tag ON nw_file_tag (tag_id) WHERE deleted = 0;

-- 7. 存储配额表
CREATE TABLE IF NOT EXISTS nw_storage_quota (
    id                  VARCHAR(32) PRIMARY KEY,
    scope_type          VARCHAR(10) NOT NULL,
    scope_id            VARCHAR(64) NOT NULL,
    quota_limit         BIGINT NOT NULL DEFAULT 10737418240,
    quota_used          BIGINT NOT NULL DEFAULT 0,
    file_count_limit    INT NOT NULL DEFAULT 10000,
    file_count_used     INT NOT NULL DEFAULT 0,
    status              VARCHAR(20) DEFAULT 'active',
    created_by          VARCHAR(64),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64),
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revision            INT NOT NULL DEFAULT 0,
    deleted             INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE nw_storage_quota IS '存储配额';
COMMENT ON COLUMN nw_storage_quota.scope_type IS '配额维度: user / tenant / project';

CREATE UNIQUE INDEX IF NOT EXISTS idx_quota_scope ON nw_storage_quota (scope_type, scope_id) WHERE deleted = 0;

-- 8. 回收站表
CREATE TABLE IF NOT EXISTS nw_trash_item (
    id                  VARCHAR(32) PRIMARY KEY,
    file_node_id        VARCHAR(32) NOT NULL,
    original_name       VARCHAR(255) NOT NULL,
    original_path       VARCHAR(1024),
    original_parent_id  VARCHAR(32),
    node_type           VARCHAR(10) NOT NULL,
    size                BIGINT NOT NULL DEFAULT 0,
    deleted_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    purge_time          TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '30 days'),
    status              VARCHAR(10) NOT NULL DEFAULT 'in_trash',
    created_by          VARCHAR(64),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64),
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revision            INT NOT NULL DEFAULT 0,
    deleted             INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE nw_trash_item IS '回收站条目';
COMMENT ON COLUMN nw_trash_item.status IS '状态: in_trash / restored / purged';

CREATE INDEX IF NOT EXISTS idx_trash_user ON nw_trash_item (created_by) WHERE deleted = 0 AND status = 'in_trash';
CREATE INDEX IF NOT EXISTS idx_trash_purge ON nw_trash_item (purge_time) WHERE deleted = 0 AND status = 'in_trash';

-- 9. 文件搜索索引表（数据库 fallback，Elasticsearch 可用时此表可选）
CREATE TABLE IF NOT EXISTS nw_search_index (
    id              VARCHAR(32) PRIMARY KEY,
    file_node_id    VARCHAR(32) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    path            VARCHAR(1024),
    content         TEXT,
    suffix          VARCHAR(20),
    mime_type       VARCHAR(128),
    size            BIGINT,
    tags            VARCHAR(500),
    created_by      VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revision        INT NOT NULL DEFAULT 0,
    deleted         INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE nw_search_index IS '文件搜索索引（数据库 fallback）';

CREATE INDEX IF NOT EXISTS idx_search_name ON nw_search_index USING gin (to_tsvector('simple', name));
CREATE INDEX IF NOT EXISTS idx_search_content ON nw_search_index USING gin (to_tsvector('simple', content));
CREATE INDEX IF NOT EXISTS idx_search_user ON nw_search_index (created_by) WHERE deleted = 0;

-- ============================================================
-- 种子数据
-- ============================================================

-- 默认租户配额（10GB / 10000 文件）
INSERT INTO nw_storage_quota (id, scope_type, scope_id, quota_limit, quota_used, file_count_limit, file_count_used, status, created_by, updated_by)
SELECT '0', 'tenant', 'default', 10737418240, 0, 10000, 0, 'active', 'SYSTEM', 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM nw_storage_quota WHERE scope_type = 'tenant' AND scope_id = 'default');
