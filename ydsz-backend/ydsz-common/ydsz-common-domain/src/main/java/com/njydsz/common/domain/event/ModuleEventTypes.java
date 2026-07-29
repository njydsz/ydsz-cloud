package com.njydsz.common.domain.event;

/**
 * 模块间事件类型常量注册表。
 *
 * <p>定义所有跨模块事件的标准类型名称，作为模块间事件契约的单一来源。
 * 各模块发布事件时使用此处定义的常量，消费方按类型订阅。
 *
 * <p><b>与 common-event StandardEventTypes 的关系：</b>
 * <ul>
 *   <li>本类定义 domain 模块内置的领域事件类型常量，与 {@link DomainEvent} 强绑定</li>
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
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ModuleEventTypes {

    private ModuleEventTypes() {
    }

    // ==================== 工作流事件（发布方: ydsz-workflow） ====================

    /** 流程实例启动（消费方: ydsz-project → 立项状态联动） */
    public static final String WORKFLOW_INSTANCE_STARTED = "WORKFLOW_INSTANCE_STARTED";

    /** 流程实例完成（消费方: ydsz-project → 立项状态联动） */
    public static final String WORKFLOW_INSTANCE_COMPLETED = "WORKFLOW_INSTANCE_COMPLETED";

    /** 流程实例驳回（消费方: ydsz-project → 立项状态联动） */
    public static final String WORKFLOW_INSTANCE_REJECTED = "WORKFLOW_INSTANCE_REJECTED";

    /** 审批任务创建（消费方: ydsz-message → 站内信/IM 推送） */
    public static final String WORKFLOW_TASK_CREATED = "WORKFLOW_TASK_CREATED";

    /** 审批任务完成（消费方: 审计日志） */
    public static final String WORKFLOW_TASK_COMPLETED = "WORKFLOW_TASK_COMPLETED";

    /** 审批催办（消费方: ydsz-message → 站内信/IM 推送） */
    public static final String WORKFLOW_TASK_URGED = "WORKFLOW_TASK_URGED";

    /** 审批转办（消费方: ydsz-message → 站内信/IM 推送） */
    public static final String WORKFLOW_TASK_TRANSFERRED = "WORKFLOW_TASK_TRANSFERRED";

    // ==================== 告警事件（发布方: 各模块） ====================

    /** 统一告警事件（消费方: ydsz-message → 多通道告警派发） */
    public static final String UNIFIED_ALERT = "UNIFIED_ALERT";

    /** 定时任务执行失败告警（发布方: ydsz-cronjob，消费方: ydsz-message） */
    public static final String CRONJOB_EXECUTION_FAILED = "CRONJOB_EXECUTION_FAILED";

    /** 定时任务执行超时告警（发布方: ydsz-cronjob，消费方: ydsz-message） */
    public static final String CRONJOB_EXECUTION_TIMEOUT = "CRONJOB_EXECUTION_TIMEOUT";

    // ==================== 权限变更事件（发布方: ydsz-system） ====================

    /** 权限变更（消费方: ydsz-gateway → 权限缓存刷新） */
    public static final String PERMISSION_CHANGED = "PERMISSION_CHANGED";

    // ==================== 配置变更事件（发布方: ydsz-system） ====================

    /** 配置变更（消费方: 各模块 → 配置热刷新） */
    public static final String CONFIG_CHANGED = "CONFIG_CHANGED";

    // ==================== 审计事件（发布方: 各模块） ====================

    /** 操作日志（消费方: ydsz-system → 审计日志持久化） */
    public static final String OPERATION_LOG = "OPERATION_LOG";

    /** 数据导出审计（消费方: ydsz-system → 导出审计记录） */
    public static final String DATA_EXPORT_AUDIT = "DATA_EXPORT_AUDIT";
}