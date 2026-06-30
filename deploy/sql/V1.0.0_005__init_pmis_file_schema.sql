-- =====================================================
-- PMIS 文件存储模块 DDL
-- 版本: V1.0.0_005
-- 描述: 文件元信息表
-- =====================================================

DROP TABLE IF EXISTS pmis_file;
CREATE TABLE pmis_file (
    id              BIGSERIAL PRIMARY KEY,
    file_name       VARCHAR(256) NOT NULL,
    original_name   VARCHAR(256) NOT NULL,
    file_path       VARCHAR(512) NOT NULL,
    bucket          VARCHAR(64)  NOT NULL,
    content_type    VARCHAR(128),
    file_size       BIGINT       NOT NULL DEFAULT 0,
    file_hash       VARCHAR(128),
    biz_type        VARCHAR(64),
    biz_id          VARCHAR(64),
    storage_type    VARCHAR(32)  NOT NULL DEFAULT 'MINIO',
    access_url      VARCHAR(1024),
    url_expire_at   TIMESTAMP,
    uploader_id     BIGINT,
    uploader_name   VARCHAR(64),
    tenant_id       BIGINT       DEFAULT 1,
    description     VARCHAR(512),
    create_by       BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_file IS '文件元信息表';
COMMENT ON COLUMN pmis_file.file_name IS '存储的文件名(系统生成)';
COMMENT ON COLUMN pmis_file.original_name IS '原始文件名';
COMMENT ON COLUMN pmis_file.file_path IS '对象 key / 存储路径';
COMMENT ON COLUMN pmis_file.bucket IS '存储桶';
COMMENT ON COLUMN pmis_file.content_type IS 'MIME 类型';
COMMENT ON COLUMN pmis_file.file_size IS '文件大小(字节)';
COMMENT ON COLUMN pmis_file.file_hash IS '文件 SHA-256';
COMMENT ON COLUMN pmis_file.biz_type IS '业务类型';
COMMENT ON COLUMN pmis_file.biz_id IS '业务单据 ID';
COMMENT ON COLUMN pmis_file.storage_type IS '存储类型: MINIO/LOCAL/OSS';
COMMENT ON COLUMN pmis_file.access_url IS '访问 URL';
COMMENT ON COLUMN pmis_file.url_expire_at IS 'URL 过期时间';
COMMENT ON COLUMN pmis_file.uploader_id IS '上传人 ID';
COMMENT ON COLUMN pmis_file.uploader_name IS '上传人姓名';
COMMENT ON COLUMN pmis_file.tenant_id IS '租户 ID';

CREATE INDEX idx_file_biz ON pmis_file(biz_type, biz_id);
CREATE INDEX idx_file_hash ON pmis_file(file_hash);
CREATE INDEX idx_file_uploader ON pmis_file(uploader_id);
CREATE INDEX idx_file_tenant ON pmis_file(tenant_id);
CREATE INDEX idx_file_bucket ON pmis_file(bucket);
