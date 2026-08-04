package com.remisoft.workflow.server.service.impl.instance;

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
import com.remisoft.common.auth.context.AuthContext;
import com.remisoft.common.core.response.PageResponse;
import com.remisoft.common.jdbc.constant.DataSourceConstants;
import com.remisoft.workflow.domain.dto.FlowInstanceViewDTO;
import com.remisoft.workflow.domain.entity.FlowHisTask;
import com.remisoft.workflow.domain.entity.FlowRunTask;
import com.remisoft.workflow.domain.enums.FlowTaskStatus;
import com.remisoft.workflow.infra.mapper.FlowHisTaskMapper;
import com.remisoft.workflow.infra.mapper.FlowRunTaskMapper;
import com.remisoft.workflow.infra.mapper.FlowUserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.remisoft.common.auth.annotation.DataScope;

/**
 * 待办任务 — 查询类 Service 实现
 *
 * <p>从原 {@code FlowTaskServiceImpl} 单体（1847 行）按职责拆分的<b>只读查询子服务</b>。
 * 通过 {@code @DS(DataSourceConstants.SLAVE)} 强制走从库（只读副本），减轻主库压力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>任务详情</b>：{@link #getById} — 单任务查询（主键索引）</li>
 *   <li><b>待办列表</b>：{@code listTodoByAssignee} / {@code listTodoByAssigneePage} / {@code listTodoByUser} —
 *       分别覆盖「我的待办」「真分页」「多维匹配（直接分配 + ROLE/DEPT 展开）」</li>
 *   <li><b>已办列表</b>：{@code listDoneByAssignee} / {@code listDoneByAssigneePage} /
 *       {@code listDoneByAssigneePageMulti} — 真分页 + 多维筛选</li>
 *   <li><b>实例待办</b>：{@code listPendingByInstance} — 推进器内部使用</li>
 *   <li><b>超期统计</b>：{@code listOverdue} / {@code countOverdue} — P2-32 SLA 监控</li>
 *   <li><b>耗时统计</b>：{@code nodeDurationStats} — P2-31 节点级效率分析</li>
 *   <li><b>视图转换</b>：{@link #toView} — 实体转 VO</li>
 * </ul>
 *
 * <p><b>事务边界：</b>类级别 {@code @Transactional(readOnly = true)}，所有方法走只读事务，
 * 配合 {@code @DS(SLAVE)} 实现读写分离。
 *
 * <p><b>性能优化：</b>
 * <ul>
 *   <li>「我的待办」走 {@code remi_flow_run_task} 复合索引 {@code idx_assignee}</li>
 *   <li>「已办分页」走 {@code idx_assignee_completed_at} 复合索引，避免大 OFFSET</li>
 *   <li>{@code listTodoByUser} 走 {@code remi_flow_user} 关联表，避免 IN 子查询超过 PG 1000 上限</li>
 *   <li>从库路由由 {@code DynamicDataSource} 切面自动完成，调用方无感知</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see FlowTaskServiceImpl 任务服务门面
 * @see FlowRunTask 运行时任务实体
 * @see FlowHisTask 历史任务实体
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
    /** listTodoByUser 需通过 remi_flow_user 关联查询任务 */
    private final FlowUserMapper userMapper;

    // ============================== 详情查询 ==============================

    /**
     * P2-20: 按 ID 查任务（任务详情查询）
     *
     * @param taskId 任务 ID
     * @return 任务 DO，不存在返回 null
     */
    public FlowRunTask getById(String taskId) {
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
    public List<FlowRunTask> listPendingByInstance(String instanceId) {
        return taskMapper.selectPendingByInstance(instanceId);
    }

    /**
     * 查用户的待办
     */
    @DataScope(deptColumn = "dept_id", userColumn = "assignee_id")
    public List<FlowRunTask> listTodoByAssignee(String assigneeId, String tenantId) {
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        return taskMapper.selectTodoByAssignee(assigneeId, tid);
    }

    /**
     * 查用户的待办（多维度匹配：直接分配 + ROLE/DEPT 展开 + remi_flow_user 关联）
     *
     * @param userId    用户 ID
     * @param roleCodes 用户拥有的角色编码（可空）
     * @param deptIds   用户所属部门 ID（字符串形式，可空）
     * @param tenantId  租户 ID（可空，默认 "1"）
     */
    @DataScope(deptColumn = "dept_id", userColumn = "assignee_id")
    public List<FlowRunTask> listTodoByUser(String userId, List<String> roleCodes,
                                            List<String> deptIds, String tenantId) {
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        Set<FlowRunTask> result = new LinkedHashSet<>();
        // 1. 直接分配给该用户的任务
        result.addAll(taskMapper.selectTodoByAssignee(String.valueOf(userId), tid));
        // 2. 通过 remi_flow_user 关联的任务
        List<Long> taskIds = userMapper.selectTaskIdsByUser(String.valueOf(userId), tid);
        if (taskIds != null && !taskIds.isEmpty()) {
            for (Long tid2 : taskIds) {
                FlowRunTask t = taskMapper.selectById(tid2);
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
    @DataScope(deptColumn = "dept_id", userColumn = "assignee_id")
    public List<FlowRunTask> listDoneByAssignee(String assigneeId, String tenantId) {
        // P0-3: 改查历史表
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        List<FlowHisTask> hisTasks = hisTaskMapper.selectDoneByAssignee(assigneeId, tid);
        List<FlowRunTask> result = new ArrayList<>();
        for (FlowHisTask his : hisTasks) {
            result.add(hisToTask(his));
        }
        return result;
    }

    // ============================== 分页查询 ==============================

    /**
     * P2-17: 查用户的待办（真分页：SQL LIMIT/OFFSET）
     */
    public PageResponse<FlowRunTask> listTodoByAssigneePage(String assigneeId, String tenantId,
                                                          int page, int size) {
        // P2-17: 真分页（SQL LIMIT/OFFSET）
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        int offset = (safePage - 1) * safeSize;
        List<FlowRunTask> list = taskMapper.selectTodoByAssigneePage(assigneeId, tid, offset, safeSize);
        long total = taskMapper.countTodoByAssignee(assigneeId, tid);
        return (PageResponse) PageResponse.success(total, (long) safePage, (long) safeSize, list);
    }

    /**
     * P2-17: 查用户的已办（真分页：SQL LIMIT/OFFSET）
     */
    public PageResponse<FlowRunTask> listDoneByAssigneePage(String assigneeId, String tenantId,
                                                          int page, int size) {
        // P2-17: 真分页（SQL LIMIT/OFFSET） — 走历史表
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        int offset = (safePage - 1) * safeSize;
        List<FlowHisTask> hisTasks = hisTaskMapper.selectDoneByAssigneePage(assigneeId, tid, offset, safeSize);
        List<FlowRunTask> list = new ArrayList<>();
        for (FlowHisTask his : hisTasks) {
            list.add(hisToTask(his));
        }
        long total = hisTaskMapper.countDoneByAssignee(assigneeId, tid);
        return (PageResponse) PageResponse.success(total, (long) safePage, (long) safeSize, list);
    }

    /**
     * P2-33: 已办多维筛选分页查询（真分页：SQL LIMIT/OFFSET）
     */
    public PageResponse<FlowRunTask> listDoneByAssigneePageMulti(String assigneeId, String businessType,
                                                               String flowCode, LocalDateTime startTime,
                                                               LocalDateTime endTime, String tenantId,
                                                               int page, int size) {
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        int offset = (safePage - 1) * safeSize;
        List<FlowHisTask> hisTasks = hisTaskMapper.selectDonePage(assigneeId, businessType,
                flowCode, startTime, endTime, tid, offset, safeSize);
        List<FlowRunTask> list = new ArrayList<>();
        for (FlowHisTask his : hisTasks) {
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
    public List<FlowRunTask> listOverdue(String assigneeId, String tenantId) {
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
        LambdaQueryWrapper<FlowRunTask> wrapper =
                new LambdaQueryWrapper<>();
        wrapper.eq(FlowRunTask::getTenantId, tid)
                .in(FlowRunTask::getTaskStatus, "PENDING", "CLAIMED");
        return taskMapper.selectCount(wrapper);
    }

    // ============================== 视图转换 ==============================

    /**
     * 转视图
     */
    public FlowInstanceViewDTO.FlowTaskViewDTO toView(FlowRunTask task) {
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
    private FlowRunTask hisToTask(FlowHisTask his) {
        FlowRunTask t = new FlowRunTask();
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
