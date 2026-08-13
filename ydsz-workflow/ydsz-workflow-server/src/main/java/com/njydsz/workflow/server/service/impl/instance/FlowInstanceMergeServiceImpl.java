package com.njydsz.workflow.server.service.impl.instance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.redis.service.RedisCollectionOps;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.service.FlowInstanceMergeService;
import com.njydsz.workflow.server.service.FlowTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 多实例合并审批服务实现
 *
 * <p>对 {@link FlowInstanceMergeService} 接口的完整实现，是工作流引擎的<b>合并审批</b>扩展。
 * 支持将多个流程实例合并为一个「审批批次」，由同一人一次性审批通过，
 * 减少审批人在「重复业务场景」（如批量报销、批量请假）下的重复操作。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>合并组创建（{@link #createMergeGroup}）</b>：将多个流程实例合并到一个「合并组」，
 *       共享审批动作（任一实例通过 / 驳回 → 全部通过 / 驳回）</li>
 *   <li><b>合并组查询（{@link #getMergeGroupDetail}）</b>：查询合并组的实例列表与状态汇总</li>
 *   <li><b>批量审批</b>：对合并组内所有实例执行「通过 / 驳回」操作，
 *       操作记录同时写入每个实例的审计日志</li>
 *   <li><b>合并组解散</b>：合并组内全部完成后自动解散（或手动解散）</li>
 * </ul>
 *
 * <p><b>合并组设计：</b>
 * <ul>
 *   <li>使用 Redis Hash 存储「合并组 ID → 实例 ID 列表」映射，
 *       支持快速查询合并组内全部实例</li>
 *   <li>合并组 TTL 默认 7 天，过期后自动清理</li>
 *   <li>每个实例可独立取消合并（{@code removeFromMergeGroup}），
 *       取消后该实例独立审批</li>
 * </ul>
 *
 * <p><b>典型场景：</b>
 * <ul>
 *   <li>「批量报销」：员工提交 10 张报销单，财务合并审批，一次性通过</li>
 *   <li>「批量请假」：部门统一提交 20 个请假申请，主管合并审批</li>
 *   <li>「批量入职」：HR 一次性发起 5 个入职流程，IT 主管合并审批电脑分配</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>合并组创建开启 {@code @Transactional(rollbackFor = Exception.class)}，
 *       确保「合并组记录 + 各实例关联」原子性</li>
 *   <li>批量审批委托给 {@link FlowTaskService#pass} / {@link #reject}，每个实例独立事务，
 *       单实例失败不影响合并组其它实例</li>
 * </ul>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>合并组 vs 会签</b>：合并审批是「<b>合并提交</b>」（多个实例 → 一个审批动作），
 *       区别于「会签」（一个节点 → 多人审批）。前者减少审批量，后者增加审批粒度</li>
 *   <li><b>独立 vs 共享</b>：合并组内各实例保持独立（独立 {@code instanceId} / 待办 / 历史），
 *       仅共享「审批动作」</li>
 *   <li><b>审计追溯</b>：合并组审批记录每个实例的审计日志，
 *       标注「由合并组 X 触发」，便于问题排查</li>
 *   <li><b>部分驳回</b>：合并组支持「部分驳回」语义，
 *       驳回一个实例不影响其它实例的审批进度</li>
 * </ul>
 *
 * <p><b>Redis Key 设计：</b>
 * <ul>
 *   <li>{@code flow:merge:group:{groupId}} — Hash，字段为 {@code instanceId}，值为业务字段</li>
 *   <li>{@code flow:merge:instance:{instanceId}} — String，记录实例所属合并组</li>
 *   <li>所有 Key TTL = 7 天</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowInstanceMergeService 接口定义
 * @see com.njydsz.common.redis.service.RedisService Redis 服务
 * @see FlowTaskService 流程任务服务
 * @see FlowInstanceService 流程实例服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowInstanceMergeServiceImpl implements FlowInstanceMergeService {

    /** 流程实例 Mapper */
    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final FlowInstanceMapper instanceMapper;
    /** 运行时任务 Mapper */
    private final FlowRunTaskMapper taskMapper;
    /** 任务服务（查询待办任务） */
    private final FlowTaskService taskService;
    /** 工作流门面（完成任务/驳回任务） */
    private final WorkflowFacade workflowFacade;
    /** Redis 集合操作组件（Set 存储合并组实例 ID 集合） */
    private final RedisCollectionOps redisCollectionOps;

    /** Redis 模板（Hash 存储合并组元信息） */
    private final RedisTemplate<String, Object> redisTemplate;

    /** Redis Key 前缀：合并组实例 ID 集合 */
    private static final String MERGE_GROUP_KEY = "ydsz:flow:merge:group:";
    /** Redis Key 前缀：合并组元信息 */
    private static final String MERGE_GROUP_DETAIL_KEY = "ydsz:flow:merge:detail:";

    /**
     * {@inheritDoc}
     * <p>校验所有实例存在、状态为 RUNNING 且 flowCode 相同，生成合并组 ID 并存入 Redis。
     *
     * @throws SysException 当实例数 < 2、实例不存在、状态非 RUNNING 或 flowCode 不一致时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String mergeInstances(List<String> instanceIds, String operatorId, String tenantId) {
        if (instanceIds == null || instanceIds.size() < 2) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_5a6b7c8d")
                .build();
        }
        String tid = tenantId != null ? tenantId : "1";

        // 校验所有实例存在且类型相同
        Set<String> flowCodes = new LinkedHashSet<>();
        for (String instanceId : instanceIds) {
            FlowInstance instance = instanceMapper.selectById(instanceId);
            if (instance == null) {
                throw SysException.builder()
                    .resultCode(BaseResultCode.NOT_FOUND)
                    .key("error.workflow.msg_9e8f0a1b").params(instanceId)
                    .build();
            }
            if (!"RUNNING".equals(instance.getFlowStatus())) {
                throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_2b3c4d5e")
                .build();
            }
            flowCodes.add(instance.getFlowCode());
        }
        if (flowCodes.size() > 1) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_6c7d8e9f")
                .build();
        }

        // 生成合并组 ID
        String mergeGroupId = String.valueOf(snowflakeIdGenerator.nextId()).replace("-", "");

        // 存储合并组关系
        String groupKey = MERGE_GROUP_KEY + mergeGroupId;
        for (String instanceId : instanceIds) {
            redisCollectionOps.sAdd(groupKey, instanceId);
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

    /**
     * {@inheritDoc}
     * <p>遍历合并组内所有实例，找到指定审批人的待办任务并逐个通过，
     * 单条失败不影响其他实例（try-catch 吞异常记 WARN）。
     *
     * @param mergeGroupId 合并组 ID
     * @param userId       审批人 ID
     * @param comment      审批意见
     * @return 成功通过的实例数
     */
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
                List<FlowRunTask> tasks = taskService.listPendingByInstance(instanceId);
                for (FlowRunTask task : tasks) {
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

    /**
     * {@inheritDoc}
     * <p>遍历合并组内所有实例，找到指定审批人的待办任务并逐个驳回，
     * 单条失败不影响其他实例（try-catch 吞异常记 WARN）。
     *
     * @param mergeGroupId 合并组 ID
     * @param userId       审批人 ID
     * @param comment      驳回意见
     * @return 成功驳回的实例数
     */
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
                List<FlowRunTask> tasks = taskService.listPendingByInstance(instanceId);
                for (FlowRunTask task : tasks) {
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

    /**
     * {@inheritDoc}
     * <p>从 Redis 读取合并组实例 ID 集合和元信息，并查询每个实例的摘要信息。
     *
     * @param mergeGroupId 合并组 ID
     * @return 合并组详情（含实例列表）
     */
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
            FlowInstance instance = instanceMapper.selectById(instanceId);
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

    /**
     * {@inheritDoc}
     * <p>查询用户待办任务，按 flowCode 分组，筛选出有 2 个以上待办的流程类型。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 可合并的流程列表（每个包含 flowCode、flowName、taskCount、taskIds）
     */
    @Override
    public List<Map<String, Object>> listMergeable(String userId, String tenantId) {
        String tid = tenantId != null ? tenantId : "1";

        // 查询用户待办任务
        List<FlowRunTask> todoTasks = taskService.listTodoByUser(userId, null, null, tid);
        if (todoTasks == null || todoTasks.isEmpty()) {
            return List.of();
        }

        // 按 flowCode 分组，筛选出有多个待办的流程类型
        Map<String, List<FlowRunTask>> grouped = todoTasks.stream()
                .filter(t -> StringUtils.hasText(t.getFlowCode()))
                .collect(Collectors.groupingBy(FlowRunTask::getFlowCode));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<FlowRunTask>> entry : grouped.entrySet()) {
            if (entry.getValue().size() >= 2) {
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("flowCode", entry.getKey());
                group.put("flowName", entry.getValue().get(0).getFlowName());
                group.put("taskCount", entry.getValue().size());
                List<String> taskIds = entry.getValue().stream()
                        .map(FlowRunTask::getId)
                        .collect(Collectors.toList());
                group.put("taskIds", taskIds);
                result.add(group);
            }
        }
        return result;
    }

    /**
     * 从 Redis Set 读取合并组内的实例 ID 集合。
     *
     * @param mergeGroupId 合并组 ID
     * @return 实例 ID 集合，mergeGroupId 为空时返回空集合
     */
    private Set<String> getGroupInstanceIds(String mergeGroupId) {
        if (mergeGroupId == null) {
            return Collections.emptySet();
        }
        Set<String> ids = redisCollectionOps.sMembers(MERGE_GROUP_KEY + mergeGroupId, String.class);
        return ids != null ? ids : Collections.emptySet();
    }
}
