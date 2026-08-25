-- ============================================================
-- V5__user_favorite_recent.sql
-- 用户收藏夹 & 最近访问表（S2-P1-06）
-- ============================================================

-- 用户收藏夹表（P1-5：快捷访问入口）
CREATE TABLE IF NOT EXISTS ydsz_user_favorite (
    id              VARCHAR(32)     PRIMARY KEY,
    user_id         VARCHAR(64)     NOT NULL,
    node_id         VARCHAR(64)     NOT NULL,
    tenant_id       VARCHAR(64)     NOT NULL,
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(64)     NOT NULL,
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64)     NOT NULL,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_time    TIMESTAMP       DEFAULT NULL
);

COMMENT ON TABLE ydsz_user_favorite IS '用户收藏夹（P1-5：快捷访问入口）';
COMMENT ON COLUMN ydsz_user_favorite.node_id IS '收藏的文件/目录节点ID';
COMMENT ON COLUMN ydsz_user_favorite.sort_order IS '排序序号（值越小越靠前）';

-- 唯一索引：同一节点不重复收藏
CREATE UNIQUE INDEX uk_ydsz_user_favorite_user_node
    ON ydsz_user_favorite (user_id, node_id)
    WHERE deleted = FALSE;

-- 查询索引：按用户查收藏列表（排序）
CREATE INDEX idx_ydsz_user_favorite_user_sort
    ON ydsz_user_favorite (user_id, sort_order)
    WHERE deleted = FALSE;

-- 租户隔离索引
CREATE INDEX idx_ydsz_user_favorite_tenant
    ON ydsz_user_favorite (tenant_id)
    WHERE deleted = FALSE;

-- 用户最近访问表（P1-5：快捷访问入口）
CREATE TABLE IF NOT EXISTS ydsz_user_recent (
    id              VARCHAR(32)     PRIMARY KEY,
    user_id         VARCHAR(64)     NOT NULL,
    node_id         VARCHAR(64)     NOT NULL,
    tenant_id       VARCHAR(64)     NOT NULL,
    access_type     VARCHAR(16)     NOT NULL DEFAULT 'view',  -- view / edit / download
    accessed_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE ydsz_user_recent IS '用户最近访问记录（P1-5：快捷访问入口）';
COMMENT ON COLUMN ydsz_user_recent.access_type IS '访问类型：view-查看 / edit-编辑 / download-下载';
COMMENT ON COLUMN ydsz_user_recent.accessed_at IS '最近访问时间（排序字段）';

-- 唯一索引：同一节点只保留最新访问记录（ON CONFLICT 更新）
CREATE UNIQUE INDEX uk_ydsz_user_recent_user_node
    ON ydsz_user_recent (user_id, node_id)
    WHERE deleted = FALSE;

-- 查询索引：按用户查最近访问列表（倒序）
CREATE INDEX idx_ydsz_user_recent_user_accessed
    ON ydsz_user_recent (user_id, accessed_at DESC)
    WHERE deleted = FALSE;

-- 租户隔离索引
CREATE INDEX idx_ydsz_user_recent_tenant
    ON ydsz_user_recent (tenant_id)
    WHERE deleted = FALSE;

-- 访问类型索引（统计各类型访问频次）
CREATE INDEX idx_ydsz_user_recent_access_type
    ON ydsz_user_recent (user_id, access_type)
    WHERE deleted = FALSE;
