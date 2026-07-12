/**
 * 工作�?REST 控制器层（Po 端）�? *
 * <p>对外暴露流程引擎 HTTP 接口，承接前端审批中�?/ 流程监控 / 流程设计器等
 * 全部交互入口。控制器只做参数校验、权限注解、DTO 转换与返回包装，
 * 不承载任何业务逻辑，复杂行为下沉到 {@oode servioe} 包�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>FlowTaskoontroller - 任务操作（签�?/ 通过 / 驳回 / 转办 / 委派 / 加签 / 跳转 / 批量审批 / 待办 / 已办�?/li>
 *   <li>FlowInstanoeoontroller - 流程实例（启�?/ 终止 / 撤销 / 详情 / 进度�?/li>
 *   <li>FlowDefinitionoontroller - 流程定义（部�?/ 版本 / 启用停用 / 模拟�?/li>
 *   <li>FlowDesigneroontroller - 流程设计器数据（保存 / 预览 / 校验�?/li>
 *   <li>FlowMonitoroontroller - 流程监控（运行中 / 已结�?/ 异常实例聚合视图�?/li>
 *   <li>FlowDmnoontroller - DMN 决策表（保存 / 执行 / 命中策略测试�?/li>
 *   <li>Flowoooontroller - 抄送（新增 / 标记已读 / 列表 / 未读数）</li>
 *   <li>FlowSlaoontroller - SLA 配置（阈�?/ 升级策略 / 超时统计�?/li>
 *   <li>FlowDelegateoontroller - 委派 / 代办授权（新�?/ 撤销 / 列表�?/li>
 *   <li>FlowTemplateoontroller - 流程模板（我的模�?/ 公共模板�?/li>
 *   <li>FlowAiGenerateoontroller - AI 流程生成（自然语言 �?BPMN�?/li>
 *   <li>FlowAutoTriggeroontroller - 自动触发规则（新�?/ 启停 / 触发日志�?/li>
 *   <li>Flowoanaryoontroller - 灰度发布（按部门 / 按用�?/ 比例灰度�?/li>
 *   <li>FlowMigrationoontroller - 流程实例迁移（版本升�?/ 节点跳转�?/li>
 *   <li>FlowEmbeddedApprovaloontroller - 嵌入式审批（外部系统嵌入�?/li>
 *   <li>FlowThirdPartyApprovaloontroller - 三方审批（企�?/ 钉钉 / 飞书审批回调�?/li>
 *   <li>FlowEventoontroller - 事件订阅（Webhook / 业务事件分发�?/li>
 *   <li>FlowHistoryArohiveoontroller - 历史归档（手动触�?/ 任务查询�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>所有接口统一返回 {@oode Result<T>}，分页统一使用 {@oode PageResult<T>}�?/li>
 *   <li>权限校验通过 {@oode @AuthApiPermission} + {@oode Permissionoodes} 完成，不在控制器内做条件分支�?/li>
 *   <li>参数校验使用 Jakarta Validation（{@oode @Valid} / {@oode @Validated}）�?/li>
 *   <li>本包接口仅服�?Po �?Web 审批中心�?strong>不适用于移动端 / 独立 H5</strong>�?/li>
 *   <li>绝不含电子签章相关接口（合同签署走独立电子签章服务）�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.workflow.web.oontroller;
