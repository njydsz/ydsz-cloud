/**
 * 数据传输对象（DTO）层：定义 Controller 与 Service 之间、跨服务 Feign 调用的入参/出参契约。
 *
 * <p>本包中的 DTO 严格遵循"输入输出分离"原则：
 * <ul>
 *   <li>Form DTO 接收 HTTP 入参（如 {@code *FormDTO}），承担参数校验与基础约束</li>
 *   <li>Query DTO 承载分页/过滤条件（如 {@code *QueryDTO}）</li>
 *   <li>Feign DTO 跨服务调用专用（如 {@code MessageFeignDTO}），仅暴露必要字段</li>
 * </ul>
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code MessageSendDTO} - 消息发送入参（通道、模板编码、接收人、参数、主题等）</li>
 *   <li>{@code NotificationSendDTO} - 通知发送入参（支持单人/批量、邮件联动）</li>
 *   <li>{@code NotificationQueryDTO} - 通知收件箱查询条件（分类/级别/已读状态/分页）</li>
 *   <li>{@code FileUploadDTO} - 文件上传入参（支持秒传、分片、元信息）</li>
 *   <li>{@code ConfigFormDTO} - 系统动态配置表单</li>
 *   <li>{@code ConfigQueryDTO} - 配置查询条件（按 namespace/key 过滤）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>不可变优先</b>：字段尽量使用 {@code final} + 构造注入；如需 setter 须配 Lombok {@code @Data}，
 *       避免对象被意外篡改</li>
 *   <li><b>实现 Serializable</b>：所有跨网络/跨线程传输的 DTO 必须实现 {@code Serializable}，
 *       显式声明 {@code serialVersionUID}</li>
 *   <li><b>OpenAPI 注解</b>：字段标注 {@code @Schema(description=..., requiredMode=...)}，
 *       自动生成清晰的接口文档</li>
 *   <li><b>Bean Validation</b>：使用 JSR-303 注解（{@code @NotBlank}/{@code @Size}/{@code @Min}）约束字段</li>
 *   <li><b>不掺杂业务</b>：DTO 中禁止出现业务方法（{@code send()/process()} 等），
 *       仅承担数据传输</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>DTO 命名后缀：入参 {@code *FormDTO}、出参 {@code *VO}、查询 {@code *QueryDTO}、
 *       Feign 入参 {@code *FeignDTO}</li>
 *   <li>禁止将 Entity/DO 直接作为入参或返回值，必须经 DTO 转换层隔离</li>
 *   <li>新增字段须同步更新 {@code @Schema} 描述与默认值</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.domain.dto;
