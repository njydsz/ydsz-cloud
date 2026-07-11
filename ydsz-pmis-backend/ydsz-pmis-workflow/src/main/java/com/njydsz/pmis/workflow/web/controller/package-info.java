/**
 * 工作流 REST 控制器层（PC 端）。
 *
 * <p>对外暴露流程引擎 HTTP 接口，承接前端审批中心 / 流程监控 / 流程设计器等
 * 全部交互入口。控制器只做参数校验、权限注解、DTO 转换与返回包装，
 * 不承载任何业务逻辑，复杂行为下沉到 {@code service} 包。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>FlowTaskController - 任务操作（签收 / 通过 / 驳回 / 转办 / 委派 / 加签 / 跳转 / 批量审批 / 待办 / 已办）</li>
 *   <li>FlowInstanceController - 流程实例（启动 / 终止 / 撤销 / 详情 / 进度）</li>
 *   <li>FlowDefinitionController - 流程定义（部署 / 版本 / 启用停用 / 模拟）</li>
 *   <li>FlowDesignerController - 流程设计器数据（保存 / 预览 / 校验）</li>
 *   <li>FlowMonitorController - 流程监控（运行中 / 已结束 / 异常实例聚合视图）</li>
 *   <li>FlowDmnController - DMN 决策表（保存 / 执行 / 命中策略测试）</li>
 *   <li>FlowCcController - 抄送（新增 / 标记已读 / 列表 / 未读数）</li>
 *   <li>FlowSlaController - SLA 配置（阈值 / 升级策略 / 超时统计）</li>
 *   <li>FlowDelegateController - 委派 / 代办授权（新增 / 撤销 / 列表）</li>
 *   <li>FlowTemplateController - 流程模板（我的模板 / 公共模板）</li>
 *   <li>FlowAiGenerateController - AI 流程生成（自然语言 → BPMN）</li>
 *   <li>FlowAutoTriggerController - 自动触发规则（新增 / 启停 / 触发日志）</li>
 *   <li>FlowCanaryController - 灰度发布（按部门 / 按用户 / 比例灰度）</li>
 *   <li>FlowMigrationController - 流程实例迁移（版本升级 / 节点跳转）</li>
 *   <li>FlowEmbeddedApprovalController - 嵌入式审批（外部系统嵌入）</li>
 *   <li>FlowThirdPartyApprovalController - 三方审批（企微 / 钉钉 / 飞书审批回调）</li>
 *   <li>FlowEventController - 事件订阅（Webhook / 业务事件分发）</li>
 *   <li>FlowHistoryArchiveController - 历史归档（手动触发 / 任务查询）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>所有接口统一返回 {@code Result<T>}，分页统一使用 {@code PageResult<T>}。</li>
 *   <li>权限校验通过 {@code @PrePermission} + {@code PermissionCodes} 完成，不在控制器内做条件分支。</li>
 *   <li>参数校验使用 Jakarta Validation（{@code @Valid} / {@code @Validated}）。</li>
 *   <li>本包接口仅服务 PC 端 Web 审批中心，<strong>不适用于移动端 / 独立 H5</strong>。</li>
 *   <li>绝不含电子签章相关接口（合同签署走独立电子签章服务）。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.web.controller;
