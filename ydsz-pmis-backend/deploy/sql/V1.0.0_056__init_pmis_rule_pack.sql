-- 规则集（RulePack）持久化表（P2-14）
-- 规则集是一组相关规则的打包发布单元，用于市场分发、一键导入、版本化升级

CREATE TABLE IF NOT EXISTS pmis_rule_pack (
    id              BIGSERIAL PRIMARY KEY,
    pack_code       VARCHAR(128) NOT NULL,
    pack_version    VARCHAR(32)  NOT NULL,
    pack_name       VARCHAR(256) NOT NULL,
    industry        VARCHAR(64),
    tags            VARCHAR(512),  -- 逗号分隔
    rule_codes      TEXT          NOT NULL,  -- JSON 数组
    description     TEXT,
    author          VARCHAR(64),
    download_count  BIGINT        NOT NULL DEFAULT 0,
    rating          NUMERIC(3,2)  NOT NULL DEFAULT 0,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    official        BOOLEAN       NOT NULL DEFAULT FALSE,  -- 是否官方
    created_by      VARCHAR(64),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    UNIQUE (pack_code, pack_version)
);

CREATE INDEX IF NOT EXISTS idx_rule_pack_industry
    ON pmis_rule_pack (industry);
CREATE INDEX IF NOT EXISTS idx_rule_pack_official
    ON pmis_rule_pack (official);
CREATE INDEX IF NOT EXISTS idx_rule_pack_download
    ON pmis_rule_pack (download_count DESC);

COMMENT ON TABLE  pmis_rule_pack IS '规则集（RulePack）市场表（P2-14）';
COMMENT ON COLUMN pmis_rule_pack.pack_code IS '规则集编码';
COMMENT ON COLUMN pmis_rule_pack.pack_version IS '版本号（语义化版本 1.0.0）';
COMMENT ON COLUMN pmis_rule_pack.rule_codes IS '包含的规则编码 JSON 数组';
COMMENT ON COLUMN pmis_rule_pack.official IS '是否官方发布';
COMMENT ON COLUMN pmis_rule_pack.download_count IS '下载次数（市场热度）';

-- 规则集安装记录
CREATE TABLE IF NOT EXISTS pmis_rule_pack_install (
    id              BIGSERIAL PRIMARY KEY,
    pack_code       VARCHAR(128) NOT NULL,
    pack_version    VARCHAR(32)  NOT NULL,
    tenant_id       BIGINT,
    installed_by    VARCHAR(64),
    installed_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(32)  NOT NULL DEFAULT 'SUCCESS',  -- SUCCESS/FAILED/PARTIAL
    error_message   TEXT
);

CREATE INDEX IF NOT EXISTS idx_rule_pack_install_tenant
    ON pmis_rule_pack_install (tenant_id);
CREATE INDEX IF NOT EXISTS idx_rule_pack_install_code
    ON pmis_rule_pack_install (pack_code);

COMMENT ON TABLE pmis_rule_pack_install IS '规则集安装历史（P2-14）';
