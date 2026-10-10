-- =====================================================================
--  ydsz-message 集成测试 Schema（最小化建表，仅覆盖 Repository 集成测试所需）
-- =====================================================================

DROP TABLE IF EXISTS ydsz_msg_template CASCADE;

CREATE TABLE ydsz_msg_template (
  id                VARCHAR(36)   PRIMARY KEY,
  template_code     VARCHAR(64)   NOT NULL,
  channel           VARCHAR(32)   NOT NULL,
  locale            VARCHAR(16)   DEFAULT 'zh-CN',
  version           VARCHAR(32)   NOT NULL DEFAULT '1',
  category          VARCHAR(64)   DEFAULT NULL,
  scene_code        VARCHAR(64)   DEFAULT NULL,
  subject           VARCHAR(255)  DEFAULT NULL,
  content           TEXT          NOT NULL,
  provider          VARCHAR(64)   DEFAULT NULL,
  provider_key      VARCHAR(128)  DEFAULT NULL,
  sign_name         VARCHAR(128)  DEFAULT NULL,
  status            VARCHAR(32)   NOT NULL DEFAULT 'DISABLED',
  audit_status      VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
  audit_by          VARCHAR(64)   DEFAULT NULL,
  audit_at          TIMESTAMP     DEFAULT NULL,
  audit_remark      VARCHAR(512)  DEFAULT NULL,
  description       VARCHAR(512)  DEFAULT NULL,
  variable_defs     JSONB         DEFAULT NULL,
  sort              INTEGER       DEFAULT 0,
  revision          INTEGER       DEFAULT 0,
  tenant_id         VARCHAR(36)   NOT NULL DEFAULT '0',
  is_deleted        SMALLINT      NOT NULL DEFAULT 0,
  created_by        VARCHAR(36)   DEFAULT NULL,
  created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by        VARCHAR(36)   DEFAULT NULL,
  updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_ydsz_msg_template_template_code UNIQUE (template_code, tenant_id)
);

CREATE INDEX idx_ydsz_msg_template_tenant_is_deleted ON ydsz_msg_template (tenant_id, is_deleted);
