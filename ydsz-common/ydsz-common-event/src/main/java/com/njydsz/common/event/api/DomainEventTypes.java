package com.njydsz.common.event.api;

/**
 * 统一领域事件类型注册表
 *
 * <p>定义所有跨模块事件的标准类型名称，作为模块间事件契约的单一来源。
 * 各模块发布事件时使用此处定义的常量，消费方按类型订阅。
 *
 * <p><b>命名规范：</b>
 * <ul>
 *   <li>格式：{@code MODULE_ENTITY_ACTION}（如 {@code ORDER_CREATED}、{@code USER_DISABLED}）</li>
 *   <li>使用过去时或完成时，表达"已发生的事实"</li>
 *   <li>每个事件类型注明发布方模块和消费方模块</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.6.0
 */
public final class DomainEventTypes {

    private DomainEventTypes() {
    }

    // ==================== 工作流事件（发布方: ydsz-workflow） ====================

    /** 流程实例启动（消费方: ydsz-project → 立项状态联动） */
    public static final String FLOW_INSTANCE_STARTED = "FLOW_INSTANCE_STARTED";

    /** 流程实例审批通过（消费方: ydsz-project → 立项状态联动） */
    public static final String FLOW_INSTANCE_APPROVED = "FLOW_INSTANCE_APPROVED";

    /** 流程实例被驳回（消费方: ydsz-project → 立项状态联动） */
    public static final String FLOW_INSTANCE_REJECTED = "FLOW_INSTANCE_REJECTED";

    /** 流程实例被终止（消费方: ydsz-project → 立项状态联动） */
    public static final String FLOW_INSTANCE_TERMINATED = "FLOW_INSTANCE_TERMINATED";

    /** 审批任务完成（消费方: 审计日志） */
    public static final String FLOW_TASK_COMPLETED = "FLOW_TASK_COMPLETED";

    /** 流程超时催办（消费方: ydsz-message → 站内信/IM 推送） */
    public static final String FLOW_URGE_TRIGGERED = "FLOW_URGE_TRIGGERED";

    // ==================== 用户事件（发布方: ydsz-userinfo） ====================

    /** 用户创建（消费方: ydsz-message → 欢迎消息；ydsz-system → 默认角色初始化） */
    public static final String USER_CREATED = "USER_CREATED";

    /** 用户更新（消费方: 各模块 → 缓存刷新/审计） */
    public static final String USER_UPDATED = "USER_UPDATED";

    /** 用户删除（消费方: ydsz-message → 清理通知订阅；各模块 → 引用清理） */
    public static final String USER_DELETED = "USER_DELETED";

    /** 用户启用 */
    public static final String USER_ENABLED = "USER_ENABLED";

    /** 用户禁用（消费方: ydsz-workflow → 转交待办任务） */
    public static final String USER_DISABLED = "USER_DISABLED";

    /** 用户登录 */
    public static final String USER_LOGIN = "USER_LOGIN";

    /** 用户登出 */
    public static final String USER_LOGOUT = "USER_LOGOUT";

    /** 角色变更（消费方: ydsz-gateway → 权限缓存刷新） */
    public static final String ROLE_CHANGED = "ROLE_CHANGED";

    /** 组织/部门变更（消费方: ydsz-workflow → 审批人解析缓存刷新） */
    public static final String ORG_STRUCTURE_CHANGED = "ORG_STRUCTURE_CHANGED";

    // ==================== 告警事件（发布方: 各模块） ====================

    /** 统一告警事件（消费方: ydsz-message → 多通道告警派发） */
    public static final String UNIFIED_ALERT = "UNIFIED_ALERT";

    // ==================== 定时任务事件（发布方: ydsz-cronjob） ====================

    /** 定时任务执行失败（消费方: ydsz-message） */
    public static final String JOB_EXECUTION_FAILED = "JOB_EXECUTION_FAILED";

    /** 定时任务执行成功 */
    public static final String JOB_EXECUTION_SUCCESS = "JOB_EXECUTION_SUCCESS";

    /** 定时任务执行超时（消费方: ydsz-message） */
    public static final String JOB_TIMEOUT = "JOB_TIMEOUT";

    /** 任务创建 */
    public static final String JOB_CREATED = "JOB_CREATED";

    /** 任务删除 */
    public static final String JOB_DELETED = "JOB_DELETED";

    /** DAG 节点完成 */
    public static final String DAG_NODE_COMPLETED = "DAG_NODE_COMPLETED";

    // ==================== 权限变更事件（发布方: ydsz-system） ====================

    /** 权限变更（消费方: ydsz-gateway → 权限缓存刷新） */
    public static final String PERMISSION_CHANGED = "PERMISSION_CHANGED";

    // ==================== 配置变更事件（发布方: ydsz-system） ====================

    /** 系统配置变更（消费方: 各模块 → 配置热刷新） */
    public static final String CONFIG_CHANGED = "CONFIG_CHANGED";

    /** 字典变更 */
    public static final String DICT_CHANGED = "DICT_CHANGED";

    /** 系统变量变更 */
    public static final String VARIABLE_CHANGED = "VARIABLE_CHANGED";

    // ==================== 审计事件（发布方: 各模块） ====================

    /** 操作日志（消费方: ydsz-system → 审计日志持久化） */
    public static final String OPERATION_LOG = "OPERATION_LOG";

    /** 数据导出审计（消费方: ydsz-system → 导出审计记录） */
    public static final String DATA_EXPORT_AUDIT = "DATA_EXPORT_AUDIT";

    // ==================== 项目事件（发布方: ydsz-project） ====================

    /** 项目立项创建（消费方: ydsz-workflow → 创建审批流程） */
    public static final String PROJECT_INITIATION_CREATED = "PROJECT_INITIATION_CREATED";

    /** 项目立项审批通过（消费方: ydsz-project → 状态机推进；ydsz-workflow → 流程归档） */
    public static final String PROJECT_INITIATION_APPROVED = "PROJECT_INITIATION_APPROVED";

    /** 项目阶段变更 */
    public static final String PROJECT_STAGE_CHANGED = "PROJECT_STAGE_CHANGED";

    /** 项目关闭 */
    public static final String PROJECT_CLOSED = "PROJECT_CLOSED";

    /** 项目合同签订 */
    public static final String PROJECT_CONTRACT_SIGNED = "PROJECT_CONTRACT_SIGNED";

    // ==================== Agent 事件（发布方: ydsz-agent） ====================

    /** 对话创建 */
    public static final String CONVERSATION_CREATED = "CONVERSATION_CREATED";

    /** Agent 审批请求 */
    public static final String AGENT_APPROVAL_REQUESTED = "AGENT_APPROVAL_REQUESTED";

    /** Agent 执行启动（消费方: ydsz-message → 执行状态通知；ydsz-cronjob → 异步任务跟踪） */
    public static final String AGENT_EXECUTION_STARTED = "AGENT_EXECUTION_STARTED";

    /** Agent 执行完成（消费方: ydsz-message → 结果通知） */
    public static final String AGENT_EXECUTION_COMPLETED = "AGENT_EXECUTION_COMPLETED";

    /** Agent 执行失败（消费方: ydsz-message → 告警；ydsz-cronjob → 重试调度） */
    public static final String AGENT_EXECUTION_FAILED = "AGENT_EXECUTION_FAILED";

    // ==================== 规则事件（发布方: ydsz-literule） ====================

    /** 规则发布（消费方: ydsz-literule → 规则缓存刷新；各模块 → 规则版本感知） */
    public static final String RULE_PUBLISHED = "RULE_PUBLISHED";

    /** 规则停用（消费方: ydsz-literule → 规则缓存清理） */
    public static final String RULE_DISABLED = "RULE_DISABLED";

    /** 规则配置变更 */
    public static final String RULE_CONFIG_CHANGED = "RULE_CONFIG_CHANGED";

    // ==================== 知识库事件（发布方: ydsz-nextwiki） ====================

    /** 文档变更（消费方: ydsz-agent → RAG 索引增量更新；ydsz-search → 全文索引刷新） */
    public static final String WIKI_DOCUMENT_CHANGED = "WIKI_DOCUMENT_CHANGED";

    // ==================== 文件事件（发布方: ydsz-nextwiki） ====================

    /** 文件上传完成 */
    public static final String FILE_UPLOADED = "FILE_UPLOADED";

    /** 文件删除 */
    public static final String FILE_DELETED = "FILE_DELETED";

    /** 文件分享 */
    public static final String FILE_SHARED = "FILE_SHARED";

    // ==================== 消息事件（发布方: ydsz-message） ====================

    /** 消息发送 */
    public static final String MESSAGE_SENT = "MESSAGE_SENT";

    /** 消息撤回 */
    public static final String MESSAGE_RECALLED = "MESSAGE_RECALLED";
}
