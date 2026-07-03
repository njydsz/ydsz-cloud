-- ============================================================
-- V1.0.0_022  智能化升级 P5  消息模板（预警中心）
-- ============================================================
-- 说明：批次 16 智能化升级-预警分级推送消息模板
--   模板命名规范: ALERT_<TYPE>_<LEVEL>  e.g. ALERT_BUDGET_YELLOW
--   占位符使用 ${var} 语法
-- ============================================================

-- 预算黄色预警
INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, create_time, update_time, deleted)
SELECT 'ALERT_BUDGET_YELLOW', 'IN_APP',
       '【预算黄色预警】${projectName}',
       '项目[${projectCode}] ${bizType}本次新增 ${delta} 元，累计已发生 ${usedAfter} 元 / 预算 ${budget} 元，使用率 ${ratio}%',
       'IN_APP', 'PMIS', 'ENABLED', '预算黄色预警(80%)', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_BUDGET_YELLOW' AND channel = 'IN_APP');

INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, create_time, update_time, deleted)
SELECT 'ALERT_BUDGET_YELLOW', 'EMAIL',
       '【预算黄色预警】${projectName}',
       '<p>项目[${projectCode}] ${bizType}本次新增 <b>${delta} 元</b>，累计已发生 <b>${usedAfter} 元</b> / 预算 <b>${budget} 元</b>，使用率 <b>${ratio}%</b>，已触及黄色阈值(80%)。</p>',
       'EMAIL', 'PMIS', 'ENABLED', '预算黄色预警邮件', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_BUDGET_YELLOW' AND channel = 'EMAIL');

-- 预算红色预警
INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, create_time, update_time, deleted)
SELECT 'ALERT_BUDGET_RED', 'IN_APP',
       '【预算红色预警】${projectName}',
       '项目[${projectCode}] ${bizType}本次新增 ${delta} 元，累计已发生 ${usedAfter} 元 / 预算 ${budget} 元，使用率 ${ratio}%，已触及红色阈值(95%)，请立即关注',
       'IN_APP', 'PMIS', 'ENABLED', '预算红色预警(95%)', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_BUDGET_RED' AND channel = 'IN_APP');

INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, create_time, update_time, deleted)
SELECT 'ALERT_BUDGET_RED', 'EMAIL',
       '【预算红色预警】${projectName}',
       '<p>项目[${projectCode}] ${bizType}本次新增 <b>${delta} 元</b>，累计已发生 <b>${usedAfter} 元</b> / 预算 <b>${budget} 元</b>，使用率 <b>${ratio}%</b>，已触及红色阈值(95%)，请立即关注。</p>',
       'EMAIL', 'PMIS', 'ENABLED', '预算红色预警邮件', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_BUDGET_RED' AND channel = 'EMAIL');

-- EVM 红色预警
INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, create_time, update_time, deleted)
SELECT 'ALERT_EVM_RED', 'IN_APP',
       '【EVM 红色预警】${title}',
       '${content}',
       'IN_APP', 'PMIS', 'ENABLED', 'EVM 红色预警(CPI<0.85 或 SPI<0.85)', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_EVM_RED' AND channel = 'IN_APP');

INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, create_time, update_time, deleted)
SELECT 'ALERT_EVM_RED', 'EMAIL',
       '【EVM 红色预警】${title}',
       '<p>${content}</p>',
       'EMAIL', 'PMIS', 'ENABLED', 'EVM 红色预警邮件', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_EVM_RED' AND channel = 'EMAIL');

-- SLA 红色预警（工单超时）
INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, create_time, update_time, deleted)
SELECT 'ALERT_SLA_RED', 'IN_APP',
       '【SLA 红色预警】工单 ${alertCode} 超时',
       '${content}',
       'IN_APP', 'PMIS', 'ENABLED', '运维工单 SLA 超时红色预警', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_SLA_RED' AND channel = 'IN_APP');

-- 通用黄色预警兜底
INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, create_time, update_time, deleted)
SELECT 'ALERT_OTHER_YELLOW', 'IN_APP',
       '【黄色预警】${title}',
       '${content}',
       'IN_APP', 'PMIS', 'ENABLED', '黄色预警通用兜底模板', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_OTHER_YELLOW' AND channel = 'IN_APP');
