#!/usr/bin/env python3
"""Inspect Mapper files to see what needs to be enhanced vs preserved.

For files that already have detailed method-level Javadoc, we only update the
class-level Javadoc block. For files with only minimal Javadoc, we can
replace the whole file.
"""
import pathlib
import re

MAPPER_FILES = [
    # (relative path, expected_entity_class)
    # ydsz-message
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/batch/MsgAggregateMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/batch/MsgBatchMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/canary/MsgCanaryMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/config/MsgFeedbackMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/config/MsgPreferenceMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/config/MsgRouteRuleMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/config/MsgSubscriptionMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/config/MsgTraceMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/config/MsgUserChannelMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/config/MsgVariableSourceMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/core/MsgLogMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/core/MsgNotificationMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/receipt/MsgReceiptMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/template/MsgTemplateMapper.java",
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/template/MsgTemplateVersionMapper.java",
    # ydsz-literule
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleDefinitionMapper.java",
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleVersionHistoryMapper.java",
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleVariableDefMapper.java",
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleTemplateMapper.java",
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleTestCaseMapper.java",
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RulePackMapper.java",
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RulePackInstallMapper.java",
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleExecutionTraceMapper.java",
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleDependencyMapper.java",
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleChainGraphMapper.java",
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleCanaryBucketMapper.java",
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleABPolicyMapper.java",
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleABRollbackMapper.java",
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/DecisionTableMapper.java",
    # ydsz-cronjob
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobWebhookMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobNodeMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobLogMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobLogContentMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobHistoryMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobDailyStatsMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobDagMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobDagVersionMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobDagInstanceMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobDagNodeInstanceMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobArtifactMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobAlertRuleMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobAlertLogMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/GlueCodeMapper.java",
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/TenantQuotaMapper.java",
    # ydsz-nextwiki
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/FileNodeMapper.java",
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/FileVersionMapper.java",
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/FileAclMapper.java",
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/ShareLinkMapper.java",
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/TagMapper.java",
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/StorageQuotaMapper.java",
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/TrashItemMapper.java",
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/SearchIndexMapper.java",
    # ydsz-agent
    "ydsz-agent/ydsz-agent-infra/src/main/java/com/njydsz/agent/infra/mapper/AgentDefinitionMapper.java",
    # ydsz-system (remaining)
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/DictTypeMapper.java",
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/DictVersionMapper.java",
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/TenantMapper.java",
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/TenantPlanMapper.java",
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/TenantPlanMenuMapper.java",
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/VariableMapper.java",
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/ConfigMapper.java",
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/AppInfoMapper.java",
]

# Manual entity package mapping (key = class name, value = full package)
ENTITY_PKG = {
    # cronjob (sub-packages based on package declarations)
    "Job": "com.njydsz.cronjob.domain.entity.job",
    "JobNode": "com.njydsz.cronjob.domain.entity.job",
    "JobTask": "com.njydsz.cronjob.domain.entity.job",
    "JobHistory": "com.njydsz.cronjob.domain.entity.job",
    "JobArtifact": "com.njydsz.cronjob.domain.entity.job",
    "JobAlertRule": "com.njydsz.cronjob.domain.entity.job",
    "JobAlertLog": "com.njydsz.cronjob.domain.entity.job",
    "JobWebhook": "com.njydsz.cronjob.domain.entity.job",
    "TenantQuota": "com.njydsz.cronjob.domain.entity.job",
    "JobDag": "com.njydsz.cronjob.domain.entity.dag",
    "JobDagVersion": "com.njydsz.cronjob.domain.entity.dag",
    "JobDagInstance": "com.njydsz.cronjob.domain.entity.dag",
    "JobDagNodeInstance": "com.njydsz.cronjob.domain.entity.dag",
    "JobLog": "com.njydsz.cronjob.domain.entity.log",
    "JobLogContent": "com.njydsz.cronjob.domain.entity.log",
    "JobDailyStats": "com.njydsz.cronjob.domain.entity.log",
    "GlueCode": "com.njydsz.cronjob.domain.entity.schedule",
    # literule (some have DO suffix - rule exception)
    "RuleDefinition": "com.njydsz.literule.domain.entity.RuleDefinitionDO",
    "RuleTestCase": "com.njydsz.literule.domain.entity.RuleTestCaseDO",
    "RuleExecutionTrace": "com.njydsz.literule.domain.entity.RuleExecutionTraceDO",
    "RulePack": "com.njydsz.literule.domain.entity.RulePackDO",
    "RuleChainGraph": "com.njydsz.literule.domain.entity.RuleChainGraphDO",
    "RuleVersionHistory": "com.njydsz.literule.domain.entity.RuleVersionHistory",
    "RuleVariableDef": "com.njydsz.literule.domain.entity.RuleVariableDef",
    "RuleTemplate": "com.njydsz.literule.domain.entity.RuleTemplate",
    "RulePackInstall": "com.njydsz.literule.domain.entity.RulePackInstall",
    "RuleDependency": "com.njydsz.literule.domain.entity.RuleDependency",
    "RuleCanaryBucket": "com.njydsz.literule.domain.entity.RuleCanaryBucket",
    "RuleABPolicy": "com.njydsz.literule.domain.entity.RuleABPolicy",
    "RuleABRollback": "com.njydsz.literule.domain.entity.RuleABRollback",
    "DecisionTable": "com.njydsz.literule.domain.entity.DecisionTable",
    # agent (DO suffix - rule exception)
    "AgentDefinition": "com.njydsz.agent.domain.entity.AgentDefinitionDO",
    # system
    "DictType": "com.njydsz.system.domain.entity.DictType",
    "DictItem": "com.njydsz.system.domain.entity.DictItem",
    "DictVersion": "com.njydsz.system.domain.entity.DictVersion",
    "Tenant": "com.njydsz.system.domain.entity.Tenant",
    "TenantPlan": "com.njydsz.system.domain.entity.TenantPlan",
    "TenantPlanMenu": "com.njydsz.system.domain.entity.TenantPlanMenu",
    "Variable": "com.njydsz.system.domain.entity.Variable",
    "Config": "com.njydsz.system.domain.entity.Config",
    "AppInfo": "com.njydsz.system.domain.entity.AppInfo",
    # message
    "MsgAggregate": "com.njydsz.message.domain.entity.batch.MsgAggregate",
    "MsgBatch": "com.njydsz.message.domain.entity.batch.MsgBatch",
    "MsgCanary": "com.njydsz.message.domain.entity.canary.MsgCanary",
    "MsgFeedback": "com.njydsz.message.domain.entity.config.MsgFeedback",
    "MsgPreference": "com.njydsz.message.domain.entity.config.MsgPreference",
    "MsgRouteRule": "com.njydsz.message.domain.entity.config.MsgRouteRule",
    "MsgSubscription": "com.njydsz.message.domain.entity.config.MsgSubscription",
    "MsgTrace": "com.njydsz.message.domain.entity.config.MsgTrace",
    "MsgUserChannel": "com.njydsz.message.domain.entity.config.MsgUserChannel",
    "MsgVariableSource": "com.njydsz.message.domain.entity.config.MsgVariableSource",
    "MsgOffline": "com.njydsz.message.domain.entity.config.MsgOffline",
    "MsgLog": "com.njydsz.message.domain.entity.core.MsgLog",
    "MsgNotification": "com.njydsz.message.domain.entity.core.MsgNotification",
    "MsgReceipt": "com.njydsz.message.domain.entity.receipt.MsgReceipt",
    "MsgTemplate": "com.njydsz.message.domain.entity.template.MsgTemplate",
    "MsgTemplateVersion": "com.njydsz.message.domain.entity.template.MsgTemplateVersion",
    # nextwiki
    "FileNode": "com.njydsz.nextwiki.domain.entity.FileNode",
    "FileVersion": "com.njydsz.nextwiki.domain.entity.FileVersion",
    "FileAcl": "com.njydsz.nextwiki.domain.entity.FileAcl",
    "ShareLink": "com.njydsz.nextwiki.domain.entity.ShareLink",
    "Tag": "com.njydsz.nextwiki.domain.entity.Tag",
    "StorageQuota": "com.njydsz.nextwiki.domain.entity.StorageQuota",
    "TrashItem": "com.njydsz.nextwiki.domain.entity.TrashItem",
    "SearchIndex": "com.njydsz.nextwiki.domain.entity.SearchIndex",
    # userinfo
    "UserAccount": "com.njydsz.userinfo.domain.entity.UserAccount",
    "Role": "com.njydsz.userinfo.domain.entity.Role",
    "Menu": "com.njydsz.userinfo.domain.entity.Menu",
    "Department": "com.njydsz.userinfo.domain.entity.Department",
    "Post": "com.njydsz.userinfo.domain.entity.Post",
    "Company": "com.njydsz.userinfo.domain.entity.Company",
    "Language": "com.njydsz.userinfo.domain.entity.Language",
    "UserRole": "com.njydsz.userinfo.domain.entity.UserRole",
    "UserDept": "com.njydsz.userinfo.domain.entity.UserDept",
    "UserPost": "com.njydsz.userinfo.domain.entity.UserPost",
    "UserField": "com.njydsz.userinfo.domain.entity.UserField",
    "RolePermission": "com.njydsz.userinfo.domain.entity.RolePermission",
    "CompanyDept": "com.njydsz.userinfo.domain.entity.CompanyDept",
    # workflow
    "FlowInstance": "com.njydsz.workflow.domain.entity.FlowInstance",
    "FlowDefinition": "com.njydsz.workflow.domain.entity.FlowDefinition",
    "FlowNode": "com.njydsz.workflow.domain.entity.FlowNode",
    "FlowHisInstance": "com.njydsz.workflow.domain.entity.FlowHisInstance",
    "FlowHisTask": "com.njydsz.workflow.domain.entity.FlowHisTask",
    "FlowRunTask": "com.njydsz.workflow.domain.entity.FlowRunTask",
    "FlowComment": "com.njydsz.workflow.domain.entity.FlowComment",
    "FlowAuditLog": "com.njydsz.workflow.domain.entity.FlowAuditLog",
    "FlowCategory": "com.njydsz.workflow.domain.entity.FlowCategory",
    "FlowTemplate": "com.njydsz.workflow.domain.entity.FlowTemplate",
    "FlowAttachment": "com.njydsz.workflow.domain.entity.FlowAttachment",
    "FlowCc": "com.njydsz.workflow.domain.entity.FlowCc",
    "FlowCcRule": "com.njydsz.workflow.domain.entity.FlowCcRule",
    "FlowDelegateAuth": "com.njydsz.workflow.domain.entity.FlowDelegateAuth",
    "FlowDmnDecision": "com.njydsz.workflow.domain.entity.FlowDmnDecision",
    "FlowDmnRule": "com.njydsz.workflow.domain.entity.FlowDmnRule",
    "FlowEventSubscription": "com.njydsz.workflow.domain.entity.FlowEventSubscription",
    "FlowQuickComment": "com.njydsz.workflow.domain.entity.FlowQuickComment",
    "FlowSkip": "com.njydsz.workflow.domain.entity.FlowSkip",
    "FlowTimer": "com.njydsz.workflow.domain.entity.FlowTimer",
    "FlowUser": "com.njydsz.workflow.domain.entity.FlowUser",
    "FlowAutoTrigger": "com.njydsz.workflow.domain.entity.FlowAutoTrigger",
    "FlowAdminRole": "com.njydsz.workflow.domain.entity.FlowAdminRole",
    "FlowThirdPartyAccount": "com.njydsz.workflow.domain.entity.FlowThirdPartyAccount",
    "FlowThirdPartyLog": "com.njydsz.workflow.domain.entity.FlowThirdPartyLog",
}

# Mapper files metadata for class-level Javadoc
# (relative_path -> dict)
MAPPERS = {
    # ===== ydsz-message (note: in flat mapper dir, not subdir) =====
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgAggregateMapper.java": {
        "title": "聚合批次 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_aggregate}，存储多消息聚合的批次信息。",
            "聚合批次是把同一业务事件的多条消息合并为 1 条聚合消息发送（避免对用户的骚扰），按业务键聚合。",
        ],
        "index": [
            "uk_agg_key — 聚合键唯一索引",
            "idx_agg_at — 聚合时间排序索引",
        ],
        "see": [
            "com.njydsz.message.domain.entity.batch.MsgAggregate 聚合批次实体",
            "com.njydsz.message.server.service.MsgAggregateService 聚合批次 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgBatchMapper.java": {
        "title": "消息批次 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_batch}，存储单次发送任务的批次信息。",
            "批次是消息发送的最小调度单位，1 个批次可能包含若干通知（{@code ydsz_msg_notification}），由调度器统一推送。",
        ],
        "index": [
            "uk_batch_no — 批次号唯一索引",
            "idx_status — 批次状态过滤索引",
        ],
        "see": [
            "com.njydsz.message.domain.entity.batch.MsgBatch 批次实体",
            "com.njydsz.message.server.service.MsgBatchService 批次 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgCanaryMapper.java": {
        "title": "消息灰度桶 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_canary}，存储消息模板/渠道的灰度发布规则。",
            "灰度桶定义按用户 ID 哈希/百分位的灰度受众，模板/渠道灰度发布时按用户命中桶决定是否启用。",
        ],
        "index": [
            "uk_canary_key — (模板/渠道+版本) 唯一索引",
            "idx_status — 状态过滤索引",
        ],
        "see": [
            "com.njydsz.message.domain.entity.canary.MsgCanary 灰度桶实体",
            "com.njydsz.message.server.service.MsgCanaryService 灰度桶 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgFeedbackMapper.java": {
        "title": "消息用户反馈 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_feedback}，存储用户对消息的反馈（有用/无用/投诉/退订）。",
            "用户反馈用于消息质量优化（取消订阅、调整推送频率、识别骚扰内容），同时作为渠道质量评分输入。",
        ],
        "index": [
            "idx_user_id — 用户维度查询索引",
            "idx_msg_id — 消息维度查询索引",
        ],
        "see": [
            "com.njydsz.message.domain.entity.config.MsgFeedback 反馈实体",
            "com.njydsz.message.server.service.MsgFeedbackService 反馈 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgPreferenceMapper.java": {
        "title": "用户消息偏好 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_preference}，存储用户消息偏好设置。",
            "用户偏好决定消息的渠道（站内/邮件/短信/IM）、时段（勿扰时段）、免打扰类型等，是消息中心的核心个性化数据。",
        ],
        "index": [
            "uk_user_id — 用户唯一索引（一个用户一份偏好）",
        ],
        "see": [
            "com.njydsz.message.domain.entity.config.MsgPreference 偏好实体",
            "com.njydsz.message.server.service.MsgPreferenceService 偏好 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgRouteRuleMapper.java": {
        "title": "消息路由规则 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_route_rule}，存储消息路由规则。",
            "路由规则按 (业务类型, 优先级, 渠道) 决定消息的发送渠道、模板、降级策略、限流配置。",
        ],
        "index": [
            "uk_biz_channel — (业务类型+渠道) 唯一索引",
            "idx_priority — 优先级排序索引",
        ],
        "see": [
            "com.njydsz.message.domain.entity.config.MsgRouteRule 路由规则实体",
            "com.njydsz.message.server.service.MsgRouteRuleService 路由规则 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgSubscriptionMapper.java": {
        "title": "消息订阅关系 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_subscription}，存储用户对业务类型/事件的订阅关系。",
            "订阅关系决定用户是否接收某类消息（OA 通知/系统公告/项目动态等），可由用户主动订阅或业务自动订阅。",
        ],
        "index": [
            "uk_user_event — (用户+事件类型) 唯一索引",
            "idx_biz_type — 业务类型过滤索引",
        ],
        "see": [
            "com.njydsz.message.domain.entity.config.MsgSubscription 订阅实体",
            "com.njydsz.message.server.service.MsgSubscriptionService 订阅 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgTraceMapper.java": {
        "title": "消息轨迹 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_trace}，存储消息生命周期各阶段的轨迹。",
            "轨迹按时间线记录消息的关键事件（创建/调度/发送/送达/已读/点击/失败/重试），用于消息全链路追踪与排查。",
        ],
        "index": [
            "idx_msg_id — 消息维度查询索引（按时间排序）",
            "idx_trace_at — 轨迹时间排序索引",
        ],
        "see": [
            "com.njydsz.message.domain.entity.config.MsgTrace 轨迹实体",
            "com.njydsz.message.server.service.MsgTraceService 轨迹 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgUserChannelMapper.java": {
        "title": "用户通道绑定 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_user_channel}，存储用户在各通道（IM/邮件/短信/站内）的地址/账号绑定。",
            "通道绑定是消息发送的最终地址（手机号/邮箱/IM openId 等），按渠道类型 + 用户 ID 唯一。",
        ],
        "index": [
            "uk_user_channel — (用户+渠道类型) 唯一索引",
            "idx_channel_account — 渠道账号查询索引（用于回执回调反查用户）",
        ],
        "see": [
            "com.njydsz.message.domain.entity.config.MsgUserChannel 通道绑定实体",
            "com.njydsz.message.server.service.MsgUserChannelService 通道绑定 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgVariableSourceMapper.java": {
        "title": "消息变量数据源 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_variable_source}，存储消息模板变量的数据源定义。",
            "数据源声明模板变量从哪个服务/接口/SQL 取值（动态变量），发送时由 {@code VariableResolver} 解析替换 ${var}。",
        ],
        "index": [
            "uk_var_key — 变量 KEY 唯一索引",
        ],
        "see": [
            "com.njydsz.message.domain.entity.config.MsgVariableSource 变量源实体",
            "com.njydsz.message.server.service.MsgVariableSourceService 变量源 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgLogMapper.java": {
        "title": "消息发送日志 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_log}，存储消息发送全量日志。",
            "每条消息的发送记录（消息 ID、接收人、渠道、模板、状态、回执状态、重试次数、错误信息），是消息中心的核心事实表。",
        ],
        "index": [
            "uk_msg_id — 消息 ID 唯一索引（雪花算法字符串）",
            "idx_user_status — 用户+状态过滤索引（待办列表）",
            "idx_send_at — 发送时间排序索引（按时间范围查询）",
        ],
        "see": [
            "com.njydsz.message.domain.entity.core.MsgLog 消息日志实体",
            "com.njydsz.message.server.service.MsgLogService 消息日志 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgNotificationMapper.java": {
        "title": "站内通知 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_notification}，存储站内通知中心数据。",
            "站内通知是消息的「最后一道兜底」渠道，无论用户是否绑定 IM/邮件，都会同步写入通知中心，登录后可见。",
        ],
        "index": [
            "uk_notification_id — 主键唯一索引",
            "idx_user_unread — (用户+已读状态) 复合索引（未读列表）",
            "idx_created_at — 创建时间排序索引",
        ],
        "see": [
            "com.njydsz.message.domain.entity.core.MsgNotification 通知实体",
            "com.njydsz.message.server.service.MsgNotificationService 通知 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgReceiptMapper.java": {
        "title": "消息回执 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_receipt}，存储消息送达/已读/点击回执。",
            "回执由 {@code ReceiptPuller} 主动拉取或渠道回调写入，与 {@code ydsz_msg_log} 一对多关联。",
        ],
        "index": [
            "uk_msg_id_channel — (消息+渠道) 唯一索引",
            "idx_receipt_at — 回执时间排序索引",
        ],
        "see": [
            "com.njydsz.message.domain.entity.receipt.MsgReceipt 回执实体",
            "com.njydsz.message.server.service.MsgReceiptService 回执 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgTemplateMapper.java": {
        "title": "消息模板 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_template}，存储消息模板主表。",
            "模板定义消息的标题/内容/变量占位符（{@code ${var}}）/渠道（IM/邮件/短信/站内），按版本管理（{@code ydsz_msg_template_version}）。",
        ],
        "index": [
            "uk_template_code — 模板编码唯一索引",
            "idx_biz_type — 业务类型过滤索引",
        ],
        "see": [
            "com.njydsz.message.domain.entity.template.MsgTemplate 模板实体",
            "com.njydsz.message.server.service.MsgTemplateService 模板 Service",
        ],
    },
    "ydsz-message/ydsz-message-infra/src/main/java/com/njydsz/message/infra/mapper/MsgTemplateVersionMapper.java": {
        "title": "消息模板版本历史 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_msg_template_version}，存储模板版本历史。",
            "模板每次修改生成新版本（draft → published → archived），支持历史回溯、灰度发布、A/B 实验。",
        ],
        "index": [
            "uk_template_version — (模板+版本号) 唯一索引",
            "idx_status — 版本状态过滤索引",
        ],
        "see": [
            "com.njydsz.message.domain.entity.template.MsgTemplateVersion 模板版本实体",
            "com.njydsz.message.server.service.MsgTemplateVersionService 模板版本 Service",
        ],
    },

    # ===== ydsz-literule =====
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleDefinitionMapper.java": {
        "title": "规则定义 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_def}，存储规则定义主表。",
            "规则是业务可配置的判断/计算逻辑（积分/折扣/审批策略/计费），支持决策表/决策树/脚本/评分卡多种表达。",
        ],
        "index": [
            "uk_rule_key — 规则 KEY 唯一索引（业务编码）",
            "idx_status — 状态过滤索引（DRAFT/PUBLISHED/DEPRECATED）",
            "idx_tenant_id — 租户隔离索引",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.RuleDefinitionDO 规则定义实体",
            "com.njydsz.literule.server.service.RuleLifecycleService 规则生命周期 Service",
        ],
    },
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleVersionHistoryMapper.java": {
        "title": "规则版本历史 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_version_history}，存储规则每次发布的版本快照。",
            "规则版本管理：每次发布生成快照（DSL+配置），支持回滚、对比、A/B 实验。",
        ],
        "index": [
            "uk_rule_version — (规则+版本号) 唯一索引",
            "idx_publish_at — 发布时间排序索引",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.RuleVersionHistory 规则版本实体",
            "com.njydsz.literule.server.service.RuleLifecycleService 规则生命周期 Service",
        ],
    },
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleVariableDefMapper.java": {
        "title": "规则变量定义 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_variable_def}，存储规则输入/输出变量定义。",
            "变量定义决定规则入参/出参的数据类型、来源、必填、约束，是规则配置的核心元数据。",
        ],
        "index": [
            "uk_rule_var — (规则+变量名) 唯一索引",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.RuleVariableDef 规则变量实体",
            "com.njydsz.literule.server.service.RuleLifecycleService 规则生命周期 Service",
        ],
    },
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleTemplateMapper.java": {
        "title": "规则模板 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_template}，存储规则模板。",
            "规则模板是规则的「母版」（预置规则集），按业务场景（OA/财务/HR）提供开箱即用的规则。",
        ],
        "index": [
            "uk_template_code — 模板编码唯一索引",
            "idx_category — 业务分类过滤索引",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.RuleTemplate 规则模板实体",
            "com.njydsz.literule.server.service.RuleTemplateService 规则模板 Service",
        ],
    },
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleTestCaseMapper.java": {
        "title": "规则测试用例 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_test_case}，存储规则的测试用例与预期结果。",
            "测试用例用于规则发布前回归（输入→预期输出对比），保证规则变更不破坏既有业务。",
        ],
        "index": [
            "uk_case_name — (规则+用例名) 唯一索引",
            "idx_result — 用例结果过滤索引（PASS/FAIL）",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.RuleTestCaseDO 规则测试用例实体",
            "com.njydsz.literule.server.service.RuleTestCaseService 测试用例 Service",
        ],
    },
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RulePackMapper.java": {
        "title": "规则包 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_pack}，存储规则包（一组规则的集合）。",
            "规则包支持批量发布/回滚/导入导出，是规则运维的核心单位。",
        ],
        "index": [
            "uk_pack_code — 包编码唯一索引",
            "idx_status — 状态过滤索引",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.RulePackDO 规则包实体",
            "com.njydsz.literule.server.service.RulePackService 规则包 Service",
        ],
    },
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RulePackInstallMapper.java": {
        "title": "规则包安装记录 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_pack_install}，存储规则包在某租户/环境的安装记录。",
            "安装记录追踪每个规则包在每个租户的安装时间、版本、状态（已安装/已卸载/升级失败）。",
        ],
        "index": [
            "uk_tenant_pack — (租户+包+版本) 唯一索引",
            "idx_installed_at — 安装时间排序索引",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.RulePackInstall 规则包安装实体",
            "com.njydsz.literule.server.service.RulePackInstallService 规则包安装 Service",
        ],
    },
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleExecutionTraceMapper.java": {
        "title": "规则执行轨迹 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_execution_trace}，存储规则每次执行的轨迹。",
            "执行轨迹记录规则入参、命中节点/分支、输出、耗时，是规则调优、问题排查、决策审计的依据。",
        ],
        "index": [
            "uk_trace_id — 轨迹 ID 唯一索引",
            "idx_rule_exec_at — (规则+执行时间) 复合索引",
            "idx_provider_trace — provider_trace_id 索引（与上游业务调用链关联）",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.RuleExecutionTraceDO 执行轨迹实体",
            "com.njydsz.literule.server.service.RuleExecutionTraceService 轨迹 Service",
        ],
    },
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleDependencyMapper.java": {
        "title": "规则依赖关系 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_dependency}，存储规则之间的依赖关系。",
            "规则依赖决定规则的执行顺序（DAG），避免循环依赖，是规则编排的核心元数据。",
        ],
        "index": [
            "uk_rule_dep — (规则+依赖规则) 唯一索引",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.RuleDependency 规则依赖实体",
            "com.njydsz.literule.server.service.RuleDependencyService 规则依赖 Service",
        ],
    },
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleChainGraphMapper.java": {
        "title": "规则链 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_chain_graph}，存储规则链的 DAG 定义。",
            "规则链把多条规则按 DAG 编排，支持串行/并行/条件分支，是复杂业务的核心编排能力。",
        ],
        "index": [
            "uk_chain_code — 链编码唯一索引",
            "idx_status — 状态过滤索引",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.RuleChainGraphDO 规则链实体",
            "com.njydsz.literule.server.service.RuleChainGraphService 规则链 Service",
        ],
    },
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleCanaryBucketMapper.java": {
        "title": "规则灰度桶 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_canary_bucket}，存储规则灰度发布的用户分桶。",
            "灰度桶按用户 ID 哈希/百分位定义灰度受众，规则灰度发布时按用户命中桶决定是否启用新规则。",
        ],
        "index": [
            "uk_bucket_key — (规则版本+桶标识) 唯一索引",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.RuleCanaryBucket 灰度桶实体",
            "com.njydsz.literule.server.service.RuleCanaryBucketService 灰度桶 Service",
        ],
    },
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleABPolicyMapper.java": {
        "title": "规则 A/B 策略 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_ab_policy}，存储规则 A/B 实验策略。",
            "A/B 策略定义对照实验（实验组/对照组/流量比例），用于规则效果对比与决策。",
        ],
        "index": [
            "uk_policy_code — 策略编码唯一索引",
            "idx_status — 状态过滤索引（RUNNING/STOPPED）",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.RuleABPolicy A/B 策略实体",
            "com.njydsz.literule.server.service.RuleABPolicyService A/B Service",
        ],
    },
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/RuleABRollbackMapper.java": {
        "title": "规则 A/B 回滚记录 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_ab_rollback}，存储 A/B 实验回滚记录。",
            "回滚记录追踪 A/B 实验失败/效果差时的自动/手动回滚动作（回到哪个版本、原因、责任人）。",
        ],
        "index": [
            "idx_policy_id — 策略维度查询索引",
            "idx_rollback_at — 回滚时间排序索引",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.RuleABRollback A/B 回滚实体",
            "com.njydsz.literule.server.service.RuleABRollbackService A/B 回滚 Service",
        ],
    },
    "ydsz-literule/ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/mapper/DecisionTableMapper.java": {
        "title": "决策表 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rule_decision_table}，存储决策表规则的行列数据。",
            "决策表是规则的一种表达方式（条件列+结论列），适合业务人员配置（if-then-else 表格化）。",
        ],
        "index": [
            "uk_rule_row — (规则+行号) 唯一索引",
        ],
        "see": [
            "com.njydsz.literule.domain.entity.DecisionTable 决策表实体",
            "com.njydsz.literule.server.service.DecisionTableService 决策表 Service",
        ],
    },

    # ===== ydsz-cronjob (remaining) =====
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobWebhookMapper.java": {
        "title": "任务 Webhook Mapper",
        "desc": [
            "对应数据表 {@code ydsz_job_webhook}，存储任务执行后的 Webhook 回调配置。",
            "Webhook 在任务成功/失败/完成时回调外部系统（OA/IM 群/工单系统），用于任务执行结果同步。",
        ],
        "index": [
            "uk_job_event — (任务+事件类型) 唯一索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.job.JobWebhook Webhook 实体",
            "com.njydsz.cronjob.server.service.JobWebhookService Webhook Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobNodeMapper.java": {
        "title": "任务执行节点 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_job_node}，存储分布式任务执行节点。",
            "节点注册到中心用于 Leader 选举、任务分片、健康检查，是分布式调度的核心基础设施。",
        ],
        "index": [
            "uk_node_id — 节点 ID 唯一索引",
            "idx_status — 状态过滤索引（ONLINE/OFFLINE）",
            "idx_heartbeat_at — 心跳时间排序索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.job.JobNode 节点实体",
            "com.njydsz.cronjob.server.service.JobNodeService 节点 Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobLogMapper.java": {
        "title": "任务执行日志 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_job_log}，存储任务每次执行的日志。",
            "执行日志记录任务触发时间、参数、结果、耗时、错误，是任务运维的事实表。",
        ],
        "index": [
            "uk_log_id — 日志 ID 唯一索引",
            "idx_job_id — 任务维度查询索引",
            "idx_trigger_at — 触发时间排序索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.log.JobLog 执行日志实体",
            "com.njydsz.cronjob.server.service.JobLogService 日志 Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobLogContentMapper.java": {
        "title": "任务日志大字段 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_job_log_content}，存储任务执行日志的大字段（堆栈/参数 JSON/返回结果）。",
            "与 {@code ydsz_job_log} 1:1 拆分，避免主表膨胀影响列表查询性能。",
        ],
        "index": [
            "uk_log_id — 日志 ID 唯一索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.log.JobLogContent 日志内容实体",
            "com.njydsz.cronjob.server.service.JobLogService 日志 Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobHistoryMapper.java": {
        "title": "任务变更历史 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_job_history}，存储任务定义的变更历史。",
            "变更历史追踪任务配置的修改（CRON/参数/重试策略），用于审计与回滚。",
        ],
        "index": [
            "idx_job_id — 任务维度查询索引",
            "idx_changed_at — 变更时间排序索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.job.JobHistory 变更历史实体",
            "com.njydsz.cronjob.server.service.JobHistoryService 变更历史 Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobDailyStatsMapper.java": {
        "title": "任务日统计 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_job_daily_stats}，存储任务每日统计（成功/失败/平均耗时）。",
            "按任务×日维度固化统计结果，用于任务大盘、告警阈值、绩效考核。",
        ],
        "index": [
            "uk_job_date — (任务+日期) 唯一索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.log.JobDailyStats 日统计实体",
            "com.njydsz.cronjob.server.service.JobStatsService 任务统计 Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobDagMapper.java": {
        "title": "任务 DAG Mapper",
        "desc": [
            "对应数据表 {@code ydsz_job_dag}，存储任务 DAG 定义。",
            "DAG 把多个 Job 编排为有向无环图，支持串行/并行/条件分支，是复杂任务的编排核心。",
        ],
        "index": [
            "uk_dag_code — DAG 编码唯一索引",
            "idx_status — 状态过滤索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.dag.JobDag DAG 实体",
            "com.njydsz.cronjob.server.service.JobDagService DAG Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobDagVersionMapper.java": {
        "title": "任务 DAG 版本历史 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_job_dag_version}，存储 DAG 的版本历史。",
            "DAG 每次修改生成新版本，支持回滚、对比、A/B 实验。",
        ],
        "index": [
            "uk_dag_version — (DAG+版本号) 唯一索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.dag.JobDagVersion DAG 版本实体",
            "com.njydsz.cronjob.server.service.JobDagService DAG Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobDagInstanceMapper.java": {
        "title": "任务 DAG 实例 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_job_dag_instance}，存储 DAG 每次执行的实例。",
            "DAG 实例记录一次完整执行的节点状态、上下文、耗时，是 DAG 运维的事实表。",
        ],
        "index": [
            "uk_instance_id — 实例 ID 唯一索引",
            "idx_dag_id — DAG 维度查询索引",
            "idx_status — 状态过滤索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.dag.JobDagInstance DAG 实例实体",
            "com.njydsz.cronjob.server.service.JobDagService DAG Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobDagNodeInstanceMapper.java": {
        "title": "任务 DAG 节点实例 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_job_dag_node_instance}，存储 DAG 实例的节点执行情况。",
            "节点实例是 DAG 执行的最小单元，记录每个节点的状态、输入、输出、耗时。",
        ],
        "index": [
            "idx_instance_id — DAG 实例维度查询索引",
            "idx_status — 状态过滤索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.dag.JobDagNodeInstance 节点实例实体",
            "com.njydsz.cronjob.server.service.JobDagService DAG Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobArtifactMapper.java": {
        "title": "任务产物 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_job_artifact}，存储任务执行的产物（文件/数据/报告）。",
            "产物是任务执行输出的可下载/可消费资产，按执行日志 ID 关联，存放在文件存储服务。",
        ],
        "index": [
            "uk_artifact_id — 产物 ID 唯一索引",
            "idx_log_id — 执行日志维度查询索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.job.JobArtifact 产物实体",
            "com.njydsz.cronjob.server.service.JobArtifactService 产物 Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobAlertRuleMapper.java": {
        "title": "任务告警规则 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_job_alert_rule}，存储任务告警规则。",
            "告警规则定义任务失败/超时/连续失败等条件触发告警（IM/短信/邮件），按租户/任务维度配置。",
        ],
        "index": [
            "uk_job_rule — (任务+规则名) 唯一索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.job.JobAlertRule 告警规则实体",
            "com.njydsz.cronjob.server.service.JobAlertRuleService 告警规则 Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobAlertLogMapper.java": {
        "title": "任务告警日志 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_job_alert_log}，存储任务告警触发日志。",
            "告警日志记录每次触发的告警（任务、规则、触发时间、推送渠道、推送结果），用于告警审计与统计。",
        ],
        "index": [
            "idx_job_id — 任务维度查询索引",
            "idx_alert_at — 告警时间排序索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.job.JobAlertLog 告警日志实体",
            "com.njydsz.cronjob.server.service.JobAlertLogService 告警日志 Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/GlueCodeMapper.java": {
        "title": "GLUE 脚本代码 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_glue_code}，存储任务 GLUE 模式的脚本代码。",
            "GLUE 模式允许任务以脚本方式实现（Shell/Python/SQL/JS），脚本内容存于本表，与 Job 解耦。",
        ],
        "index": [
            "uk_job_glue — (任务+GLUE 类型) 唯一索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.schedule.GlueCode GLUE 实体",
            "com.njydsz.cronjob.server.service.GlueCodeService GLUE Service",
        ],
    },
    "ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/TenantQuotaMapper.java": {
        "title": "租户任务配额 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_tenant_quota}，存储租户在定时任务维度的配额。",
            "配额限制租户可创建的任务数/并发数/触发频率，是多租户隔离的资源管控。",
        ],
        "index": [
            "uk_tenant_id — 租户 ID 唯一索引",
        ],
        "see": [
            "com.njydsz.cronjob.domain.entity.job.TenantQuota 配额实体",
            "com.njydsz.cronjob.server.service.TenantQuotaService 配额 Service",
        ],
    },

    # ===== ydsz-nextwiki =====
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/FileNodeMapper.java": {
        "title": "文件节点 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_file_node}，存储 NextWiki 文件树节点。",
            "文件树节点是知识库的核心数据（文件夹/文件/文档），按父子层级组织，支持版本/分享/ACL。",
        ],
        "index": [
            "uk_node_id — 节点 ID 唯一索引",
            "idx_parent_id — 父子层级索引",
            "idx_tenant_id — 租户隔离索引",
        ],
        "see": [
            "com.njydsz.nextwiki.domain.entity.FileNode 文件节点实体",
            "com.njydsz.nextwiki.server.service.FileNodeService 文件节点 Service",
        ],
    },
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/FileVersionMapper.java": {
        "title": "文件版本 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_file_version}，存储文件历史版本快照。",
            "文件每次编辑保存新版本（content + 元数据），支持回滚、对比、审计。",
        ],
        "index": [
            "uk_version_id — 版本 ID 唯一索引",
            "idx_file_id — 文件维度查询索引",
            "idx_version_no — 版本号排序索引",
        ],
        "see": [
            "com.njydsz.nextwiki.domain.entity.FileVersion 文件版本实体",
            "com.njydsz.nextwiki.server.service.FileVersionService 文件版本 Service",
        ],
    },
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/FileAclMapper.java": {
        "title": "文件权限 ACL Mapper",
        "desc": [
            "对应数据表 {@code ydsz_file_acl}，存储文件/文件夹的访问控制。",
            "ACL 按 (主体, 文件, 权限) 三元组定义访问规则（读/写/管理），是 NextWiki 安全模型的核心。",
        ],
        "index": [
            "uk_file_principal — (文件+主体类型+主体 ID+权限) 唯一索引",
            "idx_file_id — 文件维度查询索引",
        ],
        "see": [
            "com.njydsz.nextwiki.domain.entity.FileAcl 文件权限实体",
            "com.njydsz.nextwiki.server.service.FileAclService 文件权限 Service",
        ],
    },
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/ShareLinkMapper.java": {
        "title": "分享链接 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_share_link}，存储文件分享链接。",
            "分享链接是文件的对外可访问入口（含 token/过期时间/访问次数/密码），支持匿名访问与审计。",
        ],
        "index": [
            "uk_link_token — 分享 token 唯一索引",
            "idx_file_id — 文件维度查询索引",
        ],
        "see": [
            "com.njydsz.nextwiki.domain.entity.ShareLink 分享链接实体",
            "com.njydsz.nextwiki.server.service.ShareLinkService 分享 Service",
        ],
    },
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/TagMapper.java": {
        "title": "标签 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_tag}，存储文件/知识库的标签。",
            "标签是文件分类/检索的辅助手段，与文件是多对多关系（{@code ydsz_file_tag}）。",
        ],
        "index": [
            "uk_tag_name — (租户+标签名) 唯一索引",
        ],
        "see": [
            "com.njydsz.nextwiki.domain.entity.Tag 标签实体",
            "com.njydsz.nextwiki.server.service.TagService 标签 Service",
        ],
    },
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/StorageQuotaMapper.java": {
        "title": "存储配额 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_storage_quota}，存储租户在 NextWiki 的存储配额。",
            "配额按租户限制总存储容量/单文件大小/文件数量，是多租户资源隔离的关键。",
        ],
        "index": [
            "uk_tenant_id — 租户 ID 唯一索引",
        ],
        "see": [
            "com.njydsz.nextwiki.domain.entity.StorageQuota 存储配额实体",
            "com.njydsz.nextwiki.server.service.StorageQuotaService 配额 Service",
        ],
    },
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/TrashItemMapper.java": {
        "title": "回收站 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_trash_item}，存储已删除文件/文件夹。",
            "回收站支持文件恢复/彻底删除/过期自动清理，是文件删除的软删除层。",
        ],
        "index": [
            "uk_trash_id — 回收项 ID 唯一索引",
            "idx_user_id — 用户维度查询索引",
            "idx_deleted_at — 删除时间排序索引（用于过期清理）",
        ],
        "see": [
            "com.njydsz.nextwiki.domain.entity.TrashItem 回收项实体",
            "com.njydsz.nextwiki.server.service.TrashItemService 回收站 Service",
        ],
    },
    "ydsz-nextwiki/ydsz-nextwiki-infra/src/main/java/com/njydsz/nextwiki/infra/mapper/SearchIndexMapper.java": {
        "title": "搜索索引 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_search_index}，存储文件全文搜索索引。",
            "索引按文件版本同步（ES/PG 全文索引），支持全文检索/高亮/排序/聚合。",
        ],
        "index": [
            "uk_index_id — 索引 ID 唯一索引",
            "idx_file_version — (文件+版本) 索引",
        ],
        "see": [
            "com.njydsz.nextwiki.domain.entity.SearchIndex 搜索索引实体",
            "com.njydsz.nextwiki.server.service.SearchIndexService 搜索 Service",
        ],
    },

    # ===== ydsz-agent =====
    "ydsz-agent/ydsz-agent-infra/src/main/java/com/njydsz/agent/infra/mapper/AgentDefinitionMapper.java": {
        "title": "Agent 定义 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_agent_def}，存储 Agent 定义主表。",
            "Agent 是可调用的 AI 智能体（对话/任务型），由 LLM + Tools + Prompt 组成，按业务场景定义。",
        ],
        "index": [
            "uk_agent_code — Agent 编码唯一索引",
            "idx_status — 状态过滤索引（DRAFT/PUBLISHED/DEPRECATED）",
        ],
        "see": [
            "com.njydsz.agent.domain.entity.AgentDefinitionDO Agent 定义实体",
            "com.njydsz.agent.server.service.AgentDefinitionService Agent Service",
        ],
    },

    # ===== ydsz-system (remaining) =====
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/DictTypeMapper.java": {
        "title": "字典类型 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_dict_type}，存储字典类型主表。",
            "字典类型是字典项的分类（如 gender/job_level/industry），是下拉框/单选/多选等枚举型字段的元数据。",
        ],
        "index": [
            "uk_type_code — 字典类型编码唯一索引",
        ],
        "see": [
            "com.njydsz.system.domain.entity.DictType 字典类型实体",
            "com.njydsz.system.server.service.DictTypeService 字典类型 Service",
        ],
    },
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/DictVersionMapper.java": {
        "title": "字典版本管理 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_dict_version}，存储字典的版本快照。",
            "字典变更（增删改项）生成新版本，支持回滚、对比、灰度发布，避免脏数据扩散。",
        ],
        "index": [
            "uk_type_version — (字典类型+版本号) 唯一索引",
        ],
        "see": [
            "com.njydsz.system.domain.entity.DictVersion 字典版本实体",
            "com.njydsz.system.server.service.DictVersionService 字典版本 Service",
        ],
    },
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/TenantMapper.java": {
        "title": "租户 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_tenant}，存储租户主表。",
            "租户是系统多租户隔离的最高层（每条业务数据都通过 {@code tenant_id} 关联），租户状态/计划/到期时间集中管理。",
        ],
        "index": [
            "uk_tenant_code — 租户编码唯一索引",
        ],
        "see": [
            "com.njydsz.system.domain.entity.Tenant 租户实体",
            "com.njydsz.system.server.service.TenantService 租户 Service",
        ],
    },
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/TenantPlanMapper.java": {
        "title": "租户套餐 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_tenant_plan}，存储租户套餐定义。",
            "套餐定义租户的功能/容量/价格（基础版/企业版/旗舰版），由 {@code TenantPlanMenu} 关联可访问菜单。",
        ],
        "index": [
            "uk_plan_code — 套餐编码唯一索引",
        ],
        "see": [
            "com.njydsz.system.domain.entity.TenantPlan 套餐实体",
            "com.njydsz.system.server.service.TenantPlanService 套餐 Service",
        ],
    },
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/TenantPlanMenuMapper.java": {
        "title": "租户套餐-菜单关联 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_tenant_plan_menu}，存储套餐与菜单（权限）的关联。",
            "租户购买套餐后自动获得关联菜单的访问权限，是 RBAC 的「套餐级」权限分配。",
        ],
        "index": [
            "uk_plan_menu — (套餐+菜单) 唯一索引",
        ],
        "see": [
            "com.njydsz.system.domain.entity.TenantPlanMenu 套餐-菜单关联实体",
            "com.njydsz.system.server.service.TenantPlanService 套餐 Service",
        ],
    },
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/VariableMapper.java": {
        "title": "系统变量 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_variable}，存储系统级变量。",
            "系统变量是平台配置的 KV（开关/限流阈值/全局配置），由 {@code ConfigService} 提供热加载。",
        ],
        "index": [
            "uk_var_key — 变量 KEY 唯一索引",
        ],
        "see": [
            "com.njydsz.system.domain.entity.Variable 变量实体",
            "com.njydsz.system.server.service.VariableService 变量 Service",
        ],
    },
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/ConfigMapper.java": {
        "title": "系统配置 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_config}，存储系统配置项。",
            "配置项是平台级/租户级配置（功能开关/三方密钥/超时时间），支持热更新。",
        ],
        "index": [
            "uk_config_key — 配置 KEY 唯一索引",
            "idx_tenant_id — 租户隔离索引",
        ],
        "see": [
            "com.njydsz.system.domain.entity.Config 配置实体",
            "com.njydsz.system.server.service.ConfigService 配置 Service",
        ],
    },
    "ydsz-system/ydsz-system-infra/src/main/java/com/njydsz/system/infra/mapper/AppInfoMapper.java": {
        "title": "应用信息 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_app_info}，存储接入的应用信息。",
            "应用是系统接入的子系统（业务模块/三方系统），AppId/Secret 用于 API 网关鉴权。",
        ],
        "index": [
            "uk_app_id — AppId 唯一索引",
        ],
        "see": [
            "com.njydsz.system.domain.entity.AppInfo 应用实体",
            "com.njydsz.system.server.service.AppInfoService 应用 Service",
        ],
    },
}


TABLE_NAME = {
    "MsgAggregate": "ydsz_msg_aggregate",
    "MsgBatch": "ydsz_msg_batch",
    "MsgCanary": "ydsz_msg_canary",
    "MsgFeedback": "ydsz_msg_feedback",
    "MsgPreference": "ydsz_msg_preference",
    "MsgRouteRule": "ydsz_msg_route_rule",
    "MsgSubscription": "ydsz_msg_subscription",
    "MsgTrace": "ydsz_msg_trace",
    "MsgUserChannel": "ydsz_msg_user_channel",
    "MsgVariableSource": "ydsz_msg_variable_source",
    "MsgOffline": "ydsz_msg_offline",
    "MsgLog": "ydsz_msg_log",
    "MsgNotification": "ydsz_msg_notification",
    "MsgReceipt": "ydsz_msg_receipt",
    "MsgTemplate": "ydsz_msg_template",
    "MsgTemplateVersion": "ydsz_msg_template_version",
    "RuleDefinition": "ydsz_rule_def",
    "RuleVersionHistory": "ydsz_rule_version_history",
    "RuleVariableDef": "ydsz_rule_variable_def",
    "RuleTemplate": "ydsz_rule_template",
    "RuleTestCase": "ydsz_rule_test_case",
    "RulePack": "ydsz_rule_pack",
    "RulePackInstall": "ydsz_rule_pack_install",
    "RuleExecutionTrace": "ydsz_rule_execution_trace",
    "RuleDependency": "ydsz_rule_dependency",
    "RuleChainGraph": "ydsz_rule_chain_graph",
    "RuleCanaryBucket": "ydsz_rule_canary_bucket",
    "RuleABPolicy": "ydsz_rule_ab_policy",
    "RuleABRollback": "ydsz_rule_ab_rollback",
    "DecisionTable": "ydsz_rule_decision_table",
    "JobWebhook": "ydsz_job_webhook",
    "JobNode": "ydsz_job_node",
    "JobLog": "ydsz_job_log",
    "JobLogContent": "ydsz_job_log_content",
    "JobHistory": "ydsz_job_history",
    "JobDailyStats": "ydsz_job_daily_stats",
    "JobDag": "ydsz_job_dag",
    "JobDagVersion": "ydsz_job_dag_version",
    "JobDagInstance": "ydsz_job_dag_instance",
    "JobDagNodeInstance": "ydsz_job_dag_node_instance",
    "JobArtifact": "ydsz_job_artifact",
    "JobAlertRule": "ydsz_job_alert_rule",
    "JobAlertLog": "ydsz_job_alert_log",
    "GlueCode": "ydsz_glue_code",
    "TenantQuota": "ydsz_tenant_quota",
    "FileNode": "ydsz_file_node",
    "FileVersion": "ydsz_file_version",
    "FileAcl": "ydsz_file_acl",
    "ShareLink": "ydsz_share_link",
    "Tag": "ydsz_tag",
    "StorageQuota": "ydsz_storage_quota",
    "TrashItem": "ydsz_trash_item",
    "SearchIndex": "ydsz_search_index",
    "AgentDefinition": "ydsz_agent_def",
    "DictType": "ydsz_dict_type",
    "DictVersion": "ydsz_dict_version",
    "Tenant": "ydsz_tenant",
    "TenantPlan": "ydsz_tenant_plan",
    "TenantPlanMenu": "ydsz_tenant_plan_menu",
    "Variable": "ydsz_variable",
    "Config": "ydsz_config",
    "AppInfo": "ydsz_app_info",
}


def table_name(rel: str) -> str:
    base = rel.split("/")[-1].replace("Mapper.java", "")
    return TABLE_NAME.get(base, f"ydsz_{base.lower()}")


def render_class_javadoc(meta: dict, table: str) -> str:
    desc = "</p>\n * <p>".join(meta["desc"])
    index_items = "\n".join(f" *   <li>{idx}</li>" for idx in meta["index"])
    see_items = "\n".join(f" * @see {s}" for s in meta["see"])
    return f"""/**
 * {meta['title']}
 *
 * <p>对应数据表 <code>{table}</code>。
 * <p>{desc}
 *
 * <p><b>主要索引：</b>
 * <ul>
{index_items}
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {{@code tenant_id}} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{{@code deleted}} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
{see_items}
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */"""


def detect_existing_class_javadoc(content: str) -> tuple:
    """Return (start_line_of_javadoc, end_line_of_javadoc, has_full_class_doc)."""
    lines = content.split("\n")
    if not lines[0].startswith("package "):
        return (None, None, False)
    # find first /** after package + imports
    i = 0
    # find end of imports
    end_imports = -1
    for idx, line in enumerate(lines):
        if line.startswith("package ") or line.startswith("import "):
            end_imports = idx
        elif line.strip() and not line.startswith("//") and not line.startswith("*"):
            break
    # search for /** from end_imports onward
    for idx in range(end_imports + 1, len(lines)):
        if lines[idx].startswith("/**"):
            # find matching */
            j = idx
            while j < len(lines):
                if lines[j].endswith("*/"):
                    return (idx, j, True)
                j += 1
            break
    return (None, None, False)


def main():
    base = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend")
    count = 0
    skip = 0
    err = 0
    for rel, meta in MAPPERS.items():
        fpath = base / rel
        if not fpath.exists():
            print(f"SKIP (not found): {rel}")
            skip += 1
            continue
        content = fpath.read_text(encoding="utf-8")
        table = table_name(rel)
        new_javadoc = render_class_javadoc(meta, table)
        start, end, has_existing = detect_existing_class_javadoc(content)
        if has_existing:
            lines = content.split("\n")
            new_lines = lines[:start] + new_javadoc.split("\n") + lines[end + 1:]
            new_content = "\n".join(new_lines)
        else:
            # insert new javadoc before @Mapper
            new_content = content.replace(
                "@Mapper\npublic interface",
                new_javadoc + "\n@Mapper\npublic interface"
            )
        fpath.write_text(new_content, encoding="utf-8")
        print(f"OK: {rel}")
        count += 1
    print(f"\nTotal: {count} files updated, {skip} skipped")


if __name__ == "__main__":
    main()
