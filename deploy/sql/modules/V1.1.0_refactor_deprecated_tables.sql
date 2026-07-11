-- ============================================================
-- PMIS V1.1.0 架构优化 — 废弃重复表清理
-- 本脚本标记并迁移以下重复表：
--   1. pmis_flow_notify_template      → 数据迁移到 pmis_msg_template
--   2. pmis_flow_notify_channel       → 功能由 pmis_msg_route_rule 替代
--   3. pmis_flow_notify_preference    → 功能由 pmis_msg_preference 替代
--   4. pmis_flow_notify_outbox        → 保留(Outbox Pattern 独有价值)
--   5. pmis_flow_webhook_subscription → 功能由 message 模块 Webhook 通道替代
--
-- 安全策略：不直接 DROP，先标记 deprecated=1，由运维确认后手动清理。
-- ============================================================

-- ============================================================
-- 1. 迁移 pmis_flow_notify_template 种子数据到 pmis_msg_template
-- ============================================================
-- 工作流通知模板已由 message 模块的 pmis_msg_template 统一管理
-- 将 workflow 专用模板以 FLOW_ 前缀迁入 pmis_msg_template

INSERT INTO pmis_msg_template (template_code, channel, locale, subject, content, status, description, tenant_id)
SELECT
    'FLOW_' || fnt.template_code,
    CASE fnt.channel
        WHEN 'INAPP' THEN 'INAPP'
        WHEN 'EMAIL' THEN 'EMAIL'
        WHEN 'SMS' THEN 'SMS'
        WHEN 'WEBHOOK' THEN 'WEBHOOK'
        WHEN 'DINGTALK' THEN 'DINGTALK'
        WHEN 'FEISHU' THEN 'FEISHU'
        WHEN 'WECOM' THEN 'WECOM'
        ELSE 'INAPP'
    END,
    CASE fnt.locale
        WHEN 'zh_CN' THEN 'zh-CN'
        WHEN 'en_US' THEN 'en-US'
        ELSE 'zh-CN'
    END,
    fnt.title,
    fnt.content,
    CASE fnt.enabled WHEN 1 THEN 'ENABLED' ELSE 'DISABLED' END,
    COALESCE(fnt.description, '迁移自 pmis_flow_notify_template: ' || fnt.template_code),
    COALESCE(fnt.tenant_id, '1')
FROM pmis_flow_notify_template fnt
WHERE fnt.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM pmis_msg_template pmt
      WHERE pmt.template_code = 'FLOW_' || fnt.template_code
        AND pmt.channel = CASE fnt.channel
              WHEN 'INAPP' THEN 'INAPP'
              WHEN 'EMAIL' THEN 'EMAIL'
              WHEN 'SMS' THEN 'SMS'
              WHEN 'WEBHOOK' THEN 'WEBHOOK'
              WHEN 'DINGTALK' THEN 'DINGTALK'
              WHEN 'FEISHU' THEN 'FEISHU'
              WHEN 'WECOM' THEN 'WECOM'
              ELSE 'INAPP'
            END
        AND pmt.locale = CASE fnt.locale
              WHEN 'zh_CN' THEN 'zh-CN'
              WHEN 'en_US' THEN 'en-US'
              ELSE 'zh-CN'
            END
  );

-- ============================================================
-- 2. 标记废弃表（不 DROP，仅添加注释标记）
-- ============================================================
COMMENT ON TABLE pmis_flow_notify_template IS 'DEPRECATED V1.1.0: 已迁移到 pmis_msg_template，数据由 message 模块统一管理';
COMMENT ON TABLE pmis_flow_notify_channel IS 'DEPRECATED V1.1.0: 功能由 pmis_msg_route_rule + message 模块通道路由替代';
COMMENT ON TABLE pmis_flow_notify_preference IS 'DEPRECATED V1.1.0: 功能由 pmis_msg_preference 统一管理';
COMMENT ON TABLE pmis_flow_webhook_subscription IS 'DEPRECATED V1.1.0: 功能由 message 模块 WebhookChannel 替代';

-- ============================================================
-- 3. 迁移 pmis_flow_notify_preference 数据到 pmis_msg_preference
-- ============================================================
INSERT INTO pmis_msg_preference (user_id, channel, biz_type, enabled, dnd_enabled, dnd_start, dnd_end, digest_enabled, tenant_id)
SELECT
    fnp.user_id,
    'INAPP',
    '__DEFAULT__',
    1,
    CASE WHEN fnp.quiet_hours_start IS NOT NULL THEN 1 ELSE 0 END,
    fnp.quiet_hours_start,
    fnp.quiet_hours_end,
    fnp.digest_mode,
    COALESCE(fnp.tenant_id, '1')
FROM pmis_flow_notify_preference fnp
WHERE fnp.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM pmis_msg_preference pmp
      WHERE pmp.user_id = fnp.user_id
        AND pmp.channel = 'INAPP'
        AND pmp.biz_type = '__DEFAULT__'
  );

-- ============================================================
-- 4. 迁移 pmis_flow_webhook_subscription 数据到 pmis_msg_route_rule
-- ============================================================
INSERT INTO pmis_msg_route_rule (rule_code, rule_name, biz_type, channel, priority, condition_expr, target_channel, status, description, tenant_id)
SELECT
    'FLOW_WEBHOOK_' || sub.name,
    '工作流 Webhook: ' || sub.name,
    'WORKFLOW',
    'WEBHOOK',
    100,
    '#request.bizType matches ''WORKFLOW.*''',
    'WEBHOOK',
    CASE sub.enabled WHEN 1 THEN 'ENABLED' ELSE 'DISABLED' END,
    COALESCE(sub.description, '迁移自 pmis_flow_webhook_subscription'),
    COALESCE(sub.tenant_id, '1')
FROM pmis_flow_webhook_subscription sub
WHERE sub.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM pmis_msg_route_rule r
      WHERE r.rule_code = 'FLOW_WEBHOOK_' || sub.name
  );

-- ============================================================
-- 5. 保留 pmis_flow_notify_outbox（Outbox Pattern 有独立架构价值）
--    但添加注释说明通知投递应通过 NotificationClient Feign 调用
-- ============================================================
COMMENT ON TABLE pmis_flow_notify_outbox IS '工作流通知外发箱（Outbox Pattern）— 保留：保证业务事务与消息投递的最终一致性。投递时通过 NotificationClient Feign 调用 message 模块';

-- ============================================================
-- 6. 验证迁移结果
-- ============================================================
-- 检查模板迁移数量
SELECT 'flow_notify_template_migrated' AS check_name,
       COUNT(*) AS count
FROM pmis_msg_template
WHERE template_code LIKE 'FLOW_%';

-- 检查偏好迁移数量
SELECT 'flow_preference_migrated' AS check_name,
       COUNT(*) AS count
FROM pmis_msg_preference
WHERE biz_type = '__DEFAULT__' AND channel = 'INAPP';
