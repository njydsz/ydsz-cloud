/**
 * 系统管理 Controller 层：对外暴露消息、文件、通知、审计、配置等 HTTP 接口。
 *
 * <p>本包是 PMIS 系统管理模块的 REST API 入口，所有 Controller 统一使用
 * {@code @RestController} + {@code @RequestMapping} 注解，配合 {@code @PrePermission}
 * 注解实现接口级权限控制，通过 {@code swagger-v3}（{@code @Tag}/{@code @Operation}）
 * 自动生成 OpenAPI 文档。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code MessageController} - 消息发送与日志查询（{@code /message/...}）</li>
 *   <li>{@code MessageTemplateController} - 消息模板管理（增删改查 + 启用/停用）</li>
 *   <li>{@code NotificationController} - 站内通知发送、收件箱、已读标记</li>
 *   <li>{@code FileController} / {@code FileEnhanceController} - 文件上传/下载/秒传/分片</li>
 *   <li>{@code ConfigController} - 系统动态配置（基于 Nacos/DB）</li>
 *   <li>{@code FeatureFlagController} - 灰度发布/特性开关管理</li>
 *   <li>{@code OperationLogController} / {@code LoginAuditController} / {@code DataExportAuditController}
 *       - 三类审计日志查询</li>
 *   <li>{@code SensitiveOperationController} - 敏感操作（如二次鉴权/导出）审批</li>
 *   <li>{@code ChaosController} - 混沌工程演练接口（注入延迟/异常/熔断）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>薄 Controller 厚 Service</b>：Controller 仅做参数接收、权限校验、结果封装，
 *       业务逻辑下沉至 {@code service} 层</li>
 *   <li><b>统一响应</b>：所有方法返回 {@code Result<T>}，通过 {@code BizErrorCode} 表达业务错误码</li>
 *   <li><b>参数校验前置</b>：使用 {@code @Valid} + JSR-303 注解在 Controller 层拦截非法入参，
 *       避免脏数据进入 Service</li>
 *   <li><b>权限显式声明</b>：所有接口必须标注 {@code @PrePermission("module:resource:action")}，
 *       缺失注解视为未授权</li>
 *   <li><b>OpenAPI 完备</b>：每个方法须补充 {@code @Operation} summary/description，
 *       参数添加 {@code @Parameter} 说明</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>URL 路径遵循 RESTful 风格：{@code /资源/动作}，动作用动词（{@code send/page/mark}）</li>
 *   <li>分页参数统一命名为 {@code page}（页码，从 1 开始）和 {@code size}（每页条数，最大 100）</li>
 *   <li>禁止在 Controller 中直接访问 Mapper/Repository，所有数据访问必须经 Service</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.web.controller;
