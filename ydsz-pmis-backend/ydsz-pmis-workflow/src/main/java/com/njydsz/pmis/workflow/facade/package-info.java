/**
 * 工作流外观层（对外统一门面）。
 *
 * <p>实现 {@code WorkflowFacade} 接口，作为其他业务模块（Project、Contract、Finance 等）
 * 接入流程引擎的唯一入口。门面屏蔽内部 Service / Engine 拆分，对外暴露粗粒度业务能力
 * （如 {@code startProcess} / {@code getMyTasks} / {@code approve}），保证引擎升级不破坏上游调用。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.workflow.facade.PmisWorkflowFacade} - 自研引擎 v2 门面实现，
 *   内部编排 {@code FlowInstanceService} / {@code FlowTaskService} / Mapper 完成启动、审批、查询等操作</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>门面只做编排，不做核心计算；编排涉及跨 Service 事务时，统一通过 {@code @Transactional} 声明。</li>
 *   <li>对上游屏蔽 pmis_flow_* 实体细节，DTO / ViewDTO 转换在门面内完成。</li>
 *   <li>绝不在门面内引入 HTTP / Web 相关依赖，确保可被任意层（Controller / 内部 RPC）调用。</li>
 *   <li>其他业务模块只能依赖 {@code WorkflowFacade} 接口和本包，<strong>不得直接调用
 *       {@code service} / {@code engine} 包</strong>。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.facade;
