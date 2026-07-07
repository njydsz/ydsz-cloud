package com.njydsz.pmis.workflow.service;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 待办任务 Service
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowTaskService {

    /**
     * 创建任务
     */
    String createTask(String instanceId, FlowNodeDO node, Map<String, Object> variables);

    /**
     * P2-20: 按 ID 查任务（任务详情查询）
     *
     * @param taskId 任务 ID
     * @return 任务 DO，不存在返回 null
     */
    FlowRunTaskDO getById(String taskId);

    /**
     * 签收
     */
    void claim(String taskId, String userId);

    /**
     * 通过
     */
    void pass(FlowTaskOperateDTO dto);

    /**
     * 驳回
     */
    void reject(FlowTaskOperateDTO dto);

    /**
     * 转办
     */
    void transfer(FlowTaskOperateDTO dto);

    /**
     * 委派
     */
    void delegate(FlowTaskOperateDTO dto);

    /**
     * 取消某实例的全部 PENDING 任务（终止/驳回终态时使用）
     */
    void cancelByInstance(String instanceId, String reason);

    /**
     * 查实例的当前 PENDING 任务
     */
    List<FlowRunTaskDO> listPendingByInstance(String instanceId);

    /**
     * 查用户的待办
     */
    List<FlowRunTaskDO> listTodoByAssignee(String assigneeId, String tenantId);

    /**
     * P2-17: 查用户的待办（真分页：SQL LIMIT/OFFSET）
     *
     * @param assigneeId 办理人 ID
     * @param tenantId   租户 ID
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @return 分页结果
     */
    PageResult<FlowRunTaskDO> listTodoByAssigneePage(String assigneeId, String tenantId,
                                                   int page, int size);

    /**
     * 查用户的已办
     */
    List<FlowRunTaskDO> listDoneByAssignee(String assigneeId, String tenantId);

    /**
     * P2-17: 查用户的已办（真分页：SQL LIMIT/OFFSET）
     *
     * @param assigneeId 办理人 ID
     * @param tenantId   租户 ID
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @return 分页结果
     */
    PageResult<FlowRunTaskDO> listDoneByAssigneePage(String assigneeId, String tenantId,
                                                   int page, int size);

    /**
     * 查用户的待办（多维度匹配：直接分配 + ROLE/DEPT 展开 + pmis_flow_user 关联）
     *
     * @param userId    用户 ID
     * @param roleCodes 用户拥有的角色编码（可空）
     * @param deptIds   用户所属部门 ID（字符串形式，可空）
     * @param tenantId  租户 ID（可空，默认 1L）
     */
    List<FlowRunTaskDO> listTodoByUser(String userId, List<String> roleCodes,
                                     List<String> deptIds, String tenantId);

    /**
     * P1-7: 前加签 — 在当前节点前插入临时审批人
     */
    void countersignBefore(FlowTaskOperateDTO dto);

    /**
     * P1-7: 后加签 — 在当前节点通过后、下一节点前插入临时审批人
     */
    void countersignAfter(FlowTaskOperateDTO dto);

    /**
     * GAP-P0-3: 并加签 — 动态追加审批人与原审批人并行审批，所有人审完后才推进。
     *
     * <p>对标钉钉/飞书"并加签"语义。当前审批人尚未审批时动态追加，
     * 加签人与原审批人<b>并行</b>审批（performType 强制切换为 PARALLEL），
     * 所有人全部通过后才推进到下一节点。
     *
     * <p>与 {@link #countersignAfter}（后加签，SEQUENTIAL 顺序）的区别：
     * 后加签是"当前人审完→加签人审"的串行流程；并加签是"当前人+加签人同时审"的并行流程。
     *
     * @param dto 任务操作参数（需含 taskId + targetUserId + targetUserName）
     * @since 1.6.0
     */
    void countersignParallel(FlowTaskOperateDTO dto);

    /**
     * GAP-P1: 减签 — 从会签任务中移除指定审批人
     *
     * <p>对标钉钉/飞书的"减签"功能。从 pmis_flow_user 中删除指定用户，
     * 并更新任务的 approveCount（应到人数）。
     *
     * @param dto 任务操作参数（需含 taskId + userId 为被减签人）
     */
    void countersignRemove(FlowTaskOperateDTO dto);

    /**
     * GAP-P2: 已阅 — 标记任务已阅（不改变任务状态，仅记录审计日志）
     *
     * @param taskId 任务 ID
     * @param userId 操作人 ID
     */
    void markRead(String taskId, String userId);

    /**
     * GAP-P2: 沟通 — 在任务下添加沟通评论（不改变任务状态）
     *
     * @param dto 任务操作参数（需含 taskId + userId + comment）
     */
    void communicate(FlowTaskOperateDTO dto);

    /**
     * P1-9: 催办 — 通知当前节点所有待办处理人
     *
     * @return 被催办人 ID 列表
     */
    List<String> urge(String instanceId, String operatorId, String comment);

    /**
     * P2-3 (GAP-13): 节点级催办 — 仅催办指定节点（nodeCode）的待办任务
     *
     * <p>当 nodeCode 为 null 或空时，退化为 {@link #urge} 的实例级催办行为。
     *
     * @param instanceId 实例 ID
     * @param nodeCode   节点编码（指定则只催办该节点的待办）
     * @param operatorId 催办人 ID
     * @param comment    催办说明
     * @return 被催办人 ID 列表
     */
    List<String> urgeByNode(String instanceId, String nodeCode, String operatorId, String comment);

    /**
     * P2-25: 自由跳转 — 管理员强制跳转到任意节点
     *
     * <p>完成当前任务、取消同实例其他 PENDING 任务、在目标节点创建新任务。
     *
     * @param dto 任务操作参数（需含 taskId + targetNodeCode）
     */
    void jump(FlowTaskOperateDTO dto);

    /**
     * P2-26: 批量审批 — 对多个任务逐一执行 pass，@Transactional 保证原子性
     *
     * @param taskIds 任务 ID 列表
     * @param userId  操作人 ID
     * @param comment 审批意见
     */
    void batchPass(List<String> taskIds, String userId, String comment);

    /**
     * P1-4: 批量驳回 — 对多个任务逐一执行 reject，@Transactional 保证原子性。
     *
     * @param taskIds        任务 ID 列表
     * @param userId         操作人 ID
     * @param comment        审批意见
     * @param targetNodeCode 退回目标节点编码（可选，为空时走默认退回逻辑）
     */
    void batchReject(List<String> taskIds, String userId, String comment, String targetNodeCode);

    /**
     * P1-4: 批量转办 — 对多个任务逐一执行 transfer，@Transactional 保证原子性。
     *
     * @param taskIds        任务 ID 列表
     * @param userId         操作人 ID
     * @param comment        转办说明
     * @param targetUserId   目标人 ID
     * @param targetUserName 目标人姓名
     */
    void batchTransfer(List<String> taskIds, String userId, String comment,
                       String targetUserId, String targetUserName);

    /**
     * P1-4: 批量催办 — 对多个实例逐一执行 urge，单个失败不影响其他。
     *
     * @param instanceIds 实例 ID 列表
     * @param operatorId  操作人 ID
     * @param comment     催办说明
     * @return 成功催办的实例数量
     */
    int batchUrge(List<String> instanceIds, String operatorId, String comment);

    /**
     * 转视图
     */
    FlowInstanceViewDTO.FlowTaskViewDTO toView(FlowRunTaskDO task);

    /**
     * P2-31: 按节点统计平均耗时（GROUP BY node_code, node_name）
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID（可空）
     * @return 每个节点一行统计：nodeCode, nodeName, avgDurationMs, count
     */
    List<Map<String, Object>> nodeDurationStats(String flowCode, String tenantId);

    /**
     * P2-32: 查询超期任务（dueAt < now 且状态为 PENDING/CLAIMED）
     *
     * @param assigneeId 办理人 ID（可空，为空时查全部）
     * @param tenantId   租户 ID（可空）
     * @return 超期任务列表
     */
    List<FlowRunTaskDO> listOverdue(String assigneeId, String tenantId);

    /**
     * P2-32: 统计超期任务数量
     *
     * @param assigneeId 办理人 ID（可空，为空时统计全部）
     * @param tenantId   租户 ID（可空）
     * @return 超期任务数量
     */
    long countOverdue(String assigneeId, String tenantId);

    /**
     * P2-4: 统计待办任务总数（PENDING + CLAIMED）
     *
     * @param tenantId 租户 ID（可空）
     * @return 待办任务数量
     */
    long countPending(String tenantId);

    /**
     * P2-33: 已办多维筛选分页查询（真分页：SQL LIMIT/OFFSET）
     *
     * @param assigneeId   办理人 ID（可空）
     * @param businessType 业务类型（可空）
     * @param flowCode     流程编码（可空）
     * @param startTime    完成时间下界（可空）
     * @param endTime      完成时间上界（可空）
     * @param tenantId     租户 ID（可空）
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @return 分页结果
     */
    PageResult<FlowRunTaskDO> listDoneByAssigneePageMulti(String assigneeId, String businessType,
                                                       String flowCode, LocalDateTime startTime,
                                                       LocalDateTime endTime, String tenantId,
                                                       int page, int size);

    /**
     * P2-36: 标记任务超时
     *
     * <p>校验任务状态为 PENDING/CLAIMED，更新为 TIMEOUT，写审计日志并触发 onTaskTimeout 事件。
     * 当前仅实现标记超时 + 触发事件，节点超时策略（自动通过/自动驳回/仅提醒）后续扩展。
     *
     * @param taskId 任务 ID
     * @param reason 超时原因（可选）
     */
    void timeoutTask(String taskId, String reason);

    // ======================== P0-03: 暂存待审 / 追加处理人 ========================

    /**
     * GAP-P0: 暂存待审 — 审批人保存审批意见草稿（不改变任务主状态）
     *
     * <p>将审批意见保存到任务 comment 字段，任务状态保持 PENDING/CLAIMED 不变，
     * 写审计日志记录 SAVE_DRAFT 操作。对标飞书/钉钉审批的"暂存"功能。
     *
     * @param dto 任务操作参数（需含 taskId + userId + comment）
     */
    void saveDraft(FlowTaskOperateDTO dto);

    /**
     * GAP-P0: 追加处理人 — 在已有会签任务中追加一个审批人
     *
     * <p>对标 FlowString 的"追加处理人"功能。向 pmis_flow_user 插入新审批人，
     * approveCount +1，保持当前会签模式不变。比加签更轻量，不改变 performType。
     *
     * @param dto 任务操作参数（需含 taskId + targetUserId + targetUserName）
     */
    void addApprover(FlowTaskOperateDTO dto);

    /**
     * P1-3: 取回 — 审批人已审批后，在下一节点未处理前，把自己的审批撤回。
     *
     * <p>对标钉钉/飞书"取回"能力。与发起人撤回（{@link com.njydsz.pmis.workflow.service.FlowInstanceService#recall}) 不同，
     * 取回是<b>审批人</b>维度：审批人已 PASS 后，下一节点尚未处理时，可取回自己的审批，
     * 流程退回到审批人所在节点重新审批。
     *
     * <p>校验规则：
     * <ul>
     *   <li>历史任务存在且 taskStatus=COMPLETED；</li>
     *   <li>操作人必须是历史任务的办理人（assigneeId）；</li>
     *   <li>实例状态为 RUNNING；</li>
     *   <li>下一节点的待办任务必须全部为 PENDING（未签收/未完成）。</li>
     * </ul>
     *
     * @param hisTaskId 历史任务 ID（pmis_flow_his_task.id）
     * @param operatorId 操作人 ID（校验与 hisTask.assigneeId 一致）
     * @param comment 取回说明（可选）
     * @return 新创建的待办任务 ID
     * @since 1.6.0
     */
    String retract(String hisTaskId, String operatorId, String comment);
}
