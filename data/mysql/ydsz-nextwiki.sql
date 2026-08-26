-- ============================================================================
-- 模块名：ydsz-nextwiki（知识库/网盘模块）
-- 说明：基于 ydsz-nextwiki-infra 实体类与既有迁移脚本（V2~V6）整理的完整建表脚本
--       （MySQL 方言，已将 PostgreSQL 迁移脚本中的部分索引/函数式归档策略转译为 MySQL 等价形式，
--        所有变更合并为完整 CREATE TABLE 语句，无 ALTER）
-- 日期：2026-08-25
-- @author ydsz-team
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 文件节点主表（网盘文件/目录树）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_file_node (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    parent_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '父节点ID（根目录为 "0"）',
    name            VARCHAR(255)    NOT NULL COMMENT '节点名称（文件名或目录名）',
    node_type       VARCHAR(32)     NOT NULL COMMENT '节点类型：folder / file',
    suffix          VARCHAR(64)     DEFAULT NULL COMMENT '文件扩展名（小写，不含点；文件夹为空）',
    size            BIGINT          NOT NULL DEFAULT 0 COMMENT '文件大小（字节；文件夹为 0）',
    storage_key     VARCHAR(1024)   DEFAULT NULL COMMENT '底层存储对象键（objectName）',
    bucket_name     VARCHAR(128)    DEFAULT NULL COMMENT '存储桶名称',
    mime_type       VARCHAR(128)    DEFAULT NULL COMMENT 'MIME 类型',
    path            VARCHAR(1024)   NOT NULL COMMENT '目录路径（如 /root/docs/contract/），用于快速判断层级关系',
    level           INT             NOT NULL DEFAULT 0 COMMENT '层级深度（根为 0）',
    sort            INT             NOT NULL DEFAULT 0 COMMENT '排序序号',
    current_version INT             NOT NULL DEFAULT 1 COMMENT '当前版本号（从 1 开始，每次更新 +1）',
    file_hash       VARCHAR(64)     DEFAULT NULL COMMENT '文件 SHA-256 哈希（用于秒传去重）',
    thumbnail_key   VARCHAR(1024)   DEFAULT NULL COMMENT '缩略图存储键',
    preview_ready   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已生成预览',
    starred         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否星标文件',
    share_status    VARCHAR(32)     NOT NULL DEFAULT 'private' COMMENT '共享状态：private / shared / public',
    deleted_time    DATETIME        DEFAULT NULL COMMENT '逻辑删除时间（回收站功能：删除时记录时间，30 天后永久删除）',
    original_path   VARCHAR(1024)   DEFAULT NULL COMMENT '原始路径（删除前的完整路径，用于恢复）',
    storage_class   VARCHAR(32)     NOT NULL DEFAULT 'STANDARD' COMMENT '存储类型：STANDARD / GLACIER / DEEP_ARCHIVE（冷数据归档）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_tenant_deleted (tenant_id, deleted),
    INDEX idx_ydsz_wiki_file_node_parent_deleted_updated (parent_id, deleted, updated_at),
    INDEX idx_ydsz_wiki_file_node_parent_deleted_type_updated (parent_id, deleted, node_type, updated_at),
    INDEX idx_ydsz_wiki_file_node_path (path(255)),
    INDEX idx_ydsz_wiki_file_node_created_deleted_type (created_by, deleted, node_type),
    INDEX idx_ydsz_wiki_file_node_file_hash (file_hash),
    INDEX idx_ydsz_wiki_file_node_not_deleted (id, parent_id, tenant_id),
    INDEX idx_ydsz_wiki_file_node_storage_class (node_type, deleted, storage_class, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网盘文件节点（统一表示文件和目录，构成目录树的核心节点）';

-- ----------------------------------------------------------------------------
-- 2. 文件版本历史表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_file_version (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    file_node_id    VARCHAR(32)     NOT NULL COMMENT '关联的文件节点ID',
    version_number  INT             NOT NULL COMMENT '版本号（从 1 开始递增）',
    storage_key     VARCHAR(1024)   DEFAULT NULL COMMENT '该版本的存储对象键',
    size            BIGINT          NOT NULL DEFAULT 0 COMMENT '该版本的文件大小（字节）',
    file_hash       VARCHAR(64)     DEFAULT NULL COMMENT '该版本的文件 SHA-256 哈希',
    mime_type       VARCHAR(128)    DEFAULT NULL COMMENT '该版本的 MIME 类型',
    remark          VARCHAR(512)    DEFAULT NULL COMMENT '版本说明（用户自定义的版本备注）',
    change_type     VARCHAR(32)     NOT NULL DEFAULT 'update' COMMENT '变更类型：create / update / rollback',
    active          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否为当前活跃版本',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_node_version (file_node_id, version_number),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件版本历史（每次文件更新生成一条版本记录，支持版本回溯）';

-- ----------------------------------------------------------------------------
-- 3. 标签表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_tag (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    name            VARCHAR(255)    NOT NULL COMMENT '标签名称',
    color           VARCHAR(32)     DEFAULT NULL COMMENT '标签颜色（十六进制颜色码，如 #1890ff）',
    type            VARCHAR(32)     NOT NULL DEFAULT 'manual' COMMENT '标签类型：manual（手动）/ auto（自动推荐）/ system（系统预设）',
    usage_count     INT             NOT NULL DEFAULT 0 COMMENT '使用次数（文件关联数）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_tag_name (tenant_id, name),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签（对文件/文件夹打标签，用于知识库分类和检索）';

-- ----------------------------------------------------------------------------
-- 4. 文件-标签关联表（多对多）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_file_tag (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    file_node_id    VARCHAR(32)     NOT NULL COMMENT '文件节点ID',
    tag_id          VARCHAR(32)     NOT NULL COMMENT '标签ID',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_node_tag (file_node_id, tag_id),
    INDEX idx_tag_id (tag_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件-标签关联（多对多）';

-- ----------------------------------------------------------------------------
-- 5. 文件评论表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_file_comment (
    id                VARCHAR(32)   NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)   NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    file_node_id      VARCHAR(32)   NOT NULL COMMENT '关联的文件节点ID',
    content           TEXT          NOT NULL COMMENT '评论内容',
    parent_comment_id VARCHAR(32)   DEFAULT NULL COMMENT '父评论ID（用于回复，null 表示顶级评论）',
    resolved          TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已解决（用于批注功能）',
    position          JSON          DEFAULT NULL COMMENT '评论位置信息（JSON，用于文档内定位批注）',
    edited            TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否被编辑过',
    status            VARCHAR(32)   DEFAULT NULL COMMENT '状态标识',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)   DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    INDEX idx_file_node_id (file_node_id),
    INDEX idx_parent_comment_id (parent_comment_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件评论（支持文件级别的评论和回复，用于知识库协作讨论）';

-- ----------------------------------------------------------------------------
-- 6. 文件级 ACL 权限表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_file_acl (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    file_node_id    VARCHAR(32)     NOT NULL COMMENT '文件节点ID',
    grantee_type    VARCHAR(32)     NOT NULL COMMENT '授权对象类型：user / role / group / tenant',
    grantee_id      VARCHAR(64)     NOT NULL COMMENT '授权对象ID（用户ID / 角色ID / 组ID / 租户ID）',
    permission_mask INT             NOT NULL DEFAULT 0 COMMENT '权限位掩码（read=1, write=2, delete=4, share=8, download=16）',
    inherited       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否继承自父目录',
    owner           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否为所有者（所有者拥有全部权限）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_grantee (file_node_id, grantee_type, grantee_id),
    INDEX idx_grantee (grantee_type, grantee_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件级 ACL 权限（文件/文件夹级别的细粒度权限控制）';

-- ----------------------------------------------------------------------------
-- 7. 文件分享链接表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_share_link (
    id                VARCHAR(32)   NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)   NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    file_node_id      VARCHAR(32)   NOT NULL COMMENT '关联的文件节点ID',
    share_code        VARCHAR(64)   NOT NULL COMMENT '分享码（URL 中的唯一标识，UUID 生成）',
    extract_code      VARCHAR(8)    DEFAULT NULL COMMENT '提取码（4 位数字，访问时需要输入）',
    share_type        VARCHAR(32)   NOT NULL DEFAULT 'view' COMMENT '分享类型：view（仅查看）/ download（可下载）/ edit（可编辑）',
    expire_time       DATETIME      DEFAULT NULL COMMENT '过期时间（null 表示永久有效）',
    max_access_count  INT           DEFAULT NULL COMMENT '最大访问次数（null 表示不限）',
    access_count      INT           NOT NULL DEFAULT 0 COMMENT '已访问次数',
    status            VARCHAR(32)   NOT NULL DEFAULT 'active' COMMENT '分享状态：active / expired / revoked',
    password          VARCHAR(128)  DEFAULT NULL COMMENT '分享密码（BCrypt 加密；空表示无密码）',
    share_target_type VARCHAR(32)   NOT NULL DEFAULT 'PUBLIC' COMMENT '分享目标类型：PUBLIC(公开) / USER(指定用户) / DEPT(部门)',
    reminder_sent     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '到期提醒是否已发送',
    title             VARCHAR(255)  DEFAULT NULL COMMENT '分享标题（可选）',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)   DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_share_code (share_code),
    INDEX idx_file_node_id (file_node_id),
    INDEX idx_ydsz_wiki_share_link_expire_reminder (status, expire_time, reminder_sent),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件分享链接（带密码和过期时间的文件级临时授权机制）';

-- ----------------------------------------------------------------------------
-- 8. 分享目标用户表（定向分享）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_share_recipient (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    share_id        VARCHAR(32)     NOT NULL COMMENT '分享链接 ID',
    recipient_type  VARCHAR(32)     NOT NULL DEFAULT 'USER' COMMENT '接收者类型：USER/DEPT/ROLE',
    recipient_id    VARCHAR(64)     NOT NULL COMMENT '接收者 ID',
    recipient_name  VARCHAR(128)    DEFAULT NULL COMMENT '接收者名称',
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/VIEWED/REVOKED',
    viewed_at       DATETIME        DEFAULT NULL COMMENT '首次查看时间',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_share_recipient (share_id, recipient_type, recipient_id),
    INDEX idx_ydsz_wiki_share_recipient_share (share_id, deleted),
    INDEX idx_ydsz_wiki_share_recipient_user (recipient_id, status, deleted),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享目标用户（定向分享，记录分享链接的目标接收者）';

-- ----------------------------------------------------------------------------
-- 9. 分享链接访问日志表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_share_access_log (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    share_id        VARCHAR(32)     NOT NULL COMMENT '分享链接 ID',
    share_code      VARCHAR(64)     NOT NULL COMMENT '分享码',
    file_node_id    VARCHAR(32)     NOT NULL COMMENT '文件节点 ID',
    visitor_id      VARCHAR(64)     DEFAULT NULL COMMENT '访问者用户 ID（匿名为空）',
    visitor_name    VARCHAR(128)    DEFAULT NULL COMMENT '访问者名称',
    visitor_ip      VARCHAR(64)     DEFAULT NULL COMMENT '访问者 IP 地址',
    user_agent      VARCHAR(512)    DEFAULT NULL COMMENT '访问者 User-Agent',
    access_type     VARCHAR(32)     NOT NULL DEFAULT 'VIEW' COMMENT '访问类型：VIEW/DOWNLOAD/EDIT',
    access_status   VARCHAR(32)     NOT NULL DEFAULT 'SUCCESS' COMMENT '访问状态：SUCCESS/FAIL',
    fail_reason     VARCHAR(255)    DEFAULT NULL COMMENT '失败原因',
    access_time     DATETIME        NOT NULL COMMENT '访问时间',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    INDEX idx_access_time (access_time),
    INDEX idx_ydsz_wiki_share_access_log_share_id (share_id, created_at),
    INDEX idx_ydsz_wiki_share_access_log_created (created_at),
    INDEX idx_ydsz_wiki_share_access_log_visitor (visitor_id, created_at),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享链接访问日志（记录每次分享链接被访问的详细信息，用于安全审计和访问统计）';

-- ----------------------------------------------------------------------------
-- 10. 分享访问日志归档表（V5 归档策略，MySQL 以普通表落地，按 access_time/created_at 定期清理归档）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_share_access_log_archive (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    share_id        VARCHAR(32)     NOT NULL COMMENT '分享链接 ID',
    share_code      VARCHAR(64)     NOT NULL COMMENT '分享码',
    file_node_id    VARCHAR(32)     NOT NULL COMMENT '文件节点 ID',
    visitor_id      VARCHAR(64)     DEFAULT NULL COMMENT '访问者用户 ID（匿名为空）',
    visitor_name    VARCHAR(128)    DEFAULT NULL COMMENT '访问者名称',
    visitor_ip      VARCHAR(64)     DEFAULT NULL COMMENT '访问者 IP 地址',
    user_agent      VARCHAR(512)    DEFAULT NULL COMMENT '访问者 User-Agent',
    access_type     VARCHAR(32)     NOT NULL DEFAULT 'VIEW' COMMENT '访问类型：VIEW/DOWNLOAD/EDIT',
    access_status   VARCHAR(32)     NOT NULL DEFAULT 'SUCCESS' COMMENT '访问状态：SUCCESS/FAIL',
    fail_reason     VARCHAR(255)    DEFAULT NULL COMMENT '失败原因',
    access_time     DATETIME        NOT NULL COMMENT '访问时间',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_archive_share_created (share_id, created_at),
    INDEX idx_archive_created (created_at),
    INDEX idx_archive_access_time (access_time),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享访问日志归档表（归档 90 天前访问日志，防止主表无限膨胀）';

-- ----------------------------------------------------------------------------
-- 11. 知识库空间表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_space (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    name            VARCHAR(128)    NOT NULL COMMENT '空间名称',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '空间描述',
    icon_url        VARCHAR(1024)   DEFAULT NULL COMMENT '空间图标 URL',
    cover_url       VARCHAR(1024)   DEFAULT NULL COMMENT '空间封面 URL',
    owner_id        VARCHAR(64)     NOT NULL COMMENT '空间所有者（创建者）',
    status          VARCHAR(32)     NOT NULL DEFAULT 'active' COMMENT '空间状态：active / archived / deleted',
    visibility      VARCHAR(32)     NOT NULL DEFAULT 'private' COMMENT '可见性：private / organization / public',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序序号',
    member_count    INT             NOT NULL DEFAULT 1 COMMENT '成员数量',
    node_count      INT             NOT NULL DEFAULT 0 COMMENT '节点数量（文件/目录总数）',
    quota_limit     BIGINT          DEFAULT NULL COMMENT '空间独立配额（字节，NULL 表示使用租户配额）',
    quota_used      BIGINT          NOT NULL DEFAULT 0 COMMENT '已使用配额（字节）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    deleted_time    DATETIME        DEFAULT NULL COMMENT '删除时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ydsz_wiki_space_tenant_name (tenant_id, name),
    INDEX idx_ydsz_wiki_space_tenant_sort (tenant_id, sort_order),
    INDEX idx_ydsz_wiki_space_owner (owner_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库空间（空间管理聚合根，文件节点的顶级容器）';

-- ----------------------------------------------------------------------------
-- 12. 空间成员表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_space_member (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    space_id        VARCHAR(32)     NOT NULL COMMENT '空间ID',
    user_id         VARCHAR(64)     NOT NULL COMMENT '用户ID',
    role            VARCHAR(32)     NOT NULL COMMENT '角色：owner / admin / editor / viewer',
    joined_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ydsz_wiki_space_member_space_user (space_id, user_id),
    INDEX idx_ydsz_wiki_space_member_space_role (space_id, role),
    INDEX idx_ydsz_wiki_space_member_user (user_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空间成员（记录用户与空间的归属关系及角色）';

-- ----------------------------------------------------------------------------
-- 13. 空间模板表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_space_template (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     DEFAULT NULL COMMENT '租户 ID（系统模板为 NULL）',
    name            VARCHAR(128)    NOT NULL COMMENT '模板名称',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '模板描述',
    category        VARCHAR(32)     NOT NULL DEFAULT 'general' COMMENT '模板分类：general / project / meeting / knowledge',
    icon_url        VARCHAR(1024)   DEFAULT NULL COMMENT '模板图标 URL',
    is_system       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否为系统内置模板（不可删除）',
    is_public       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否公开（所有租户可见）',
    structure_json  JSON            NOT NULL COMMENT '模板结构 JSON（定义目录树、初始页面、权限配置等）',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序序号',
    usage_count     INT             NOT NULL DEFAULT 0 COMMENT '使用次数',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    INDEX idx_ydsz_wiki_space_template_tenant_category (tenant_id, category),
    INDEX idx_ydsz_wiki_space_template_system (is_system, is_public),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空间模板（预定义可复用的空间结构模板）';

-- ----------------------------------------------------------------------------
-- 14. 回收站条目表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_trash_item (
    id                  VARCHAR(32)   NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)   NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    file_node_id        VARCHAR(32)   NOT NULL COMMENT '原文件节点ID',
    original_name       VARCHAR(255)  NOT NULL COMMENT '原文件名',
    original_path       VARCHAR(1024) DEFAULT NULL COMMENT '原始路径',
    original_parent_id  VARCHAR(32)   DEFAULT NULL COMMENT '原始父节点ID',
    node_type           VARCHAR(32)   NOT NULL COMMENT '节点类型：folder / file',
    size                BIGINT        NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    deleted_time        DATETIME      NOT NULL COMMENT '删除时间',
    purge_time          DATETIME      NOT NULL COMMENT '预计永久删除时间',
    status              VARCHAR(32)   NOT NULL DEFAULT 'in_trash' COMMENT '状态：in_trash / restored / purged',
    deleted             TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by          VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    updated_by          VARCHAR(64)   DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    INDEX idx_file_node_id (file_node_id),
    INDEX idx_deleted_time (deleted_time),
    INDEX idx_purge_time (purge_time),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回收站条目（记录被逻辑删除的文件/文件夹，支持恢复和自动清理）';

-- ----------------------------------------------------------------------------
-- 15. 文件搜索索引表（ES 不可用时的数据库 fallback）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_search_index (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    file_node_id    VARCHAR(32)     NOT NULL COMMENT '关联的文件节点ID',
    name            VARCHAR(255)    NOT NULL COMMENT '文件名（用于搜索）',
    path            VARCHAR(1024)   DEFAULT NULL COMMENT '目录路径',
    content         LONGTEXT        COMMENT '索引内容（文件名 + 路径 + 提取的文本）',
    suffix          VARCHAR(64)     DEFAULT NULL COMMENT '文件后缀',
    mime_type       VARCHAR(128)    DEFAULT NULL COMMENT 'MIME 类型',
    size            BIGINT          NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    tags            VARCHAR(512)    DEFAULT NULL COMMENT '标签（逗号分隔）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_node_id (file_node_id),
    FULLTEXT INDEX ft_search_name_content (name, content),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件搜索索引（数据库 fallback 搜索，ES 不可用时提供文件名/路径/内容搜索）';

-- ----------------------------------------------------------------------------
-- 16. 用户收藏夹表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_user_favorite (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    user_id         VARCHAR(64)     NOT NULL COMMENT '用户ID',
    node_id         VARCHAR(64)     NOT NULL COMMENT '收藏的文件/目录节点ID',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序序号（值越小越靠前）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    deleted_time    DATETIME        DEFAULT NULL COMMENT '删除时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ydsz_wiki_user_favorite_user_node (user_id, node_id),
    INDEX idx_ydsz_wiki_user_favorite_user_sort (user_id, sort_order),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏夹（记录用户收藏的文件/目录节点，支持排序与软删除）';

-- ----------------------------------------------------------------------------
-- 17. 用户最近访问表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_user_recent (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    user_id         VARCHAR(64)     NOT NULL COMMENT '用户ID',
    node_id         VARCHAR(64)     NOT NULL COMMENT '访问的文件/目录节点ID',
    access_type     VARCHAR(32)     NOT NULL DEFAULT 'view' COMMENT '访问类型：view / edit / download',
    accessed_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近访问时间（排序字段）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ydsz_wiki_user_recent_user_node (user_id, node_id),
    INDEX idx_ydsz_wiki_user_recent_user_accessed (user_id, accessed_at),
    INDEX idx_ydsz_wiki_user_recent_access_type (user_id, access_type),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户最近访问记录（同一节点只保留一条，支持按访问时间倒序查询）';

-- ----------------------------------------------------------------------------
-- 18. 存储配额表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_wiki_storage_quota (
    id              VARCHAR(32)     NOT NULL COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    scope_type      VARCHAR(32)     NOT NULL COMMENT '配额维度：user / tenant / project',
    scope_id        VARCHAR(64)     NOT NULL COMMENT '维度ID（用户ID / 租户ID / 项目ID）',
    quota_limit     BIGINT          NOT NULL DEFAULT 0 COMMENT '配额上限（字节）',
    quota_used      BIGINT          NOT NULL DEFAULT 0 COMMENT '已使用量（字节）',
    file_count_limit INT            DEFAULT NULL COMMENT '文件数量上限',
    file_count_used INT             NOT NULL DEFAULT 0 COMMENT '已使用文件数量',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope (scope_type, scope_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存储配额（按用户/租户/项目维度设置存储上限，上传时校验配额）';
