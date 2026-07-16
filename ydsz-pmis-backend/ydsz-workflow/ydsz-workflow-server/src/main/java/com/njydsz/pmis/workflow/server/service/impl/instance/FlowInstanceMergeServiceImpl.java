package com.njydsz.workflow.server.service.impl.instance;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.service.FlowInstanceMergeService;
import com.njydsz.workflow.server.service.FlowTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-5: 多实例合并审批服务实现
 *
 * <p>使用 Redis 存储合并组关系，合并组内实例保持独立但共享审批操作。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowInstanceMergeServiceImpl implements FlowInstanceMergeService {

    private final FlowInstanceMapper instanceMapper;
    private final FlowRunTaskMapper taskMapper;
    private final FlowTaskService taskService;
    private final WorkflowFacade workflowFacade;
    private final StringRedisTemplate redisTemplate;

    private static final String MERGE_GROUP_KEY = "ydsz:flow:merge:group:";
    private static final String MERGE_GROUP_DETAIL_KEY = "ydsz:flow:merge:detail:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String mergeInstances(List<String> instanceIds, String operatorId, String tenantId) {
        if (instanceIds == null || instanceIds.size() < 2) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_5a6b7c8d");
        }
        String tid = tenantId != null ? tenantId : "1";

        // 校验所有实例存在且类型相同
        Set<String> flowCodes = new LinkedHashSet<>();
        for (String instanceId : instanceIds) {
            FlowInstanceDO instance = instanceMapper.selectById(instanceId);
            if (instance == null) {
                throw new SysException(BaseResultCode.NOT_FOUND, "error.workflow.msg_9e8f0a1b", instanceId);
            }
            if (!"RUNNING".equals(instance.getFlowStatus())) {
                throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_2b3c4d5e");
            }
            flowCodes.add(instance.getFlowCode());
        }
        if (flowCodes.size() > 1) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_6c7d8e9f");
        }

        // 生成合并组 ID
        String mergeGroupId = UUID.randomUUID().toString().replace("-", "");

        // 存储合并组关系
        String groupKey = MERGE_GROUP_KEY + mergeGroupId;
        for (String instanceId : instanceIds) {
            redisTemplate.opsForSet().add(groupKey, instanceId);
        }

        // 存储合并组元信息
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("operatorId", operatorId != null ? operatorId : "");
        detail.put("tenantId", tid);
        detail.put("flowCode", flowCodes.iterator().next());
        detail.put("instanceCount", String.valueOf(instanceIds.size()));
        detail.put("createdAt", String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().putAll(MERGE_GROUP_DETAIL_KEY + mergeGroupId, detail);

        log.info("[FlowMerge] 合并实例: groupId={} count={} flowCode={} operator={}",
                mergeGroupId, instanceIds.size(), flowCodes.iterator().next(), operatorId);
        return mergeGroupId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchPassMerged(String mergeGroupId, String userId, String comment) {
        Set<String> instanceIds = getGroupInstanceIds(mergeGroupId);
        if (instanceIds.isEmpty()) {
            return 0;
        }
        int successCount = 0;
        for (String instanceId : instanceIds) {
            try {
                List<FlowRunTaskDO> tasks = taskService.listPendingByInstance(instanceId);
                for (FlowRunTaskDO task : tasks) {
                    if (userId.equals(task.getAssigneeId())) {
                        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
                        dto.setTaskId(task.getId());
                        dto.setComment(comment);
                        dto.setUserId(userId);
                        workflowFacade.completeTask(dto);
                        successCount++;
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("[FlowMerge] 合并通过失败: instanceId={} err={}", instanceId, e.getMessage());
            }
        }
        log.info("[FlowMerge] 批量通过: groupId={} success={}/{}", mergeGroupId, successCount, instanceIds.size());
        return successCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchRejectMerged(String mergeGroupId, String userId, String comment) {
        Set<String> instanceIds = getGroupInstanceIds(mergeGroupId);
        if (instanceIds.isEmpty()) {
            return 0;
        }
        int successCount = 0;
        for (String instanceId : instanceIds) {
            try {
                List<FlowRunTaskDO> tasks = taskService.listPendingByInstance(instanceId);
                for (FlowRunTaskDO task : tasks) {
                    if (userId.equals(task.getAssigneeId())) {
                        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
                        dto.setTaskId(task.getId());
                        dto.setComment(comment);
                        dto.setUserId(userId);
                        workflowFacade.rejectTask(dto);
                        successCount++;
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("[FlowMerge] 合并驳回失败: instanceId={} err={}", instanceId, e.getMessage());
            }
        }
        log.info("[FlowMerge] 批量驳回: groupId={} success={}/{}", mergeGroupId, successCount, instanceIds.size());
        return successCount;
    }

    @Override
    public Map<String, Object> getMergeGroup(String mergeGroupId) {
        Set<String> instanceIds = getGroupInstanceIds(mergeGroupId);
        Map<Object, Object> detail = redisTemplate.opsForHash()
                .entries(MERGE_GROUP_DETAIL_KEY + mergeGroupId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mergeGroupId", mergeGroupId);
        result.put("instanceIds", new ArrayList<>(instanceIds));
        result.put("instanceCount", instanceIds.size());
        result.put("operatorId", detail.get("operatorId"));
        result.put("flowCode", detail.get("flowCode"));
        result.put("createdAt", detail.get("createdAt"));

        // 获取实例摘要
        List<Map<String, Object>> instanceDetails = new ArrayList<>();
        for (String instanceId : instanceIds) {
            FlowInstanceDO instance = instanceMapper.selectById(instanceId);
            if (instance != null) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("instanceId", instance.getId());
                info.put("flowName", instance.getFlowName());
                info.put("flowStatus", instance.getFlowStatus());
                info.put("businessNo", instance.getBusinessNo());
                info.put("initiatorName", instance.getInitiatorName());
                instanceDetails.add(info);
            }
        }
        result.put("instances", instanceDetails);
        return result;
    }

    @Override
    public List<Map<String, Object>> listMergeable(String userId, String tenantId) {
        String tid = tenantId != null ? tenantId : "1";

        // 查询用户待办任务
        List<FlowRunTaskDO> todoTasks = taskService.listTodoByUser(userId, null, null, tid);
        if (todoTasks == null || todoTasks.isEmpty()) {
            return List.of();
        }

        // 按 flowCode 分组，筛选出有多个待办的流程类型
        Map<String, List<FlowRunTaskDO>> grouped = todoTasks.stream()
                .filter(t -> StringUtils.hasText(t.getFlowCode()))
                .collect(Collectors.groupingBy(FlowRunTaskDO::getFlowCode));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<FlowRunTaskDO>> entry : grouped.entrySet()) {
            if (entry.getValue().size() >= 2) {
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("flowCode", entry.getKey());
                group.put("flowName", entry.getValue().get(0).getFlowName());
                group.put("taskCount", entry.getValue().size());
                List<String> taskIds = entry.getValue().stream()
                        .map(FlowRunTaskDO::getId)
                        .collect(Collectors.toList());
                group.put("taskIds", taskIds);
                result.add(group);
            }
        }
        return result;
    }

    private Set<String> getGroupInstanceIds(String mergeGroupId) {
        if (mergeGroupId == null) {
            return Collections.emptySet();
        }
        Set<String> ids = redisTemplate.opsForSet().members(MERGE_GROUP_KEY + mergeGroupId);
        return ids != null ? ids : Collections.emptySet();
    }
}
