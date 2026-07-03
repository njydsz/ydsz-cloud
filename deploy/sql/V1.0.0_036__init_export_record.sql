-- ============================================================
-- V1.0.0_036  P2-11 异步导出记录表（下载中心）
-- ============================================================
-- 说明：异步导出任务记录表，支持大文件后台生成 + 下载中心轮询。
--   状态流转：PENDING → GENERATING → COMPLETED / FAILED / EXPIRED
--   文件上传 MinIO 后回写 file_url，过期自动清理。
-- 注意：版本号 033/034 已被流程引擎占用，本表使用 036。
-- ============================================================

-- 异步导出记录表
CREATE TABLE IF NOT EXISTS pmis_export_record (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    export_type     VARCHAR(50)  NOT NULL,
    file_name       VARCHAR(500),
    file_key        VARCHAR(500),
    file_url        VARCHAR(1000),
    file_size       BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    params          TEXT,
    error_message   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,
    expired_at      TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_export_record IS '异步导出记录表';
COMMENT ON COLUMN pmis_export_record.user_id IS '发起导出的用户ID';
COMMENT ON COLUMN pmis_export_record.export_type IS '导出类型 (PROJECT/CONTRACT/INVOICE/PAYMENT/EVM/AUDIT_LOG等)';
COMMENT ON COLUMN pmis_export_record.file_name IS '文件名';
COMMENT ON COLUMN pmis_export_record.file_key IS 'MinIO 文件 key';
COMMENT ON COLUMN pmis_export_record.file_url IS '下载 URL';
COMMENT ON COLUMN pmis_export_record.file_size IS '文件大小（字节）';
COMMENT ON COLUMN pmis_export_record.status IS '状态 (PENDING/GENERATING/COMPLETED/FAILED/EXPIRED)';
COMMENT ON COLUMN pmis_export_record.params IS '导出参数（JSON）';
COMMENT ON COLUMN pmis_export_record.error_message IS '错误信息';
COMMENT ON COLUMN pmis_export_record.completed_at IS '完成时间';
COMMENT ON COLUMN pmis_export_record.expired_at IS '过期时间';

CREATE INDEX idx_export_user ON pmis_export_record (user_id) WHERE deleted = 0;
CREATE INDEX idx_export_status ON pmis_export_record (status) WHERE completed_at IS NULL;
