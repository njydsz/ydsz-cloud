package com.remisoft.common.feign;

/**
 * Feign 客户端服务名与 URL 路径常量。
 *
 * <p>统一管理所有微服务的 Spring Cloud 服务名和预定义 API 路径，避免各模块硬编码字符串。
 * 新增 Feign 客户端时，在此添加对应的服务名和路径常量。
 *
 * <h3>服务清单</h3>
 * <ul>
 *   <li>{@link #MESSAGE} — 消息中心（通知/短信/邮件/Webhook）</li>
 *   <li>{@link #WORKFLOW} — 工作流引擎（流程定义/实例/审批）</li>
 *   <li>{@link #SALES} — 商务销售（商机/合同/变更/模板）</li>
 *   <li>{@link #FINANCE} — 财务会计（发票/回款/费用/收入/利润/对账）</li>
 *   <li>{@link #AGENT} — AI Agent 服务（编排/工具/知识库）</li>
 *   <li>{@link #CRONJOB} — 定时任务调度（DAG/告警/统计）</li>
 *   <li>{@link #GATEWAY} — API 网关（路由/限流/鉴权）</li>
 *   <li>{@link #USERINFO} — 用户中心（userinfo 模块）</li>
 *   <li>{@link #SYSTEM} — 系统管理</li>
 *   <li>{@link #LITERULE} — 规则引擎（规则定义/编排/评估/灰度/回放）</li>
 * </ul>
 *
 * <h3>URL 路径常量</h3>
 * <ul>
 *   <li>{@link #MESSAGE_PATH_NOTIFICATION_SEND} — 发送通知</li>
 *   <li>{@link #MESSAGE_PATH_NOTIFICATION_PUSH_REALTIME} — 推送实时消息</li>
 *   <li>{@link #MESSAGE_PATH_NOTIFICATION_BROADCAST} — 广播实时消息</li>
 *   <li>{@link #MESSAGE_PATH_SEND} — 发送消息</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class FeignClientConstants {

    private FeignClientConstants() {
    }

    // ==================== 服务名常量 ====================

    /** 消息中心服务名 */
    public static final String MESSAGE = "remi-message";

    /** 工作流引擎服务名 */
    public static final String WORKFLOW = "remi-workflow";

    /** AI Agent 服务名 */
    public static final String AGENT = "remi-agent";

    /** 定时任务调度服务名 */
    public static final String CRONJOB = "remi-cronjob";

    /** API 网关服务名 */
    public static final String GATEWAY = "remi-gateway";

    /** 用户中心服务名（userinfo 模块） */
    public static final String USERINFO = "remi-userinfo";

    /** 用户中心服务名（如有独立部署） */
    public static final String USER_CENTER = "remi-user-center";

    /** 配置中心服务名（如有独立部署） */
    public static final String CONFIG_CENTER = "remi-config-center";

    /** 系统管理服务名 */
    public static final String SYSTEM = "remi-system";

    /** 规则引擎服务名（规则定义/编排/评估/灰度/回放/审批） */
    public static final String LITERULE = "remi-literule";

    /** 网盘知识库服务名 */
    public static final String NEXTWIKI = "remi-nextwiki";

    // ==================== 消息中心 URL 路径常量 ====================

    /** 发送通知 API 路径 */
    public static final String MESSAGE_PATH_NOTIFICATION_SEND = "/api/v1/message/notifications/send";

    /** 推送实时消息 API 路径 */
    public static final String MESSAGE_PATH_NOTIFICATION_PUSH_REALTIME = "/api/v1/message/notifications/push";

    /** 广播实时消息 API 路径 */
    public static final String MESSAGE_PATH_NOTIFICATION_BROADCAST = "/api/v1/message/notifications/broadcast";

    /** 发送消息 API 路径 */
    public static final String MESSAGE_PATH_SEND = "/api/v1/message/send";

    // ==================== 系统管理 URL 路径常量 ====================

    /** 按配置键查询配置值 API 路径 */
    public static final String SYSTEM_PATH_CONFIG_GET = "/api/internal/config/get";

    /** 按类型和编码查询字典项 API 路径 */
    public static final String SYSTEM_PATH_DICT_ITEM = "/api/internal/dict/item";

    /** 按字典类型查询字典项列表 API 路径 */
    public static final String SYSTEM_PATH_DICT_LIST = "/api/internal/dict/list";

    // ==================== AI Agent URL 路径常量 ====================
    // P3-3 TODO: remi-agent 模块尚未创建 InternalApiController，
    // 以下端点需在 agent-web 中实现后才能正常调用

    /** 执行 Agent API 路径（对应 AgentController POST /api/v1/agent/execute） */
    public static final String AGENT_PATH_EXECUTE = "/api/v1/agent/execute";

    // ==================== 网盘知识库 URL 路径常量 ====================
    // P3-3 TODO: remi-nextwiki 模块尚未创建 InternalApiController，
    // 以下端点需在 nextwiki-web 中实现后才能正常调用

    /** 按文件 ID 查询文件信息 API 路径 */
    public static final String NEXTWIKI_PATH_FILE_GET = "/api/internal/file/get";

    /** 获取文件下载 URL API 路径 */
    public static final String NEXTWIKI_PATH_FILE_URL = "/api/internal/file/url";
}
