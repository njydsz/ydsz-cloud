-- ============================================================
-- V1.0.0_018  智能化升级 P4-1/P4-2/P4-3  脚本
-- ============================================================
-- 说明：批次 15 智能化升级-系统内部数据管理（PRD 4.2）
-- 1) 工时表新增 billable 字段（可计费标识）
-- 2) 预警分级推送表 pmis_alert_dispatch
-- 3) 每日对账表 pmis_reconcile_daily
-- ============================================================

-- ----------------------------
-- 1) 工时表新增 billable 字段
-- ----------------------------
ALTER TABLE pmis_execution_time_entry
    ADD COLUMN IF NOT EXISTS billable SMALLINT NOT NULL DEFAULT 1;
COMMENT ON COLUMN pmis_execution_time_entry.billable IS '可计费标识: 1=可计费（计入 BillableUtilization）,0=非计费';

-- ----------------------------
-- 2) 预警分级推送表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_alert_dispatch (
    id                  BIGSERIAL PRIMARY KEY,
    alert_code          VARCHAR(64)  NOT NULL UNIQUE,
    alert_type          VARCHAR(32)  NOT NULL,
    alert_level         VARCHAR(8)   NOT NULL,
    source_type         VARCHAR(32)  NOT NULL,
    source_id           VARCHAR(64),
    title               VARCHAR(256) NOT NULL,
    content             TEXT,
    target_role         VARCHAR(64)  NOT NULL,
    target_user_ids     VARCHAR(1024),
    push_channels       VARCHAR(64)  NOT NULL DEFAULT 'IN_APP',
    dispatched_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_by       VARCHAR(64),
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    sent_at             TIMESTAMP,
    fail_reason         VARCHAR(512),
    retry_count         INT          NOT NULL DEFAULT 0,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE  pmis_alert_dispatch IS '预警分级推送表: 黄/红不同层级触达,失败自动重试（最大 3 次）';
COMMENT ON COLUMN pmis_alert_dispatch.alert_code IS '预警编码: 业务唯一,如 ALERT-2026-001';
COMMENT ON COLUMN pmis_alert_dispatch.alert_type IS '预警类型: BUDGET 预算 / EVM 挣值 / SLA 工单 / RISK 风险 / PROFIT 利润';
COMMENT ON COLUMN pmis_alert_dispatch.alert_level IS '预警等级: YELLOW 黄色 / RED 红色';
COMMENT ON COLUMN pmis_alert_dispatch.source_type IS '触发源类型: PROJECT/EVM/TICKET 等';
COMMENT ON COLUMN pmis_alert_dispatch.source_id IS '触发源业务 ID';
COMMENT ON COLUMN pmis_alert_dispatch.title IS '预警标题';
COMMENT ON COLUMN pmis_alert_dispatch.content IS '预警内容（已渲染的模板）';
COMMENT ON COLUMN pmis_alert_dispatch.target_role IS '目标角色: PM/PMO/CFO 等';
COMMENT ON COLUMN pmis_alert_dispatch.target_user_ids IS '目标用户 ID 列表: 逗号分隔,精确触达';
COMMENT ON COLUMN pmis_alert_dispatch.push_channels IS '推送渠道: IN_APP 站内信 / EMAIL 邮件 / SMS 短信 / WECHAT 微信,逗号分隔';
COMMENT ON COLUMN pmis_alert_dispatch.dispatched_at IS '派发时间';
COMMENT ON COLUMN pmis_alert_dispatch.dispatched_by IS '派发人: 定时任务 / 系统 / 用户';
COMMENT ON COLUMN pmis_alert_dispatch.status IS '发送状态: PENDING 待发送 / SENT 已发送 / FAILED 失败 / RETRYING 重试中';
COMMENT ON COLUMN pmis_alert_dispatch.sent_at IS '发送成功时间';
COMMENT ON COLUMN pmis_alert_dispatch.fail_reason IS '失败原因';
COMMENT ON COLUMN pmis_alert_dispatch.retry_count IS '重试次数: 最大 3 次';
COMMENT ON COLUMN pmis_alert_dispatch.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_alert_dispatch.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_alert_dispatch.deleted IS '逻辑删除: 0=未删除,1=已删除';

CREATE INDEX IF NOT EXISTS idx_alert_dispatch_level   ON pmis_alert_dispatch(alert_level, deleted);
CREATE INDEX IF NOT EXISTS idx_alert_dispatch_type    ON pmis_alert_dispatch(alert_type, deleted);
CREATE INDEX IF NOT EXISTS idx_alert_dispatch_status  ON pmis_alert_dispatch(status, deleted);
CREATE INDEX IF NOT EXISTS idx_alert_dispatch_source  ON pmis_alert_dispatch(source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_alert_dispatch_target  ON pmis_alert_dispatch(target_role, deleted);

-- ----------------------------
-- 3) 每日对账表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_reconcile_daily (
    id                  BIGSERIAL PRIMARY KEY,
    reconcile_date      DATE         NOT NULL,
    reconcile_type      VARCHAR(32)  NOT NULL,
    initiation_id       BIGINT,
    expected_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    actual_amount       NUMERIC(18,2) NOT NULL DEFAULT 0,
    diff_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    diff_pct            NUMERIC(8,4) NOT NULL DEFAULT 0,
    status              VARCHAR(16)  NOT NULL DEFAULT 'OK',
    detail              TEXT,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE  pmis_reconcile_daily IS '每日自动对账表: 成本/收入/回款/开票 跨模块校验,ReconcileServiceImpl 执行';
COMMENT ON COLUMN pmis_reconcile_daily.reconcile_date IS '对账日期: 每日 02:00 触发';
COMMENT ON COLUMN pmis_reconcile_daily.reconcile_type IS '对账类型: COST 成本 / REVENUE 收入 / PAYMENT 回款 / INVOICE 开票 / TIMESHEET 工时 / PROFIT 利润';
COMMENT ON COLUMN pmis_reconcile_daily.initiation_id IS '所属立项 ID: 可空,NULL 表示全局维度';
COMMENT ON COLUMN pmis_reconcile_daily.expected_amount IS '应计金额(元)';
COMMENT ON COLUMN pmis_reconcile_daily.actual_amount IS '实计金额(元)';
COMMENT ON COLUMN pmis_reconcile_daily.diff_amount IS '差异金额(元) = actual - expected';
COMMENT ON COLUMN pmis_reconcile_daily.diff_pct IS '差异比例: 0.05=5%';
COMMENT ON COLUMN pmis_reconcile_daily.status IS '对账状态: OK 一致 / WARN 警告（diff_pct < 5%）/ FAIL 失败（diff_pct >= 5%）';
COMMENT ON COLUMN pmis_reconcile_daily.detail IS '对账明细 JSON: 列出差异项';
COMMENT ON COLUMN pmis_reconcile_daily.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_reconcile_daily.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_reconcile_daily.deleted IS '逻辑删除: 0=未删除,1=已删除';

CREATE INDEX IF NOT EXISTS idx_reconcile_daily_date    ON pmis_reconcile_daily(reconcile_date, deleted);
CREATE INDEX IF NOT EXISTS idx_reconcile_daily_type    ON pmis_reconcile_daily(reconcile_type, deleted);
CREATE INDEX IF NOT EXISTS idx_reconcile_daily_init    ON pmis_reconcile_daily(initiation_id, deleted);
CREATE INDEX IF NOT EXISTS idx_reconcile_daily_status  ON pmis_reconcile_daily(status, deleted);

-- 唯一约束：每天每个维度只能有一条
CREATE UNIQUE INDEX IF NOT EXISTS uk_reconcile_daily
    ON pmis_reconcile_daily(reconcile_date, reconcile_type, COALESCE(initiation_id, 0), deleted);
