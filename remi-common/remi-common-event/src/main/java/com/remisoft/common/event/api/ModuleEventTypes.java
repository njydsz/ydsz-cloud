package com.remisoft.common.event.api;

/**
 * 模块间事件类型常量注册表。
 *
 * <p>定义所有跨模块事件的标准类型名称，作为模块间事件契约的单一来源。
 * 各模块发布事件时使用此处定义的常量，消费方按类型订阅。
 *
 * <p><b>与 common-event StandardEventTypes 的关系：</b>
 * <ul>
 *   <li>本类定义领域事件类型常量，与 {@link DomainEvent} 强绑定</li>
 *   <li>{@code StandardEventTypes}（common-event 模块）定义通用基础设施事件类型（如 Outbox/MQ 事件）</li>
 *   <li>领域事件优先使用本类常量，基础设施事件使用 StandardEventTypes</li>
 * </ul>
 *
 * <h3>命名规范</h3>
 * <ul>
 *   <li>格式：{@code MODULE_ACTION}（如 {@code WORKFLOW_TASK_CREATED}）</li>
 *   <li>使用过去时或完成时，表达"已发生的事实"</li>
 *   <li>每个事件类型注明发布方模块和消费方模块</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @since 1.5.0 由 common-domain 迁入 common-event
 */
public final class ModuleEventTypes {

    private ModuleEventTypes() {
    }

    // ==================== 工作流事件（发布方: remi-workflow） ====================

    /** 流程实例启动（消费方: remi-project → 立项状态联动） */
    public static final String WORKFLOW_INSTANCE_STARTED = "WORKFLOW_INSTANCE_STARTED";

    /** 流程实例完成（消费方: remi-project → 立项状态联动） */
    public static final String WORKFLOW_INSTANCE_COMPLETED = "WORKFLOW_INSTANCE_COMPLETED";

    /** 流程实例驳回（消费方: remi-project → 立项状态联动） */
    public static final String WORKFLOW_INSTANCE_REJECTED = "WORKFLOW_INSTANCE_REJECTED";

    /** 审批任务创建（消费方: remi-message → 站内信/IM 推送） */
    public static final String WORKFLOW_TASK_CREATED = "WORKFLOW_TASK_CREATED";

    /** 审批任务完成（消费方: 审计日志） */
    public static final String WORKFLOW_TASK_COMPLETED = "WORKFLOW_TASK_COMPLETED";

    /** 审批催办（消费方: remi-message → 站内信/IM 推送） */
    public static final String WORKFLOW_TASK_URGED = "WORKFLOW_TASK_URGED";

    /** 审批转办（消费方: remi-message → 站内信/IM 推送） */
    public static final String WORKFLOW_TASK_TRANSFERRED = "WORKFLOW_TASK_TRANSFERRED";

    // ==================== 告警事件（发布方: 各模块） ====================

    /** 统一告警事件（消费方: remi-message → 多通道告警派发） */
    public static final String UNIFIED_ALERT = "UNIFIED_ALERT";

    /** 定时任务执行失败告警（发布方: remi-cronjob，消费方: remi-message） */
    public static final String CRONJOB_EXECUTION_FAILED = "CRONJOB_EXECUTION_FAILED";

    /** 定时任务执行超时告警（发布方: remi-cronjob，消费方: remi-message） */
    public static final String CRONJOB_EXECUTION_TIMEOUT = "CRONJOB_EXECUTION_TIMEOUT";

    // ==================== 权限变更事件（发布方: remi-system） ====================

    /** 权限变更（消费方: remi-gateway → 权限缓存刷新） */
    public static final String PERMISSION_CHANGED = "PERMISSION_CHANGED";

    // ==================== 配置变更事件（发布方: remi-system） ====================

    /** 配置变更（消费方: 各模块 → 配置热刷新） */
    public static final String CONFIG_CHANGED = "CONFIG_CHANGED";

    // ==================== 审计事件（发布方: 各模块） ====================

    /** 操作日志（消费方: remi-system → 审计日志持久化） */
    public static final String OPERATION_LOG = "OPERATION_LOG";

    /** 数据导出审计（消费方: remi-system → 导出审计记录） */
    public static final String DATA_EXPORT_AUDIT = "DATA_EXPORT_AUDIT";

    // ==================== 用户/权限事件（发布方: remi-userinfo） ====================

    /** 用户创建（消费方: remi-message → 欢迎消息；remi-system → 默认角色初始化） */
    public static final String USER_CREATED = "USER_CREATED";

    /** 用户更新（消费方: 各模块 → 缓存刷新/审计） */
    public static final String USER_UPDATED = "USER_UPDATED";

    /** 用户删除（消费方: remi-message → 清理通知订阅；各模块 → 引用清理） */
    public static final String USER_DELETED = "USER_DELETED";

    /** 角色变更（消费方: remi-gateway → 权限缓存刷新） */
    public static final String ROLE_CHANGED = "ROLE_CHANGED";

    /** 组织/部门变更（消费方: remi-workflow → 审批人解析缓存刷新） */
    public static final String DEPARTMENT_CHANGED = "DEPARTMENT_CHANGED";

    // ==================== 项目事件（发布方: remi-project） ====================

    /** 项目创建（消费方: remi-workflow → 立项流程联动） */
    public static final String PROJECT_CREATED = "PROJECT_CREATED";

    /** 项目状态变更（消费方: remi-message → 通知项目干系人） */
    public static final String PROJECT_STATUS_CHANGED = "PROJECT_STATUS_CHANGED";

    /** 项目立项审批通过（消费方: remi-project → 状态机推进；remi-workflow → 流程归档） */
    public static final String PROJECT_INITIATION_APPROVED = "PROJECT_INITIATION_APPROVED";

    /** 项目变更审批通过（消费方: remi-project → 变更生效；remi-message → 通知） */
    public static final String PROJECT_CHANGE_APPROVED = "PROJECT_CHANGE_APPROVED";

    // ==================== Agent 事件（发布方: remi-agent） ====================

    /** Agent 执行启动（消费方: remi-message → 执行状态通知；remi-cronjob → 异步任务跟踪） */
    public static final String AGENT_EXECUTION_STARTED = "AGENT_EXECUTION_STARTED";

    /** Agent 执行完成（消费方: remi-message → 结果通知） */
    public static final String AGENT_EXECUTION_COMPLETED = "AGENT_EXECUTION_COMPLETED";

    /** Agent 执行失败（消费方: remi-message → 告警；remi-cronjob → 重试调度） */
    public static final String AGENT_EXECUTION_FAILED = "AGENT_EXECUTION_FAILED";

    // ==================== 规则事件（发布方: remi-literule） ====================

    /** 规则发布（消费方: remi-literule → 规则缓存刷新；各模块 → 规则版本感知） */
    public static final String RULE_PUBLISHED = "RULE_PUBLISHED";

    /** 规则停用（消费方: remi-literule → 规则缓存清理） */
    public static final String RULE_DISABLED = "RULE_DISABLED";

    // ==================== 知识库事件（发布方: remi-nextwiki） ====================

    /** 文档变更（消费方: remi-agent → RAG 索引增量更新；remi-search → 全文索引刷新） */
    public static final String WIKI_DOCUMENT_CHANGED = "WIKI_DOCUMENT_CHANGED";
}
