package com.njydsz.pmis.workflow.flow.service;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;

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
    Long createTask(Long instanceId, FlowNodeDO node, Map<String, Object> variables);

    /**
     * P2-20: 按 ID 查任务（任务详情查询）
     *
     * @param taskId 任务 ID
     * @return 任务 DO，不存在返回 null
     */
    FlowTaskDO getById(Long taskId);

    /**
     * 签收
     */
    void claim(Long taskId, Long userId);

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
    void cancelByInstance(Long instanceId, String reason);

    /**
     * 查实例的当前 PENDING 任务
     */
    List<FlowTaskDO> listPendingByInstance(Long instanceId);

    /**
     * 查用户的待办
     */
    List<FlowTaskDO> listTodoByAssignee(String assigneeId, Long tenantId);

    /**
     * P2-17: 查用户的待办（真分页：SQL LIMIT/OFFSET）
     *
     * @param assigneeId 办理人 ID
     * @param tenantId   租户 ID
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @return 分页结果
     */
    PageResult<FlowTaskDO> listTodoByAssigneePage(String assigneeId, Long tenantId,
                                                   int page, int size);

    /**
     * 查用户的已办
     */
    List<FlowTaskDO> listDoneByAssignee(String assigneeId, Long tenantId);

    /**
     * P2-17: 查用户的已办（真分页：SQL LIMIT/OFFSET）
     *
     * @param assigneeId 办理人 ID
     * @param tenantId   租户 ID
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @return 分页结果
     */
    PageResult<FlowTaskDO> listDoneByAssigneePage(String assigneeId, Long tenantId,
                                                   int page, int size);

    /**
     * 查用户的待办（多维度匹配：直接分配 + ROLE/DEPT 展开 + pmis_flow_user 关联）
     *
     * @param userId    用户 ID
     * @param roleCodes 用户拥有的角色编码（可空）
     * @param deptIds   用户所属部门 ID（字符串形式，可空）
     * @param tenantId  租户 ID（可空，默认 1L）
     */
    List<FlowTaskDO> listTodoByUser(Long userId, List<String> roleCodes,
                                     List<String> deptIds, Long tenantId);

    /**
     * P1-7: 前加签 — 在当前节点前插入临时审批人
     */
    void countersignBefore(FlowTaskOperateDTO dto);

    /**
     * P1-7: 后加签 — 在当前节点通过后、下一节点前插入临时审批人
     */
    void countersignAfter(FlowTaskOperateDTO dto);

    /**
     * P1-9: 催办 — 通知当前节点所有待办处理人
     *
     * @return 被催办人 ID 列表
     */
    List<String> urge(Long instanceId, Long operatorId, String comment);

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
    void batchPass(List<Long> taskIds, Long userId, String comment);

    /**
     * 转视图
     */
    FlowInstanceViewDTO.FlowTaskViewDTO toView(FlowTaskDO task);

    /**
     * P2-31: 按节点统计平均耗时（GROUP BY node_code, node_name）
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID（可空）
     * @return 每个节点一行统计：nodeCode, nodeName, avgDurationMs, count
     */
    List<Map<String, Object>> nodeDurationStats(String flowCode, Long tenantId);

    /**
     * P2-32: 查询超期任务（dueAt < now 且状态为 PENDING/CLAIMED）
     *
     * @param assigneeId 办理人 ID（可空，为空时查全部）
     * @param tenantId   租户 ID（可空）
     * @return 超期任务列表
     */
    List<FlowTaskDO> listOverdue(String assigneeId, Long tenantId);

    /**
     * P2-32: 统计超期任务数量
     *
     * @param assigneeId 办理人 ID（可空，为空时统计全部）
     * @param tenantId   租户 ID（可空）
     * @return 超期任务数量
     */
    long countOverdue(String assigneeId, Long tenantId);

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
    PageResult<FlowTaskDO> listDoneByAssigneePageMulti(String assigneeId, String businessType,
                                                       String flowCode, LocalDateTime startTime,
                                                       LocalDateTime endTime, Long tenantId,
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
    void timeoutTask(Long taskId, String reason);
}
