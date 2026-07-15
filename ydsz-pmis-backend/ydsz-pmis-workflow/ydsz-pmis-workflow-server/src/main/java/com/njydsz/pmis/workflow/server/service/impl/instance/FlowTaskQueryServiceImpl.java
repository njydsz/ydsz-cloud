package com.njydsz.pmis.workflow.server.service.impl.instance;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.core.response.PageResponse;
import com.njydsz.pmis.common.jdbc.constant.DataSourceConstants;
import com.njydsz.pmis.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.infra.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowUserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 待办任务 — 查询类 Service 实现
 *
 * <p>从原 {@code FlowTaskServiceImpl} 拆分，专注只读查询职责：
 * <ul>
 *   <li>任务详情：{@link #getById(Long)}</li>
 *   <li>待办列表：{@link #listTodoByAssignee} / {@link #listTodoByAssigneePage} / {@link #listTodoByUser}</li>
 *   <li>已办列表：{@link #listDoneByAssignee} / {@link #listDoneByAssigneePage} / {@link #listDoneByAssigneePageMulti}</li>
 *   <li>实例待办：{@link #listPendingByInstance(Long)}</li>
 *   <li>超期统计：{@link #listOverdue} / {@link #countOverdue}</li>
 *   <li>耗时统计：{@link #nodeDurationStats}</li>
 *   <li>视图转换：{@link #toView(FlowRunTaskDO)}</li>
 * </ul>
 *
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS(DataSourceConstants.SLAVE)
@Transactional(readOnly = true)
public class FlowTaskQueryServiceImpl {

    /** 运行时任务 Mapper，查询待办/已办任务列表 */
    private final FlowRunTaskMapper taskMapper;
    /** 历史任务 Mapper，查询已归档的已办任务 */
    private final FlowHisTaskMapper hisTaskMapper;
    /** listTodoByUser 需通过 pmis_flow_user 关联查询任务 */
    private final FlowUserMapper userMapper;

    // ============================== 详情查询 ==============================

    /**
     * P2-20: 按 ID 查任务（任务详情查询）
     *
     * @param taskId 任务 ID
     * @return 任务 DO，不存在返回 null
     */
    public FlowRunTaskDO getById(String taskId) {
        // P2-20: 任务详情查询，委托 BaseMapper 自带 selectById
        if (taskId == null) {
            return null;
        }
        return taskMapper.selectById(taskId);
    }

    // ============================== 列表查询 ==============================

    /**
     * 查实例的当前 PENDING 任务
     */
    public List<FlowRunTaskDO> listPendingByInstance(String instanceId) {
        return taskMapper.selectPendingByInstance(instanceId);
    }

    /**
     * 查用户的待办
     */
    public List<FlowRunTaskDO> listTodoByAssignee(String assigneeId, String tenantId) {
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        return taskMapper.selectTodoByAssignee(assigneeId, tid);
    }

    /**
     * 查用户的待办（多维度匹配：直接分配 + ROLE/DEPT 展开 + pmis_flow_user 关联）
     *
     * @param userId    用户 ID
     * @param roleCodes 用户拥有的角色编码（可空）
     * @param deptIds   用户所属部门 ID（字符串形式，可空）
     * @param tenantId  租户 ID（可空，默认 "1"）
     */
    public List<FlowRunTaskDO> listTodoByUser(String userId, List<String> roleCodes,
                                            List<String> deptIds, String tenantId) {
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        Set<FlowRunTaskDO> result = new LinkedHashSet<>();
        // 1. 直接分配给该用户的任务
        result.addAll(taskMapper.selectTodoByAssignee(String.valueOf(userId), tid));
        // 2. 通过 pmis_flow_user 关联的任务
        List<Long> taskIds = userMapper.selectTaskIdsByUser(String.valueOf(userId), tid);
        if (taskIds != null && !taskIds.isEmpty()) {
            for (Long tid2 : taskIds) {
                FlowRunTaskDO t = taskMapper.selectById(tid2);
                if (t != null && !FlowTaskStatus
                        .valueOf(t.getTaskStatus()).isFinished()) {
                    result.add(t);
                }
            }
        }
        // 3. ROLE/DEPT 匹配
        if (roleCodes != null) {
            for (String rc : roleCodes) {
                result.addAll(taskMapper.selectTodoByAssignee(rc, tid));
            }
        }
        if (deptIds != null) {
            for (String did : deptIds) {
                result.addAll(taskMapper.selectTodoByAssignee(did, tid));
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 查用户的已办
     */
    public List<FlowRunTaskDO> listDoneByAssignee(String assigneeId, String tenantId) {
        // P0-3: 改查历史表
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.selectDoneByAssignee(assigneeId, tid);
        List<FlowRunTaskDO> result = new ArrayList<>();
        for (FlowHisTaskDO his : hisTasks) {
            result.add(hisToTask(his));
        }
        return result;
    }

    // ============================== 分页查询 ==============================

    /**
     * P2-17: 查用户的待办（真分页：SQL LIMIT/OFFSET）
     */
    public PageResponse<FlowRunTaskDO> listTodoByAssigneePage(String assigneeId, String tenantId,
                                                          int page, int size) {
        // P2-17: 真分页（SQL LIMIT/OFFSET）
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        int offset = (safePage - 1) * safeSize;
        List<FlowRunTaskDO> list = taskMapper.selectTodoByAssigneePage(assigneeId, tid, offset, safeSize);
        long total = taskMapper.countTodoByAssignee(assigneeId, tid);
        return (PageResponse) PageResponse.success(total, (long) safePage, (long) safeSize, list);
    }

    /**
     * P2-17: 查用户的已办（真分页：SQL LIMIT/OFFSET）
     */
    public PageResponse<FlowRunTaskDO> listDoneByAssigneePage(String assigneeId, String tenantId,
                                                          int page, int size) {
        // P2-17: 真分页（SQL LIMIT/OFFSET） — 走历史表
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        int offset = (safePage - 1) * safeSize;
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.selectDoneByAssigneePage(assigneeId, tid, offset, safeSize);
        List<FlowRunTaskDO> list = new ArrayList<>();
        for (FlowHisTaskDO his : hisTasks) {
            list.add(hisToTask(his));
        }
        long total = hisTaskMapper.countDoneByAssignee(assigneeId, tid);
        return (PageResponse) PageResponse.success(total, (long) safePage, (long) safeSize, list);
    }

    /**
     * P2-33: 已办多维筛选分页查询（真分页：SQL LIMIT/OFFSET）
     */
    public PageResponse<FlowRunTaskDO> listDoneByAssigneePageMulti(String assigneeId, String businessType,
                                                               String flowCode, LocalDateTime startTime,
                                                               LocalDateTime endTime, String tenantId,
                                                               int page, int size) {
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        int offset = (safePage - 1) * safeSize;
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.selectDonePage(assigneeId, businessType,
                flowCode, startTime, endTime, tid, offset, safeSize);
        List<FlowRunTaskDO> list = new ArrayList<>();
        for (FlowHisTaskDO his : hisTasks) {
            list.add(hisToTask(his));
        }
        long total = hisTaskMapper.countDone(assigneeId, businessType, flowCode,
                startTime, endTime, tid);
        return (PageResponse) PageResponse.success(total, (long) safePage, (long) safeSize, list);
    }

    // ============================== 统计查询 ==============================

    /**
     * P2-31: 按节点统计平均耗时（GROUP BY node_code, node_name）
     */
    public List<Map<String, Object>> nodeDurationStats(String flowCode, String tenantId) {
        return hisTaskMapper.nodeDurationStats(flowCode, tenantId);
    }

    /**
     * P2-32: 查询超期任务（dueAt < now 且状态为 PENDING/CLAIMED）
     */
    public List<FlowRunTaskDO> listOverdue(String assigneeId, String tenantId) {
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        return taskMapper.selectOverdue(assigneeId, tid);
    }

    /**
     * P2-32: 统计超期任务数量
     */
    public long countOverdue(String assigneeId, String tenantId) {
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        return taskMapper.countOverdue(assigneeId, tid);
    }

    /**
     * P2-4: 统计待办任务总数（PENDING + CLAIMED）
     */
    public long countPending(String tenantId) {
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        LambdaQueryWrapper<FlowRunTaskDO> wrapper =
                new LambdaQueryWrapper<>();
        wrapper.eq(FlowRunTaskDO::getTenantId, tid)
                .in(FlowRunTaskDO::getTaskStatus, "PENDING", "CLAIMED");
        return taskMapper.selectCount(wrapper);
    }

    // ============================== 视图转换 ==============================

    /**
     * 转视图
     */
    public FlowInstanceViewDTO.FlowTaskViewDTO toView(FlowRunTaskDO task) {
        if (task == null) {
            return null;
        }
        return FlowInstanceViewDTO.FlowTaskViewDTO.builder()
                .id(task.getId())
                .nodeCode(task.getNodeCode())
                .nodeName(task.getNodeName())
                .nodeType(task.getNodeType())
                .assigneeType(task.getAssigneeType())
                .assigneeId(task.getAssigneeId())
                .assigneeName(task.getAssigneeName())
                .performType(task.getPerformType())
                .taskStatus(task.getTaskStatus())
                .comment(task.getComment())
                .createAt(task.getCreatedAt())
                .claimAt(task.getClaimAt())
                .finishAt(task.getFinishAt())
                .durationMs(task.getDurationMs())
                .dueAt(task.getDueAt())
                .priority(task.getPriority())
                .build();
    }

    // ============================== 私有辅助 ==============================

    /** 将历史任务 DO 转换为待办任务 DO（用于已办查询结果统一） */
    private FlowRunTaskDO hisToTask(FlowHisTaskDO his) {
        FlowRunTaskDO t = new FlowRunTaskDO();
        t.setId(his.getTaskId());
        t.setInstanceId(his.getInstanceId());
        t.setFlowCode(his.getFlowCode());
        t.setDefinitionId(his.getDefinitionId());
        t.setNodeCode(his.getNodeCode());
        t.setNodeName(his.getNodeName());
        t.setNodeType(his.getNodeType());
        t.setBusinessType(his.getBusinessType());
        t.setBusinessId(his.getBusinessId());
        t.setBusinessNo(his.getBusinessNo());
        t.setFlowName(his.getFlowName());
        t.setTitle(his.getTitle());
        t.setAssigneeType(his.getAssigneeType());
        t.setAssigneeId(his.getAssigneeId());
        t.setAssigneeName(his.getAssigneeName());
        t.setPerformType(his.getPerformType());
        t.setTaskStatus(his.getTaskStatus());
        t.setComment(his.getComment());
        t.setCreatedAt(his.getCreatedAt());
        t.setClaimAt(his.getClaimAt());
        t.setFinishAt(his.getFinishAt());
        t.setDurationMs(his.getDurationMs());
        return t;
    }
}
