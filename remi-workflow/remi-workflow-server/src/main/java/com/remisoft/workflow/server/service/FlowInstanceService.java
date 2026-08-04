package com.remisoft.workflow.server.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.remisoft.common.core.response.PageResponse;
import com.remisoft.workflow.domain.dto.FlowInstanceViewDTO;
import com.remisoft.workflow.domain.dto.FlowStartProcessDTO;
import com.remisoft.workflow.domain.entity.FlowInstance;
import com.remisoft.workflow.domain.entity.FlowNode;

/**
 * 流程实例 Service
 *
 * <p>流程实例是工作流引擎的<b>运行时核心</b>，本 Service 负责实例的整个生命周期：
 * 启动、查询、终止、挂起/激活、归档、子流程级联等。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>实例生命周期</b>：启动（{@link #start}）/ 终止（{@link #terminate}）/ 挂起（{@link #suspend}）/ 激活（{@link #activate}）/ 完成（{@link #complete}）</li>
 *   <li><b>批量能力</b>：批量启动（{@link #batchStartInstances}）/ 批量终止（{@link #batchTerminate}）</li>
 *   <li><b>查询能力</b>：按 ID / 按业务关联 / 按发起人 / 按状态 / 待办聚合查询</li>
 *   <li><b>子流程</b>：父-子流程级联（终止时自动终止所有子实例）</li>
 *   <li><b>幂等性</b>：{@link #start} 基于 {@code (businessType, businessId)} 唯一索引保证幂等</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>{@link #start} 整体事务（含初始化任务、抄送、审计日志）</li>
 *   <li>{@link #batchStartInstances} 每个 {@link FlowStartProcessDTO} 独立事务</li>
 *   <li>跨服务调用（如业务侧）使用 {@code Spring Event} 解耦，避免大事务</li>
 * </ul>
 *
 * <p><b>性能优化：</b>
 * <ul>
 *   <li>「我的发起」「我的待办」使用 {@code remi_flow_instance} 复合索引（{@code idx_initiator}）</li>
 *   <li>待办列表使用 {@link FlowRunTask} 索引（{@code idx_assignee}）避免 JOIN</li>
 *   <li>实例详情通过冗余字段（{@code initiatorName/currentNodeName}）减少 JOIN</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.workflow.server.service.impl.FlowInstanceServiceImpl 实现类
 * @see FlowTaskService 任务服务（实例推进的核心执行器）
 */
public interface FlowInstanceService {

    /**
     * 启动流程
     *
     * <p>幂等性保证：基于 {@code (businessType, businessId)} 唯一索引，同一业务单据
     * 重复调用时返回原实例 ID，不会产生新实例。
     *
     * <p>启动过程：① 解析流程定义 + 起始节点；② 创建实例（{@link FlowInstance}）；
     * ③ 在起始节点创建待办；④ 发布 {@code FlowStartedEvent} 事件。
     *
     * @param dto 启动参数（flowCode / businessType / businessId / variables / starterId）
     * @return 实例 ID（已存在则返回原 ID）
     */
    String start(FlowStartProcessDTO dto);

    /**
     * P2-6: 批量发起流程实例。
     *
     * <p>对标钉钉/飞书"批量发起审批"能力：一次性提交多个流程实例，每个实例独立事务，
     * 单个失败不影响其他实例的发起。适用于"批量立项"、"批量报销"等场景。
     *
     * <p>行为约定：
     * <ul>
     *   <li>每个 {@link FlowStartProcessDTO} 独立事务，失败记录到 failedItems</li>
     *   <li>返回成功发起的 instanceId 列表 + 失败项明细</li>
     *   <li>限制单次批量最大 100 条（防止事务过多）</li>
     *   <li>幂等性由 {@link #start} 内部保证（同 businessType+businessId 已有 RUNNING 实例时返回原 ID）</li>
     * </ul>
     *
     * @param dtos 流程启动参数列表（不能为空，最多 100 条）
     * @return Map 包含：
     *   <ul>
     *     <li>{@code successCount} (int) — 成功发起数</li>
     *     <li>{@code failedCount} (int) — 失败数</li>
     *     <li>{@code instanceIds} (List&lt;String&gt;) — 成功发起的实例 ID 列表</li>
     *     <li>{@code failedItems} (List&lt;Map&gt;) — 失败项明细，每项含 index / businessId / reason</li>
     *   </ul>
     * @throws SysException 当 dtos 为空或超过 100 条时
     */
    Map<String, Object> batchStartInstances(List<FlowStartProcessDTO> dtos);

    /**
     * 按 ID 查询流程实例
     *
     * @param id 实例 ID
     * @return 流程实例 DO，不存在返回 null
     */
    FlowInstance getById(String id);

    /**
     * 业务关联查询（通过业务类型 + 业务 ID 查实例）
     *
     * <p>幂等启动的核心反查接口：重复发起前先调用此方法判断是否已存在实例。
     *
     * @param businessType 业务类型（如 {@code project_initiation}）
     * @param businessId   业务 ID
     * @return 流程实例 DO，未发起时返回 null
     */
    FlowInstance getByBusiness(String businessType, String businessId);

    /**
     * 终止流程（管理员强制终止）
     *
     * <p>终止时级联取消所有 PENDING 任务、关闭实例、记录审计日志。
     * 子流程实例会被自动级联终止。终止后实例状态变为 TERMINATED。
     *
     * @param instanceId 实例 ID
     * @param reason     终止原因（写入审计日志）
     */
    void terminate(String instanceId, String reason);

    /**
     * P1-8: 批量终止流程实例（含子流程级联终止）
     *
     * <p>终止指定实例列表，同时级联终止所有关联的子流程实例。
     *
     * @param instanceIds 实例 ID 列表
     * @param reason 终止原因
     * @return 实际终止的实例数（含级联子流程）
     */
    int batchTerminate(List<String> instanceIds, String reason);

    /**
     * 挂起流程实例
     *
     * <p>实例挂起后所有 PENDING/CLAIMED 任务变为 FROZEN 状态，
     * 不会推进、不计超时。激活后任务回到原状态继续处理。
     *
     * @param instanceId 实例 ID
     */
    void suspend(String instanceId);

    /**
     * 激活流程实例
     *
     * <p>将已挂起实例的 FROZEN 任务恢复为 PENDING，流程恢复正常推进。
     *
     * @param instanceId 实例 ID
     */
    void activate(String instanceId);

    /**
     * 强制完成（驳回到终态时由调用方使用）
     *
     * <p>主要用于驳回到「结束节点」的终态处理：实例状态变为 COMPLETED，
     * 所有 PENDING 任务被取消。
     *
     * @param instanceId  实例 ID
     * @param endNodeCode 终止节点编码
     */
    void complete(String instanceId, String endNodeCode);

    /**
     * 转化为视图对象（含当前节点任务列表）
     *
     * <p>用于「流程详情」页组装，拼接富化字段（发起人姓名、当前节点名称、审批人姓名）。
     *
     * @param instance     流程实例 DO
     * @param currentTasks 当前节点的待办任务列表
     * @return 流程实例视图 VO
     */
    FlowInstanceViewDTO toView(FlowInstance instance, List<FlowInstanceViewDTO.FlowTaskViewDTO> currentTasks);

    /**
     * 发起人维度查询（我的发起）
     *
     * @param initiatorId 发起人 ID
     * @param flowStatus  流程状态过滤（RUNNING / COMPLETED / REJECTED / TERMINATED，null 表示全部）
     * @return 该发起人指定状态的实例列表
     */
    List<FlowInstance> listByInitiator(String initiatorId, String flowStatus);

    /**
     * P1-8: 撤回流程（仅发起人可撤回，仅运行中可撤回，下一节点未被处理才可撤回）
     *
     * <p>默认撤回到开始节点下游第一节点（重新生成第一批待办）。
     * 如需撤回到任意历史节点，请使用 {@link #recall(String, String, String)}。
     *
     * @param instanceId  实例 ID
     * @param initiatorId 发起人 ID
     * @return 是否撤回成功
     */
    boolean recall(String instanceId, String initiatorId);

    /**
     * P1-1: 撤回到指定历史节点（对标钉钉/飞书"撤回到指定节点"）。
     *
     * <p>与 {@link #recall(String, String)} 的差异：调用方可显式指定退回到任意历史已办节点，
     * 而非仅能撤回到开始节点下游第一节点。
     *
     * <p>校验规则：
     * <ul>
     *   <li>继承 {@link #recall(String, String)} 的全部校验（发起人、运行中、未处理）；</li>
     *   <li>额外校验 targetNodeCode 必须在 {@link #listRecallableNodes(String, String)} 返回的列表中。</li>
     * </ul>
     *
     * @param instanceId     实例 ID
     * @param initiatorId    发起人 ID
     * @param targetNodeCode 目标节点编码（null/空时降级到 {@link #recall(String, String)}）
     * @return 是否撤回成功
     * @since 1.0.0
     */
    boolean recall(String instanceId, String initiatorId, String targetNodeCode);

    /**
     * P1-1: 查询可撤回的历史节点列表。
     *
     * <p>返回当前实例已办过的历史节点（排除当前待办节点），供前端展示"撤回到"选择列表。
     *
     * <p>校验规则：仅发起人可查询，仅运行中实例可查询。
     *
     * @param instanceId  实例 ID
     * @param initiatorId 发起人 ID
     * @return 节点列表，每个 Map 包含 nodeCode / nodeName / firstFinishAt / visitCount
     * @since 1.0.0
     */
    List<Map<String, Object>> listRecallableNodes(String instanceId, String initiatorId);

    /**
     * P2-3: 回滚已完成的流程实例（撤销）
     *
     * <p>对标钉钉/飞书的"撤销审批"能力。已完成（COMPLETED）的流程实例，
     * 在回滚时间窗口内（默认 7 天）可由发起人或管理员撤销：
     * <ul>
     *   <li>实例状态置为 ROLLED_BACK，保留全部历史轨迹；</li>
     *   <li>不再重新推进流程，业务侧（如 ProjectInitiationFlowListener）通过
     *       监听 onInstanceRolledBack 事件执行补偿逻辑；</li>
     *   <li>记录回滚操作人、时间、原因到 variable JSON。</li>
     * </ul>
     *
     * <p>校验规则：
     * <ul>
     *   <li>仅 COMPLETED 状态可回滚（TERMINATED/REJECTED 不可回滚，已驳回/已终止为最终态）；</li>
     *   <li>仅发起人或管理员可回滚；</li>
     *   <li>endAt 距当前时间不得超过 maxRollbackDays（默认 7 天）。</li>
     * </ul>
     *
     * @param instanceId      实例 ID
     * @param operatorId      操作人 ID（发起人或管理员）
     * @param reason          回滚原因
     * @param maxRollbackDays 允许回滚的最大天数（&lt;=0 时使用默认值 7）
     * @return 是否回滚成功
     */
    boolean rollback(String instanceId, String operatorId, String reason, int maxRollbackDays);

    /**
     * P2-23: 实例多维分页查询
     *
     * @param businessType 业务类型（可选）
     * @param initiatorId  发起人 ID（可选）
     * @param flowStatus   流程状态（可选）
     * @param startTime    开始时间下界（可选）
     * @param endTime      开始时间上界（可选）
     * @param tenantId     租户 ID（可选）
     * @param pageNo       页码（从 1 开始）
     * @param pageSize     每页大小
     * @return 分页结果
     */
    PageResponse<FlowInstance> page(String businessType, String initiatorId, String flowStatus,
                                    LocalDateTime startTime, LocalDateTime endTime,
                                    String tenantId, int pageNo, int pageSize);

    /**
     * P2-24: 读取实例流程变量
     *
     * @param instanceId 实例 ID
     * @return 变量 Map，无变量返回空 Map
     */
    Map<String, Object> getVariables(String instanceId);

    /**
     * P2-24: 合并写入单个变量并持久化
     *
     * @param instanceId 实例 ID
     * @param key        变量名
     * @param value      变量值
     */
    void setVariable(String instanceId, String key, Object value);

    /**
     * P2-24: 批量合并写入变量并持久化
     *
     * @param instanceId 实例 ID
     * @param variables  变量 Map
     */
    void setVariables(String instanceId, Map<String, Object> variables);

    /**
     * 引擎内部方法：推进后批量生成任务（供 FlowAdvancer / FlowTaskService 调用）
     *
     * @param instanceId 流程实例 ID
     * @param nextNodes  推进后的下一节点列表
     * @param variables  流程变量
     */
    void generateTasksForNodes(String instanceId, List<FlowNode> nextNodes,
                                Map<String, Object> variables);

    /**
     * GAP-V2-08: 流程模拟运行 — 使用模拟变量驱动引擎走一遍流程，不创建实际实例
     *
     * <p>从开始节点出发，按照 variables 中的条件变量模拟条件判断，依次走过每个节点并记录路径。
     * 遇到 END 节点终止；遇到循环或超过 50 个节点时终止（防死循环）。不写数据库，不创建实例/任务。
     *
     * @param flowCode  流程编码
     * @param version   版本号（可空，默认查最新已发布版本）
     * @param variables 模拟变量
     * @param tenantId  租户 ID（可空，默认从上下文获取）
     * @return 模拟路径列表，每个 Map 包含 step, nodeCode, nodeName, nodeType, assignee, condition, skipped
     */
    List<Map<String, Object>> simulate(String flowCode, String version,
                                        Map<String, Object> variables, String tenantId);

    /**
     * GAP-V2-02: 获取表单渲染数据 — 根据当前任务所在节点返回字段权限配置
     *
     * <p>审批人打开待办时，前端调用本接口获取当前节点的表单字段权限（EDIT/READONLY/HIDDEN），
     * 结合业务表单实现运行时表单渲染。
     *
     * @param instanceId 流程实例 ID
     * @param taskId     当前任务 ID（可空，为空则取实例当前节点的配置）
     * @return Map 包含 nodeCode / nodeName / formFieldsConfig / variables
     */
    Map<String, Object> getFormRenderData(String instanceId, String taskId);

    /**
     * 设置实例的 dueAt 字段（子流程超时处理）
     *
     * @param instanceId 实例 ID
     * @param dueAt      超时时间（传 null 清除超时标记）
     */
    void setDueAt(String instanceId, LocalDateTime dueAt);

    /**
     * P2-2 (GAP-10): 驳回后快速重审
     *
     * <p>实例处于 REJECTED 终态时，发起人可基于原实例直接重新提交（而非全新发起），
     * 引擎保留原有审批轨迹与流程变量，将实例状态恢复为 RUNNING 并从开始节点重新推进。
     *
     * <p>校验规则：
     * <ul>
     *   <li>仅 REJECTED 状态可重审（RUNNING/COMPLETED/TERMINATED/ROLLED_BACK 不可重审）；</li>
     *   <li>仅发起人可重审；</li>
     *   <li>重审时传入的新变量会合并覆盖（保留未覆盖的历史变量）。</li>
     * </ul>
     *
     * @param instanceId 被驳回的实例 ID
     * @param initiatorId 发起人 ID（校验一致性）
     * @param variables  重审时新增/覆盖的变量（可空）
     * @param comment    重审说明（可选）
     * @return 实例 ID
     */
    String resubmit(String instanceId, String initiatorId,
                    Map<String, Object> variables, String comment);

    /**
     * P1-8: 流程重做 — 支持 redoMode 指定重做策略。
     *
     * <p>redoMode 取值：
     * <ul>
     *   <li>{@code RESTART}（默认，向后兼容）：仅 REJECTED 实例可重做，在原实例上重置状态并从开始节点重新推进。
     *       等价于 {@link #resubmit(String, String, Map, String)}。</li>
     *   <li>{@code NEW_INSTANCE}：任意终态（COMPLETED / REJECTED / TERMINATED / ROLLED_BACK）均可重做，
     *       创建全新实例（新 instanceId），复用原实例的 flowCode / businessType / businessId / initiator，
     *       合并原变量与传入变量。原实例保持不变，仅追加一条 REDO 审计日志。</li>
     * </ul>
     *
     * <p>校验规则：
     * <ul>
     *   <li>RESTART 模式：仅 REJECTED 可重做（与原 resubmit 一致）；</li>
     *   <li>NEW_INSTANCE 模式：仅终态可重做（非 RUNNING / SUSPENDED）；</li>
     *   <li>两种模式均要求仅发起人可操作。</li>
     * </ul>
     *
     * @param instanceId  原实例 ID
     * @param initiatorId 发起人 ID（校验一致性）
     * @param variables   重做时新增/覆盖的变量（可空）
     * @param comment     重做说明（可选）
     * @param redoMode    重做模式：RESTART / NEW_INSTANCE（null/空时默认 RESTART）
     * @return 实例 ID（RESTART 返回原 instanceId，NEW_INSTANCE 返回新 instanceId）
     * @since 1.0.0
     */
    String resubmit(String instanceId, String initiatorId,
                    Map<String, Object> variables, String comment, String redoMode);
}
