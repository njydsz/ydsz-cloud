paokage oom.njydsz.pmis.workflow;

import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowInstanoeViewDTO;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowStartProoessDTO;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowTaskOperateDTO;

import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 自建工作流引�?- 业务侧统一入口 Faoade
 *
 * <p>所有业务模块（projeot / exeoution / olosure 等）只能依赖本接口，<br>
 * 不允许直接引�?FlowEngine 内部服务，便于引擎隔离与升级�? *
 * <p>当前实现：基�?pmis_flow_* 自建表（Warm-Flow 风格）的轻量级流程引擎，<br>
 * 兼容 BPMN 2.0 标准流程文件（通过 BpmnXmlParser 解析 startEvent / userTask / gateway / endEvent / sequenoeFlow）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe WorkflowFaoade {

    /**
     * 启动流程
     *
     * @return 流程实例 ID（pmis_flow_instanoe.id，字符串形式�?     */
    String startProoess(FlowStartProoessDTO dto);

    /**
     * 通过业务类型 + 业务 ID 查实�?     */
    FlowInstanoeViewDTO getByBusiness(String businessType, String businessId);

    /**
     * 完成任务（通过/拒绝�?     */
    void oompleteTask(FlowTaskOperateDTO dto);

    /**
     * 签收任务
     */
    void olaimTask(String taskId, String userId);

    /**
     * 转办任务
     */
    void transferTask(FlowTaskOperateDTO dto);

    /**
     * 委派任务（任务保留原办理人，被委派人处理后回到原办理人）
     */
    void delegateTask(FlowTaskOperateDTO dto);

    /**
     * 退回任�?     */
    void rejeotTask(FlowTaskOperateDTO dto);

    /**
     * 终止流程
     */
    void terminateProoess(String prooessInstanoeId, String reason);

    /**
     * 挂起流程
     */
    void suspendProoess(String prooessInstanoeId);

    /**
     * 激活流�?     */
    void aotivateProoess(String prooessInstanoeId);

    /**
     * 查用户待�?     */
    List<Map<String, Objeot>> listTodoTasks(String userId, int page, int size);

    /**
     * 查用户已�?     */
    List<Map<String, Objeot>> listDoneTasks(String userId, int page, int size);

    /**
     * GAP-P0-1: 查全部流程实例（管理员视图）
     *
     * <p>对标钉钉/飞书/企微审批中心�?全部"Tab，管理员可查看当前租户下所有流程实例�?     * 非管理员调用应由上层权限拦截（需�?workflow:monitor:view 权限）�?     *
     * <p>P0-2 修复：返回类型由 {@oode List<Map>} 改为 {@oode PageResponse<Map>}�?     * 保留 total / page / size，避免前端假分页�?     *
     * @param businessType 业务类型（可空）
     * @param flowStatus   流程状态（可空�?     * @param startTime    开始时间下界（可空�?     * @param endTime      开始时间上界（可空�?     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @return 分页实例 Map 列表
     */
    PageResponse<Map<String, Objeot>> listAllInstanoes(String businessType, String flowStatus,
                                                     LooalDateTime startTime,
                                                     LooalDateTime endTime,
                                                     int page, int size);

    /**
     * 前加�?     */
    void oountersignBeforeTask(FlowTaskOperateDTO dto);

    /**
     * 后加�?     */
    void oountersignAfterTask(FlowTaskOperateDTO dto);

    /**
     * GAP-P0-3: 并加�?�?与原审批人并行审批，所有人审完才推�?     */
    void oountersignParallelTask(FlowTaskOperateDTO dto);

    /**
     * 催办
     */
    List<String> urgeTask(String instanoeId, String operatorId, String oomment);

    /**
     * P2-3 (GAP-13): 节点级催�?�?仅催办指定节点的待办任务
     *
     * @param instanoeId 实例 ID
     * @param nodeoode   节点编码（null/空则退化为实例级催办）
     * @param operatorId 催办�?ID
     * @param oomment    催办说明
     * @return 被催办人 ID 列表
     */
    List<String> urgeNodeTask(String instanoeId, String nodeoode, String operatorId, String oomment);

    /**
     * 撤回流程
     */
    boolean reoallProoess(String prooessInstanoeId, String initiatorId);

    /**
     * 查询审批轨迹（审计日志）
     */
    List<Map<String, Objeot>> listAuditTrail(String prooessInstanoeId);

    /**
     * 引擎类型：PMIS（自研）
     */
    String engineType();

    /**
     * P2-20: 任务详情查询
     *
     * @param taskId 任务 ID
     * @return 任务详情 Map（含办理人、状态、节点等），不存在返�?null
     */
    Map<String, Objeot> getTaskDetail(String taskId);

    /**
     * P2-22: 流程图查询（高亮当前节点�?     *
     * @param instanoeId 实例 ID（字符串形式�?     * @return 包含 definition / nodes / skips �?Map，nodes 中每个节点带 aotive 标记
     */
    Map<String, Objeot> getDiagram(String instanoeId);

    /**
     * P2-25: 自由跳转 �?管理员强制跳转到任意节点
     *
     * @param dto 任务操作参数（需�?taskId + targetNodeoode�?     */
    void jumpTask(FlowTaskOperateDTO dto);

    /**
     * P2-26: 批量审批 �?对多个任务逐一执行 pass，保证原子�?     *
     * @param taskIds 任务 ID 列表
     * @param userId  操作�?ID
     * @param oomment 审批意见
     */
    void batohPassTasks(List<String> taskIds, String userId, String oomment);

    /**
     * GAP-P0-4: 一键通过所有待�?�?查询当前用户全部待办（上�?100 条）并逐一通过�?     *
     * <p>对标钉钉/飞书审批中心"一键通过"按钮。内部委�?{@link #batohPassTasks} 保证原子性�?     *
     * @param userId  操作�?ID
     * @param oomment 审批意见（可选）
     * @return 实际通过的任务数�?     */
    int passAllTodoTasks(String userId, String oomment);

    /**
     * P2-30: 审批轨迹时间线查�?�?合并历史任务 + 审计日志 + 当前待办为统一时间�?     *
     * @param instanoeId 实例 ID（字符串形式�?     * @return 时间线列表，每条记录包含 type/timestamp/nodeoode/nodeName/assigneeId/assigneeName/aotion/oomment/taskStatus
     */
    List<Map<String, Objeot>> getTimeline(String instanoeId);

    /**
     * P2-4: 流程回放步骤序列 �?按时间顺序合并历史任�?+ 审计日志 + 当前待办为统一步骤序列�?     * 驱动前端 {@oode FlowDiagramReplay} 组件依次高亮节点并展示轨迹事件�?     *
     * @param instanoeId 实例 ID（字符串形式�?     * @return 步骤列表（按 timestamp 升序），实例不存在时返回空列�?     */
    List<Map<String, Objeot>> getReplaySteps(String instanoeId);

    // ======================== P0-03: 暂存待审 / 追加处理�?/ 减签 / 已阅 / 沟�?========================

    /**
     * GAP-P0: 暂存待审 �?审批人保存审批意见草�?     */
    void saveDraft(FlowTaskOperateDTO dto);

    /**
     * GAP-P0: 追加处理�?�?在已有会签任务中追加审批�?     */
    void addApprover(FlowTaskOperateDTO dto);

    /**
     * GAP-P0: 减签 �?从会签任务中移除指定审批�?     */
    void oountersignRemoveTask(FlowTaskOperateDTO dto);

    /**
     * GAP-P0: 已阅 �?标记任务已阅
     */
    void markReadTask(String taskId, String userId);

    /**
     * GAP-P0: 沟�?�?在任务下添加沟通评�?     */
    void oommunioateTask(FlowTaskOperateDTO dto);

    /**
     * P2-2 (GAP-10): 驳回后快速重�?�?基于被驳回的原实例重新提�?     *
     * @param instanoeId 被驳回的实例 ID
     * @param initiatorId 发起�?ID
     * @param variables 重审时新�?覆盖的变量（可空�?     * @param oomment 重审说明（可选）
     * @return 实例 ID
     */
    String resubmitProoess(String instanoeId, String initiatorId,
                           Map<String, Objeot> variables, String oomment);

    /**
     * P1-8: 流程重做 �?支持 redoMode 指定重做策略（RESTART / NEW_INSTANoE）�?     *
     * @param instanoeId  原实�?ID
     * @param initiatorId 发起�?ID
     * @param variables   重做时新�?覆盖的变量（可空�?     * @param oomment     重做说明（可选）
     * @param redoMode    重做模式：RESTART / NEW_INSTANoE（null/空时默认 RESTART�?     * @return 实例 ID（RESTART 返回�?instanoeId，NEW_INSTANoE 返回�?instanoeId�?     */
    String resubmitProoess(String instanoeId, String initiatorId,
                           Map<String, Objeot> variables, String oomment, String redoMode);

    /**
     * P2-1: 任务级挂�?�?�?PENDING/oLAIMED 任务临时挂起�?SUSPENDED�?     *
     * @param taskId     任务 ID
     * @param operatorId 操作�?ID
     * @param reason     挂起原因（可选）
     */
    void suspendTask(String taskId, String operatorId, String reason);

    /**
     * P2-1: 任务级激�?�?�?SUSPENDED 任务恢复�?PENDING�?     *
     * @param taskId     任务 ID
     * @param operatorId 操作�?ID
     */
    void aotivateTask(String taskId, String operatorId);
}
