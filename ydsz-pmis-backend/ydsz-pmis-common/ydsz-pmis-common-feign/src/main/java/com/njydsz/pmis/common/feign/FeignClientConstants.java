package com.njydsz.pmis.common.feign;

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
 *   <li>{@link #PROJECT} — 项目管理（项目/资源/台账）</li>
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
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
public final class FeignClientConstants {

    private FeignClientConstants() {
    }

    // ==================== 服务名常量 ====================

    /** 消息中心服务名 */
    public static final String MESSAGE = "ydsz-pmis-message";

    /** 工作流引擎服务名 */
    public static final String WORKFLOW = "ydsz-pmis-workflow";

    /** 项目管理服务名（项目执行域：立项/WBS/EVM/风险/报表） */
    public static final String PROJECT = "ydsz-pmis-project";

    /** 商务销售服务名（商机/合同/变更/模板） */
    public static final String SALES = "ydsz-pmis-sales";

    /** 财务会计服务名（发票/回款/费用/收入/利润/对账） */
    public static final String FINANCE = "ydsz-pmis-finance";

    /** AI Agent 服务名 */
    public static final String AGENT = "ydsz-pmis-agent";

    /** 定时任务调度服务名 */
    public static final String CRONJOB = "ydsz-pmis-cronjob";

    /** API 网关服务名 */
    public static final String GATEWAY = "ydsz-pmis-gateway";

    /** 用户中心服务名（userinfo 模块） */
    public static final String USERINFO = "ydsz-pmis-userinfo";

    /** 用户中心服务名（如有独立部署） */
    public static final String USER_CENTER = "ydsz-pmis-user-center";

    /** 配置中心服务名（如有独立部署） */
    public static final String CONFIG_CENTER = "ydsz-pmis-config-center";

    /** 系统管理服务名 */
    public static final String SYSTEM = "ydsz-pmis-system";

    /** 规则引擎服务名（规则定义/编排/评估/灰度/回放/审批） */
    public static final String LITERULE = "ydsz-pmis-literule";

    // ==================== 消息中心 URL 路径常量 ====================

    /** 发送通知 API 路径 */
    public static final String MESSAGE_PATH_NOTIFICATION_SEND = "/api/message/notification/send";

    /** 推送实时消息 API 路径 */
    public static final String MESSAGE_PATH_NOTIFICATION_PUSH_REALTIME = "/api/message/notification/push-realtime";

    /** 广播实时消息 API 路径 */
    public static final String MESSAGE_PATH_NOTIFICATION_BROADCAST = "/api/message/notification/broadcast";

    /** 发送消息 API 路径 */
    public static final String MESSAGE_PATH_SEND = "/api/message/send";
}
