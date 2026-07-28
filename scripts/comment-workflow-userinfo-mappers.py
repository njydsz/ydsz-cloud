#!/usr/bin/env python3
"""Enhance class-level Javadoc for ydsz-workflow and ydsz-userinfo Mappers.

Many of these Mappers have minimal Javadoc (just title and table name). This
script upgrades them to the same standard as the other modules' Mappers
(class-level Javadoc with business context, indexes, multi-tenant notes,
logical deletion markers).
"""
import pathlib
import re

BASE = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend")

# (rel_path, title, desc_lines[], index_lines[], see_lines[])
MAPPERS = {
    # ===== ydsz-workflow (26 files) =====
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowDefinitionMapper.java": {
        "title": "流程定义 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_definition</code>，存储流程定义主表。",
            "流程定义是「流程模板的某个具体版本」（含 BPMN 2.0 XML / JSON DSL / 节点配置），按 version 管理，支持发布/灰度/版本回滚。",
        ],
        "index": [
            "uk_flow_code_version — (flowCode+version+tenantId) 唯一索引",
            "idx_is_publish — 发布状态过滤索引",
            "idx_activity_status — 激活状态过滤索引（0 挂起 / 1 激活）",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowDefinition 流程定义实体",
            "com.njydsz.workflow.server.service.FlowDefinitionService 流程定义 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowInstanceMapper.java": {
        "title": "流程实例 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_instance</code>，存储每次流程发起生成的运行实例。",
            "流程实例是「流程定义的一次具体执行」（含发起人/业务关联/当前节点/状态/变量），按 RUNNING/APPROVED/REJECTED 状态推进，结束态归档到 {@code ydsz_flow_his_instance}。",
        ],
        "index": [
            "uk_business — (tenantId+businessType+businessId) 唯一索引（一业务一实例）",
            "idx_initiator — 发起人维度索引",
            "idx_flow_status — 流程状态过滤索引（待办/已办查询）",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowInstance 流程实例实体",
            "com.njydsz.workflow.server.service.FlowInstanceService 流程实例 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowNodeMapper.java": {
        "title": "流程节点 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_node</code>，维护流程定义中每个节点（开始/审批/分支/结束）的元数据。",
            "节点是流程执行的最小单元（审批人/CC人/超时/驳回策略），引擎按节点推进实例。",
        ],
        "index": [
            "uk_node_code — (definitionId+nodeCode) 唯一索引",
            "idx_node_type — 节点类型过滤索引（START/APPROVAL/BRANCH/END）",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowNode 流程节点实体",
            "com.njydsz.workflow.server.service.FlowNodeService 流程节点 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowHisInstanceMapper.java": {
        "title": "P2-3 流程实例归档 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_his_instance</code>，存储结束态（APPROVED/REJECTED/TERMINATED）的流程实例归档数据。",
            "归档表与运行表分离，避免 {@code ydsz_flow_instance} 无限膨胀；归档数据用于历史查询/审计/统计分析。",
        ],
        "index": [
            "uk_instance_id — 实例 ID 唯一索引（1:1 关联运行实例）",
            "idx_end_at — 结束时间排序索引（按时间段查询）",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowHisInstance 流程实例归档实体",
            "com.njydsz.workflow.server.service.FlowArchiveService 归档 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowHisTaskMapper.java": {
        "title": "历史任务 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_his_task</code>，归档已完成的流程任务，供已办查询与审计追溯。",
            "任务结束（同意/驳回/转办/加签完成）后从 {@code ydsz_flow_run_task} 迁移到本表，保留完整审批轨迹。",
        ],
        "index": [
            "uk_task_id — 任务 ID 唯一索引（1:1 关联运行任务）",
            "idx_user_done — 用户维度已办查询索引",
            "idx_end_at — 完成时间排序索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowHisTask 历史任务实体",
            "com.njydsz.workflow.server.service.FlowTaskHistoryService 已办 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowRunTaskMapper.java": {
        "title": "待办任务运行态 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_run_task</code>（原 {@code ydsz_flow_task}，2026-07-06 重命名），存储进行中的待办任务。",
            "待办任务是「某个节点 + 某个处理人 + 某种状态」的实例，运行态任务结束后迁移到 {@code ydsz_flow_his_task} 归档表。",
        ],
        "index": [
            "uk_task_id — 任务 ID 唯一索引",
            "idx_assignee — 处理人维度待办查询索引",
            "idx_instance_id — 流程实例维度查询索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowRunTask 待办任务实体",
            "com.njydsz.workflow.server.service.FlowTaskService 待办 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowCommentMapper.java": {
        "title": "P2-2 流程评论 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_comment</code>，存储审批评论与多级回复。",
            "一级评论（{@code parent_comment_id IS NULL}）与回复通过不同索引高效查询；评论支持 @ 提醒（{@code mentioned_user_ids}）与表情。",
        ],
        "index": [
            "idx_instance_id — 流程实例维度查询索引",
            "idx_parent_id — 父子层级索引（多级回复）",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowComment 评论实体",
            "com.njydsz.workflow.server.service.FlowCommentService 评论 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowAuditLogMapper.java": {
        "title": "流程审计日志 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_audit_log</code>，记录审批全操作轨迹。",
            "审计日志是「不可变」的事实表（仅插入不更新/删除），用于安全审计/合规追溯/异常排查。",
        ],
        "index": [
            "idx_instance_id — 流程实例维度查询索引",
            "idx_audit_at — 操作时间排序索引（按时间范围查询）",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowAuditLog 审计日志实体",
            "com.njydsz.workflow.server.service.FlowAuditService 审计 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowCategoryMapper.java": {
        "title": "流程分类 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_category</code>，存储流程分类字典（人事/财务/项目/合同等）。",
            "分类用于流程模板的归类与检索，是流程中心左侧导航树的根节点。",
        ],
        "index": [
            "uk_category_code — 分类编码唯一索引",
            "idx_parent_id — 父子层级索引（支持二级分类）",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowCategory 流程分类实体",
            "com.njydsz.workflow.server.service.FlowCategoryService 分类 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowTemplateMapper.java": {
        "title": "流程模板 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_template</code>，存储可复用的流程模板（带版本化与继承关系）。",
            "模板是「流程定义的母版」，按分类与编码组织，支持版本升级与父子继承。",
        ],
        "index": [
            "uk_template_code — 模板编码唯一索引",
            "idx_category_id — 分类过滤索引",
            "idx_is_latest — 最新版本过滤索引（默认仅返回 is_latest=1）",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowTemplate 流程模板实体",
            "com.njydsz.workflow.server.service.FlowTemplateService 流程模板 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowAttachmentMapper.java": {
        "title": "自建工作流引擎 - 审批附件 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_attachment</code>（P1-6 GAP-51），存储审批节点上传的附件。",
            "附件走文件存储服务，DB 仅保存元数据（文件 ID/名称/大小/上传人），由 {@code FileStorageService} 负责上传/下载。",
        ],
        "index": [
            "idx_instance_id — 流程实例维度查询索引",
            "idx_task_id — 任务维度查询索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowAttachment 审批附件实体",
            "com.njydsz.workflow.server.service.FlowAttachmentService 审批附件 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowCcMapper.java": {
        "title": "流程抄送 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_cc</code>（P0-3），存储流程抄送关系。",
            "抄送中心（对标钉钉/飞书的「抄送我的」独立 Tab），被抄送人只读可见，不参与审批。",
        ],
        "index": [
            "idx_cc_user — 被抄送人维度查询索引（抄送我的）",
            "idx_instance_id — 流程实例维度查询索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowCc 抄送实体",
            "com.njydsz.workflow.server.service.FlowCcService 抄送 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowCcRuleMapper.java": {
        "title": "流程抄送规则 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_cc_rule</code>，存储自动抄送规则。",
            "抄送规则按条件自动抄送（按节点/角色/部门/发起人），由引擎在节点完成后自动触发，无需人工选择。",
        ],
        "index": [
            "uk_rule_code — 规则编码唯一索引",
            "idx_flow_code — 流程编码过滤索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowCcRule 抄送规则实体",
            "com.njydsz.workflow.server.service.FlowCcRuleService 抄送规则 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowDelegateAuthMapper.java": {
        "title": "流程委派代理 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_delegate_auth</code>（P1-4），存储长期授权委派。",
            "委派代理用于请假/出差场景，授权人 A 将自己的待办授权给代理人 B 处理（含时间范围/可委派范围/转交/不转交策略）。",
        ],
        "index": [
            "uk_auth_id — 授权 ID 唯一索引",
            "idx_authorizer — 授权人维度查询索引",
            "idx_effective_time — 生效时间范围索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowDelegateAuth 委派代理实体",
            "com.njydsz.workflow.server.service.FlowDelegateService 委派 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowDmnDecisionMapper.java": {
        "title": "P0-1 DMN 决策表 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_dmn_decision</code>，存储 DMN（Decision Model and Notation）决策表定义。",
            "DMN 决策表用于条件分支场景（如「金额&gt;10万 → 走财务总监审批」），是 BPMN 流程中分支节点的配置数据。",
        ],
        "index": [
            "uk_decision_code — 决策表编码唯一索引",
            "idx_flow_code — 流程编码过滤索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowDmnDecision DMN 决策表实体",
            "com.njydsz.workflow.server.service.FlowDmnService DMN Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowDmnRuleMapper.java": {
        "title": "P0-1 DMN 决策规则 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_dmn_rule</code>，存储 DMN 决策表的具体行规则。",
            "决策规则是决策表的一行（输入条件 + 输出结论），按 hitPolicy 决定命中策略（FIRST/UNIQUE/PRIORITY/ANY）。",
        ],
        "index": [
            "uk_decision_rule_no — (decisionId+ruleNo) 唯一索引",
            "idx_priority — 优先级排序索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowDmnRule DMN 规则实体",
            "com.njydsz.workflow.server.service.FlowDmnService DMN Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowEventSubscriptionMapper.java": {
        "title": "工作流事件订阅 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_event_subscription</code>，存储流程事件的外部订阅。",
            "事件订阅支持「流程开始/结束/节点完成」等事件推送到 IM/OA/三方系统（基于 Spring Event / Redis Stream）。",
        ],
        "index": [
            "uk_subscription_id — 订阅 ID 唯一索引",
            "idx_event_type — 事件类型过滤索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowEventSubscription 事件订阅实体",
            "com.njydsz.workflow.server.service.FlowEventService 事件 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowQuickCommentMapper.java": {
        "title": "审批常用语 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_quick_comment</code>，存储审批常用语（快捷回复）。",
            "常用语按用户维度配置（个人常用/部门常用/全局常用），支持排序与启用/禁用。",
        ],
        "index": [
            "uk_user_comment — (userId+content) 唯一索引",
            "idx_user_scope — 用户/范围过滤索引（PERSONAL/DEPT/GLOBAL）",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowQuickComment 常用语实体",
            "com.njydsz.workflow.server.service.FlowQuickCommentService 常用语 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowSkipMapper.java": {
        "title": "节点跳转 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_skip</code>，记录节点之间的跳转关系（正向流转/退回）。",
            "跳转规则由 BPMN 2.0 的 SequenceFlow 解析得到（含条件表达式），是引擎查找前驱/后继节点的核心数据。",
        ],
        "index": [
            "uk_skip_id — 跳转 ID 唯一索引",
            "idx_from_node — 源节点维度查询索引",
            "idx_to_node — 目标节点维度查询索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowSkip 节点跳转实体",
            "com.njydsz.workflow.server.engine.FlowEngine 流程引擎",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowTimerMapper.java": {
        "title": "工作流定时器 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_timer</code>，存储工作流中的定时器配置（超时/催办/自动跳过）。",
            "定时器由 {@code FlowTimerScheduler} 周期性扫描触发（每分钟），执行超时自动通过/催办通知/自动跳过等动作。",
        ],
        "index": [
            "uk_timer_id — 定时器 ID 唯一索引",
            "idx_fire_time — 触发时间排序索引（扫描待触发定时器）",
            "idx_status — 状态过滤索引（PENDING/FIRED/CANCELLED）",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowTimer 定时器实体",
            "com.njydsz.workflow.server.scheduler.FlowTimerScheduler 定时器调度器",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowUserMapper.java": {
        "title": "流程用户 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_user</code>，记录会签/或签场景下每个任务的处理人与处理状态。",
            "会签模式下多个 FlowUser 关联同一任务；或签模式下任何一个人处理完即视为任务完成。",
        ],
        "index": [
            "uk_user_task — (taskId+userId) 唯一索引",
            "idx_user_status — 用户+处理状态过滤索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowUser 流程用户实体",
            "com.njydsz.workflow.server.service.FlowTaskService 待办 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowAutoTriggerMapper.java": {
        "title": "流程自动触发规则 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_auto_trigger</code>，存储流程间的自动触发规则。",
            "自动触发用于「源流程完成后自动发起目标流程」场景（如项目立项完成后自动发起「项目启动会议」流程），按源流程编码 + 触发条件定义。",
        ],
        "index": [
            "uk_rule_id — 规则 ID 唯一索引",
            "idx_source_flow — 源流程编码过滤索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowAutoTrigger 自动触发规则实体",
            "com.njydsz.workflow.server.service.FlowAutoTriggerService 自动触发 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowAdminRoleMapper.java": {
        "title": "流程管理员角色 Mapper（P1-6）",
        "desc": [
            "对应数据表 <code>ydsz_flow_admin_role</code>，存储流程管理员与流程分类的关联。",
            "流程管理员可管理某分类下所有流程（设计/发布/统计），按 (userId + categoryId) 唯一，区别于 RBAC 角色。",
        ],
        "index": [
            "uk_user_category — (userId+categoryId) 唯一索引",
            "idx_category_id — 分类维度查询索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowAdminRole 流程管理员实体",
            "com.njydsz.workflow.server.service.FlowAdminService 流程管理员 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowThirdPartyAccountMapper.java": {
        "title": "三方审批账号映射 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_third_party_account</code>（P0-2），存储 ydsz 用户与三方平台账号的映射。",
            "用于审批消息推送/回调（钉钉/飞书/企微），按三方平台类型 + 三方用户 ID 唯一，反向通过 ydsz 用户 ID 查找。",
        ],
        "index": [
            "uk_platform_user — (platform+thirdUserId) 唯一索引",
            "idx_user_id — ydsz 用户 ID 索引（反向查询）",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowThirdPartyAccount 三方账号映射实体",
            "com.njydsz.workflow.server.service.FlowThirdPartyService 三方 Service",
        ],
    },
    "ydsz-workflow/ydsz-workflow-infra/src/main/java/com/njydsz/workflow/infra/mapper/FlowThirdPartyLogMapper.java": {
        "title": "三方审批回调日志 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_flow_third_party_log</code>（P0-2），存储三方审批回调日志与状态更新。",
            "钉钉/飞书/企微审批完成后通过 webhook 回调到本表，再由同步任务拉取状态变更（同意/驳回/转办）。",
        ],
        "index": [
            "idx_platform_event — (platform+eventId) 索引（幂等去重）",
            "idx_instance_id — 流程实例维度查询索引",
        ],
        "see": [
            "com.njydsz.workflow.domain.entity.FlowThirdPartyLog 三方回调日志实体",
            "com.njydsz.workflow.server.service.FlowThirdPartySyncService 三方同步 Service",
        ],
    },
    # ===== ydsz-userinfo (6 remaining files) =====
    "ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/UserRoleMapper.java": {
        "title": "用户-角色关联表 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_user_role</code>，存储用户与角色的多对多关联。",
            "一个用户可拥有多个角色（叠加权限），角色由 {@code RoleMapper} 维护，权限由 {@code RolePermissionMapper} 维护。",
        ],
        "index": [
            "uk_user_role — (userId+roleId) 唯一索引",
            "idx_user_id — 用户维度查询索引（查用户的角色）",
            "idx_role_id — 角色维度查询索引（查角色的用户）",
        ],
        "see": [
            "com.njydsz.userinfo.domain.entity.UserRole 用户-角色关联实体",
            "com.njydsz.userinfo.server.service.UserRoleService 用户-角色 Service",
        ],
    },
    "ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/UserDeptMapper.java": {
        "title": "用户-部门关联表 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_user_dept</code>，存储用户与部门的多对多关联。",
            "支持一人多部门（主岗/兼岗），用 {@code is_main} 标识主部门，是工作流审批人展开（{@code dept:xxx}）的核心数据。",
        ],
        "index": [
            "uk_user_dept — (userId+deptId) 唯一索引",
            "idx_user_id — 用户维度查询索引（查用户的部门）",
            "idx_dept_id — 部门维度查询索引（查部门的用户）",
        ],
        "see": [
            "com.njydsz.userinfo.domain.entity.UserDept 用户-部门关联实体",
            "com.njydsz.userinfo.server.service.UserDeptService 用户-部门 Service",
        ],
    },
    "ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/UserPostMapper.java": {
        "title": "用户-岗位关联表 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_user_post</code>，存储用户与岗位的多对多关联。",
            "支持一人多岗（PM/DEV/QA），是工作流审批人展开（{@code post:xxx}）的核心数据。",
        ],
        "index": [
            "uk_user_post — (userId+postId) 唯一索引",
            "idx_user_id — 用户维度查询索引",
            "idx_post_id — 岗位维度查询索引",
        ],
        "see": [
            "com.njydsz.userinfo.domain.entity.UserPost 用户-岗位关联实体",
            "com.njydsz.userinfo.server.service.UserPostService 用户-岗位 Service",
        ],
    },
    "ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/UserFieldMapper.java": {
        "title": "用户自定义字段 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_user_field</code>，存储用户表的扩展字段定义。",
            "支持运行时扩展用户属性（不必修改 user 表结构），由 Service 层在用户查询时按 key-value 合并。",
        ],
        "index": [
            "uk_user_field — (userId+fieldKey) 唯一索引",
            "idx_user_id — 用户维度查询索引",
            "idx_field_key — 字段 KEY 过滤索引",
        ],
        "see": [
            "com.njydsz.userinfo.domain.entity.UserField 用户扩展字段实体",
            "com.njydsz.userinfo.server.service.UserFieldService 用户扩展字段 Service",
        ],
    },
    "ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/RolePermissionMapper.java": {
        "title": "角色-权限关联表 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_role_permission</code>，存储角色与权限（菜单）的多对多关联。",
            "是 RBAC 模型的核心中间表，权限（{@code ydsz_menu}）既可表示菜单也可表示后端接口权限码。",
        ],
        "index": [
            "uk_role_perm — (roleId+menuId) 唯一索引",
            "idx_role_id — 角色维度查询索引（角色的权限）",
            "idx_menu_id — 菜单维度查询索引（哪些角色拥有）",
        ],
        "see": [
            "com.njydsz.userinfo.domain.entity.RolePermission 角色-权限关联实体",
            "com.njydsz.userinfo.server.service.RolePermissionService 角色-权限 Service",
        ],
    },
    "ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/CompanyDeptMapper.java": {
        "title": "公司-部门关联表 Mapper",
        "desc": [
            "对应数据表 <code>ydsz_company_dept</code>，存储公司与部门的多对多关联。",
            "支持一个部门归属多个公司（联合公司/合资公司场景），区别于部门的 {@code companyId} 直接归属字段。",
        ],
        "index": [
            "uk_company_dept — (companyId+deptId) 唯一索引",
            "idx_company_id — 公司维度查询索引",
            "idx_dept_id — 部门维度查询索引",
        ],
        "see": [
            "com.njydsz.userinfo.domain.entity.CompanyDept 公司-部门关联实体",
            "com.njydsz.userinfo.server.service.CompanyDeptService 公司-部门 Service",
        ],
    },
}


def render_class_javadoc(meta: dict) -> str:
    desc = "</p>\n * <p>".join(meta["desc"])
    index_items = "\n".join(f" *   <li>{idx}</li>" for idx in meta["index"])
    see_items = "\n".join(f" * @see {s}" for s in meta["see"])
    return f"""/**
 * {meta['title']}
 *
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
    end_imports = -1
    for idx, line in enumerate(lines):
        if line.startswith("package ") or line.startswith("import "):
            end_imports = idx
        elif line.strip() and not line.startswith("//") and not line.startswith("*"):
            break
    for idx in range(end_imports + 1, len(lines)):
        if lines[idx].startswith("/**"):
            j = idx
            while j < len(lines):
                if lines[j].endswith("*/"):
                    return (idx, j, True)
                j += 1
            break
    return (None, None, False)


def main():
    count = 0
    skip = 0
    for rel, meta in MAPPERS.items():
        fpath = BASE / rel
        if not fpath.exists():
            print(f"SKIP (not found): {rel}")
            skip += 1
            continue
        content = fpath.read_text(encoding="utf-8")
        new_javadoc = render_class_javadoc(meta)
        start, end, has_existing = detect_existing_class_javadoc(content)
        if has_existing:
            lines = content.split("\n")
            new_lines = lines[:start] + new_javadoc.split("\n") + lines[end + 1:]
            new_content = "\n".join(new_lines)
        else:
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
