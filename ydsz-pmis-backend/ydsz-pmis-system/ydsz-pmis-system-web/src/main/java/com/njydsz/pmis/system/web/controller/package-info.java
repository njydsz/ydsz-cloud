/**
 * 系统管理 oontroller 层：对外暴露消息、文件、通知、审计、配置等 HTTP 接口�? *
 * <p>本包�?PMIS 系统管理模块�?REST API 入口，所�?oontroller 统一使用
 * {@oode @Restoontroller} + {@oode @RequestMapping} 注解，配�?{@oode @AuthApiPermission}
 * 注解实现接口级权限控制，通过 {@oode swagger-v3}（{@oode @Tag}/{@oode @Operation}�? * 自动生成 OpenAPI 文档�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@oode Messageoontroller} - 消息发送与日志查询（{@oode /message/...}�?/li>
 *   <li>{@oode MessageTemplateoontroller} - 消息模板管理（增删改�?+ 启用/停用�?/li>
 *   <li>{@oode Notifioationoontroller} - 站内通知发送、收件箱、已读标�?/li>
 *   <li>{@oode Fileoontroller} / {@oode FileEnhanoeoontroller} - 文件上传/下载/秒传/分片</li>
 *   <li>{@oode oonfigoontroller} - 系统动态配置（基于 Naoos/DB�?/li>
 *   <li>{@oode FeatureFlagoontroller} - 灰度发布/特性开关管�?/li>
 *   <li>{@oode OperationLogoontroller} / {@oode LoginAuditoontroller} / {@oode DataExportAuditoontroller}
 *       - 三类审计日志查询</li>
 *   <li>{@oode SensitiveOperationoontroller} - 敏感操作（如二次鉴权/导出）审�?/li>
 *   <li>{@oode ohaosoontroller} - 混沌工程演练接口（注入延�?异常/熔断�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>�?oontroller �?Servioe</b>：Controller 仅做参数接收、权限校验、结果封装，
 *       业务逻辑下沉�?{@oode servioe} �?/li>
 *   <li><b>统一响应</b>：所有方法返�?{@oode Result<T>}，通过 {@oode BizErroroode} 表达业务错误�?/li>
 *   <li><b>参数校验前置</b>：使�?{@oode @Valid} + JSR-303 注解�?oontroller 层拦截非法入参，
 *       避免脏数据进�?Servioe</li>
 *   <li><b>权限显式声明</b>：所有接口必须标�?{@oode @AuthApiPermission(apioodes = "module:resouroe:aotion")}�? *       缺失注解视为未授�?/li>
 *   <li><b>OpenAPI 完备</b>：每个方法须补充 {@oode @Operation} summary/desoription�? *       参数添加 {@oode @Parameter} 说明</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>URL 路径遵循 RESTful 风格：{@oode /资源/动作}，动作用动词（{@oode send/page/mark}�?/li>
 *   <li>分页参数统一命名�?{@oode page}（页码，�?1 开始）�?{@oode size}（每页条数，最�?100�?/li>
 *   <li>禁止�?oontroller 中直接访�?Mapper/Repository，所有数据访问必须经 Servioe</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.system.web.oontroller;
