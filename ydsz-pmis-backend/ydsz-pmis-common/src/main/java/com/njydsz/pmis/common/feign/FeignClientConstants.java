package com.njydsz.pmis.common.feign;

/**
 * Feign 客户端服务名常量（P2-1 架构优化）。
 *
 * <p>统一管理所有微服务的 Spring Cloud 服务名，避免各模块硬编码字符串。
 * 新增 Feign 客户端时，在此添加对应的服务名常量。
 *
 * <h3>服务清单</h3>
 * <ul>
 *   <li>{@link #MESSAGE} — 消息中心（通知/短信/邮件/Webhook）</li>
 *   <li>{@link #WORKFLOW} — 工作流引擎（流程定义/实例/审批）</li>
 *   <li>{@link #PROJECT} — 项目管理（项目/资源/台账）</li>
 *   <li>{@link #AGENT} — AI Agent 服务（编排/工具/知识库）</li>
 *   <li>{@link #CRONJOB} — 定时任务调度（DAG/告警/统计）</li>
 *   <li>{@link #GATEWAY} — API 网关（路由/限流/鉴权）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P2-1)
 */
public final class FeignClientConstants {

    private FeignClientConstants() {
    }

    /** 消息中心服务名 */
    public static final String MESSAGE = "ydsz-pmis-message";

    /** 工作流引擎服务名 */
    public static final String WORKFLOW = "ydsz-pmis-workflow";

    /** 项目管理服务名 */
    public static final String PROJECT = "ydsz-pmis-project";

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
}
