package com.njydsz.common.event.model;

/**
 * 标准领域事件类型常量。
 *
 * <p>各业务模块发布 Outbox 事件时统一引用这些常量，确保事件类型命名一致性，
 * 便于消费端按事件类型路由和过滤。
 *
 * <p>命名规范：{@code MODULE_ENTITY_ACTION}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class StandardEventTypes {

    private StandardEventTypes() {
    }

    // ========== Workflow 事件 ==========
    /** 流程实例启动（发起人提交申请，进入审批流） */
    public static final String FLOW_INSTANCE_STARTED = "FLOW_INSTANCE_STARTED";
    /** 流程实例审批通过（走完所有审批节点，到达结束节点） */
    public static final String FLOW_INSTANCE_APPROVED = "FLOW_INSTANCE_APPROVED";
    /** 流程实例被驳回（被驳回至终止状态） */
    public static final String FLOW_INSTANCE_REJECTED = "FLOW_INSTANCE_REJECTED";
    /** 流程实例被终止（管理员强制终止） */
    public static final String FLOW_INSTANCE_TERMINATED = "FLOW_INSTANCE_TERMINATED";
    /** 流程任务被完成 */
    public static final String FLOW_TASK_COMPLETED = "FLOW_TASK_COMPLETED";
    /** 流程超时催办 */
    public static final String FLOW_URGE_TRIGGERED = "FLOW_URGE_TRIGGERED";

    // ========== Userinfo 事件 ==========
    /** 用户创建 */
    public static final String USER_CREATED = "USER_CREATED";
    /** 用户更新 */
    public static final String USER_UPDATED = "USER_UPDATED";
    /** 用户删除 */
    public static final String USER_DELETED = "USER_DELETED";
    /** 用户登录 */
    public static final String USER_LOGIN = "USER_LOGIN";
    /** 用户登出 */
    public static final String USER_LOGOUT = "USER_LOGOUT";

    // ========== System 事件 ==========
    /** 系统配置变更 */
    public static final String CONFIG_CHANGED = "CONFIG_CHANGED";
    /** 字典变更 */
    public static final String DICT_CHANGED = "DICT_CHANGED";
    /** 系统变量变更 */
    public static final String VARIABLE_CHANGED = "VARIABLE_CHANGED";

    // ========== Cronjob 事件 ==========
    /** 任务执行失败 */
    public static final String JOB_EXECUTION_FAILED = "JOB_EXECUTION_FAILED";
    /** 任务执行成功 */
    public static final String JOB_EXECUTION_SUCCESS = "JOB_EXECUTION_SUCCESS";
    /** DAG 节点完成 */
    public static final String DAG_NODE_COMPLETED = "DAG_NODE_COMPLETED";
    /** 任务超时 */
    public static final String JOB_TIMEOUT = "JOB_TIMEOUT";

    // ========== Nextwiki 事件 ==========
    /** 文件上传完成 */
    public static final String FILE_UPLOADED = "FILE_UPLOADED";
    /** 文件删除 */
    public static final String FILE_DELETED = "FILE_DELETED";
    /** 文件分享 */
    public static final String FILE_SHARED = "FILE_SHARED";

    // ========== Literule 事件 ==========
    /** 规则配置变更 */
    public static final String RULE_CONFIG_CHANGED = "RULE_CONFIG_CHANGED";

    // ========== Agent 事件 ==========
    /** 对话创建 */
    public static final String CONVERSATION_CREATED = "CONVERSATION_CREATED";
    /** Agent 审批请求 */
    public static final String AGENT_APPROVAL_REQUESTED = "AGENT_APPROVAL_REQUESTED";

    // ========== Project 事件 ==========
    /** 项目立项创建 */
    public static final String PROJECT_INITIATION_CREATED = "PROJECT_INITIATION_CREATED";
    /** 项目立项审批通过 */
    public static final String PROJECT_INITIATION_APPROVED = "PROJECT_INITIATION_APPROVED";
    /** 项目阶段变更 */
    public static final String PROJECT_STAGE_CHANGED = "PROJECT_STAGE_CHANGED";
    /** 项目关闭 */
    public static final String PROJECT_CLOSED = "PROJECT_CLOSED";
    /** 项目合同签订 */
    public static final String PROJECT_CONTRACT_SIGNED = "PROJECT_CONTRACT_SIGNED";

    // ========== Userinfo 扩展事件 ==========
    /** 用户启用 */
    public static final String USER_ENABLED = "USER_ENABLED";
    /** 用户禁用 */
    public static final String USER_DISABLED = "USER_DISABLED";
    /** 组织架构变更 */
    public static final String ORG_STRUCTURE_CHANGED = "ORG_STRUCTURE_CHANGED";

    // ========== Message 事件 ==========
    /** 消息发送 */
    public static final String MESSAGE_SENT = "MESSAGE_SENT";
    /** 消息撤回 */
    public static final String MESSAGE_RECALLED = "MESSAGE_RECALLED";

    // ========== Cronjob 扩展事件 ==========
    /** 任务创建 */
    public static final String JOB_CREATED = "JOB_CREATED";
    /** 任务删除 */
    public static final String JOB_DELETED = "JOB_DELETED";
}
