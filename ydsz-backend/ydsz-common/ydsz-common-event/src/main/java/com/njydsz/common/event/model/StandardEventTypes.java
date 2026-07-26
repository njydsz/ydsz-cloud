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
 * @since 1.1.0
 */
public final class StandardEventTypes {

    private StandardEventTypes() {
    }

    // ========== Workflow 事件 ==========
    /** 流程实例审批通过 */
    public static final String FLOW_INSTANCE_APPROVED = "FLOW_INSTANCE_APPROVED";
    /** 流程实例被驳回 */
    public static final String FLOW_INSTANCE_REJECTED = "FLOW_INSTANCE_REJECTED";
    /** 流程实例被终止 */
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
}
