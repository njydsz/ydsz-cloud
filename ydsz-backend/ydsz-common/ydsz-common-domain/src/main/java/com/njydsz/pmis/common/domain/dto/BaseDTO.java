package com.njydsz.common.domain.dto;

import java.io.Serializable;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 数据传输对象基类
 *
 * <p>所有 DTO 的公共基类，封装跨层传输时的通用上下文信息。
 * 典型子类应继承此类并添加业务字段，而非直接使用。</p>
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>实现 {@link Serializable} 以支持序列化传输</li>
 *   <li>包含操作人、请求ID、追踪ID 等跨切面信息</li>
 *   <li>配合 Lombok {@code @Data} 自动生成 getter/setter</li>
 * </ul>
 *
 * <p><b>通用字段说明：</b>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>operatorId</td><td>String</td><td>操作人ID</td></tr>
 *   <tr><td>operatorName</td><td>String</td><td>操作人姓名</td></tr>
 *   <tr><td>requestId</td><td>String</td><td>请求ID</td></tr>
 *   <tr><td>traceId</td><td>String</td><td>请求追踪ID</td></tr>
 *   <tr><td>tenantId</td><td>String</td><td>租户ID</td></tr>
 *   <tr><td>language</td><td>String</td><td>语言标识</td></tr>
 *   <tr><td>source</td><td>String</td><td>请求来源</td></tr>
 * </table>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class BaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 操作人ID
     *
     * <p>执行当前操作的用户ID，通常从安全上下文中获取。
     * 用于审计日志、数据权限控制等场景。
     */
    private String operatorId;

    /**
     * 操作人姓名
     *
     * <p>执行当前操作的用户姓名，用于日志记录和消息通知。
     * 与 operatorId 配合使用，提供更友好的显示信息。
     */
    private String operatorName;

    /**
     * 请求ID
     *
     * <p>唯一标识一次 HTTP 请求，用于请求追踪和日志关联。
     * 通常由网关或拦截器自动生成。
     */
    private String requestId;

    /**
     * 请求追踪ID
     *
     * <p>分布式链路追踪ID，用于跨服务调用链追踪。
     * 在微服务架构中用于关联多个服务的日志。
     */
    private String traceId;

    /**
     * 租户ID
     *
     * <p>多租户场景下的租户标识，用于数据隔离。
     * 在 SaaS 应用中确保不同租户的数据互不干扰。
     */
    private String tenantId;

    /**
     * 语言标识
     *
     * <p>客户端语言偏好，用于国际化（i18n）处理。
     * 格式：zh-CN, en-US 等。
     * 默认值：zh-CN
     */
    @lombok.Builder.Default
    private String language = "zh-CN";

    /**
     * 请求来源
     *
     * <p>标识请求的来源渠道，用于区分不同入口的请求。
     * 可选值：
     * <ul>
     *   <li>WEB - Web 。</li>
     *   <li>APP - 移动应用</li>
     *   <li>API - 开放 API</li>
     *   <li>SCHEDULE - 定时任务</li>
     *   <li>SYSTEM - 系统内部</li>
     * </ul>
     */
    private String source;

    /**
     * 备注信息
     *
     * <p>用于传递额外的上下文信息，如操作原因、备注说明等。
     * 在审计日志中记录操作原因时特别有用。
     */
    private String remark;

    /**
     * 扩展属性
     *
     * <p>用于传递业务特定的扩展信息，避免频繁修改基类。
     * 使用 Map 结构支持键值对形式的扩展数据。
     */
    private Map<String, Object> extension;
}
