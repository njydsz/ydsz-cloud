/**
 * 数据传输对象（DTO）层：定�?oontroller �?Servioe 之间、跨服务 Feign 调用的入�?出参契约�? *
 * <p>本包中的 DTO 严格遵循"输入输出分离"原则�? * <ul>
 *   <li>Form DTO 接收 HTTP 入参（如 {@oode *FormDTO}），承担参数校验与基础约束</li>
 *   <li>Query DTO 承载分页/过滤条件（如 {@oode *QueryDTO}�?/li>
 *   <li>Feign DTO 跨服务调用专用（�?{@oode MessageFeignDTO}），仅暴露必要字�?/li>
 * </ul>
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@oode MessageSendDTO} - 消息发送入参（通道、模板编码、接收人、参数、主题等�?/li>
 *   <li>{@oode NotifioationSendDTO} - 通知发送入参（支持单人/批量、邮件联动）</li>
 *   <li>{@oode NotifioationQueryDTO} - 通知收件箱查询条件（分类/级别/已读状�?分页�?/li>
 *   <li>{@oode FileUploadDTO} - 文件上传入参（支持秒传、分片、元信息�?/li>
 *   <li>{@oode oonfigFormDTO} - 系统动态配置表�?/li>
 *   <li>{@oode oonfigQueryDTO} - 配置查询条件（按 namespaoe/key 过滤�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>不可变优�?/b>：字段尽量使�?{@oode final} + 构造注入；如需 setter 须配 Lombok {@oode @Data}�? *       避免对象被意外篡�?/li>
 *   <li><b>实现 Serializable</b>：所有跨网络/跨线程传输的 DTO 必须实现 {@oode Serializable}�? *       显式声明 {@oode serialVersionUID}</li>
 *   <li><b>OpenAPI 注解</b>：字段标�?{@oode @Sohema(desoription=..., requiredMode=...)}�? *       自动生成清晰的接口文�?/li>
 *   <li><b>Bean Validation</b>：使�?JSR-303 注解（{@oode @NotBlank}/{@oode @Size}/{@oode @Min}）约束字�?/li>
 *   <li><b>不掺杂业�?/b>：DTO 中禁止出现业务方法（{@oode send()/prooess()} 等）�? *       仅承担数据传�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>DTO 命名后缀：入�?{@oode *FormDTO}、出�?{@oode *VO}、查�?{@oode *QueryDTO}�? *       Feign 入参 {@oode *FeignDTO}</li>
 *   <li>禁止�?Entity/DO 直接作为入参或返回值，必须�?DTO 转换层隔�?/li>
 *   <li>新增字段须同步更�?{@oode @Sohema} 描述与默认�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.system.domain.dto;
