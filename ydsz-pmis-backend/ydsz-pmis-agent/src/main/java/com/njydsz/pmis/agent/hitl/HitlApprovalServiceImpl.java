package com.njydsz.pmis.agent.hitl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.agent.engine.react.ReActLoop;
import com.njydsz.pmis.agent.engine.react.ReActResult;
import com.njydsz.pmis.agent.entity.hitl.HitlApprovalRequestDO;
import com.njydsz.pmis.agent.enums.hitl.HitlApprovalStatus;
import com.njydsz.pmis.agent.mapper.hitl.HitlApprovalRequestMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HITL 人工审批服务实现（P3-4 落地）
 *
 * <p>审批流程：
 * <ol>
 *   <li>ReAct 循环暂停 → {@link #createRequest} 持久化审批请求（含快照 JSON）</li>
 *   <li>人工审批 → {@link #approve} / {@link #reject} 更新状态并调用 {@link ReActLoop#resume} 恢复</li>
 *   <li>超时 → {@link #timeoutExpired} 批量标记 TIMEOUT</li>
 * </ol>
 *
 * <p>依赖注入使用 {@link ObjectProvider} 延迟加载，避免无 DB / 无 ReActLoop 环境启动失败。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
@Slf4j
@Service
public class HitlApprovalServiceImpl implements HitlApprovalService {

    private final ObjectProvider<HitlApprovalRequestMapper> mapperProvider;
    private final ObjectProvider<ReActLoop> reactLoopProvider;
    private final ObjectMapper objectMapper;

    /**
     * 构造注入。
     *
     * @param mapperProvider   审批请求 Mapper 提供者
     * @param reactLoopProvider ReAct 循环提供者
     * @param objectMapper     JSON 序列化器
     */
    public HitlApprovalServiceImpl(ObjectProvider<HitlApprovalRequestMapper> mapperProvider,
                                   ObjectProvider<ReActLoop> reactLoopProvider,
                                   ObjectMapper objectMapper) {
        this.mapperProvider = mapperProvider;
        this.reactLoopProvider = reactLoopProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public HitlApprovalRequestDO createRequest(ReActSnapshot snapshot, String agentType,
                                                String bizType, String bizId, String bizRef,
                                                String traceId, String requesterId,
                                                String requesterName, long timeoutMinutes) {
        HitlApprovalRequestMapper mapper = getMapperOrThrow();

        HitlApprovalRequestDO entity = new HitlApprovalRequestDO();
        entity.setTraceId(traceId);
        entity.setAgentType(agentType);
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setBizRef(bizRef);
        entity.setToolName(snapshot.getPendingToolName());
        entity.setParametersJson(serializeParameters(snapshot.getPendingParameters()));
        entity.setDescription("工具 [" + snapshot.getPendingToolName() + "] 请求执行审批");
        entity.setStatus(HitlApprovalStatus.PENDING.getCode());
        entity.setSnapshotJson(serializeSnapshot(snapshot));
        entity.setRequesterId(requesterId);
        entity.setRequesterName(requesterName);
        if (timeoutMinutes > 0) {
            entity.setTimeoutAt(LocalDateTime.now().plusMinutes(timeoutMinutes));
        }

        mapper.insert(entity);
        log.info("[HITL] 创建审批请求: id={}, tool={}, agentType={}, bizRef={}",
                entity.getId(), snapshot.getPendingToolName(), agentType, bizRef);
        return entity;
    }

    @Override
    public ReActResult approve(String id, String approverId, String approverName, String comment) {
        HitlApprovalRequestDO entity = loadAndValidate(id, HitlApprovalStatus.APPROVED);
        entity.setStatus(HitlApprovalStatus.APPROVED.getCode());
        entity.setApproverId(approverId);
        entity.setApproverName(approverName);
        entity.setApproverComment(comment);
        entity.setResolvedAt(LocalDateTime.now());
        mapperProvider.getIfAvailable().updateById(entity);

        log.info("[HITL] 审批批准: id={}, approver={}", id, approverName);
        return resumeLoop(entity, HitlApprovalStatus.APPROVED, comment);
    }

    @Override
    public ReActResult reject(String id, String approverId, String approverName, String comment) {
        HitlApprovalRequestDO entity = loadAndValidate(id, HitlApprovalStatus.REJECTED);
        entity.setStatus(HitlApprovalStatus.REJECTED.getCode());
        entity.setApproverId(approverId);
        entity.setApproverName(approverName);
        entity.setApproverComment(comment);
        entity.setResolvedAt(LocalDateTime.now());
        mapperProvider.getIfAvailable().updateById(entity);

        log.info("[HITL] 审批拒绝: id={}, approver={}, comment={}", id, approverName, comment);
        return resumeLoop(entity, HitlApprovalStatus.REJECTED, comment);
    }

    @Override
    public void cancel(String id, String approverId, String approverName, String reason) {
        HitlApprovalRequestDO entity = loadAndValidate(id, HitlApprovalStatus.CANCELLED);
        entity.setStatus(HitlApprovalStatus.CANCELLED.getCode());
        entity.setApproverId(approverId);
        entity.setApproverName(approverName);
        entity.setApproverComment(reason);
        entity.setResolvedAt(LocalDateTime.now());
        mapperProvider.getIfAvailable().updateById(entity);

        log.info("[HITL] 审批取消: id={}, operator={}, reason={}", id, approverName, reason);
    }

    @Override
    public int timeoutExpired() {
        HitlApprovalRequestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return 0;
        }
        LambdaQueryWrapper<HitlApprovalRequestDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HitlApprovalRequestDO::getStatus, HitlApprovalStatus.PENDING.getCode())
               .isNotNull(HitlApprovalRequestDO::getTimeoutAt)
               .lt(HitlApprovalRequestDO::getTimeoutAt, LocalDateTime.now());

        List<HitlApprovalRequestDO> expired = mapper.selectList(wrapper);
        for (HitlApprovalRequestDO entity : expired) {
            entity.setStatus(HitlApprovalStatus.TIMEOUT.getCode());
            entity.setResolvedAt(LocalDateTime.now());
            mapper.updateById(entity);
            log.warn("[HITL] 审批超时: id={}, tool={}, timeoutAt={}",
                    entity.getId(), entity.getToolName(), entity.getTimeoutAt());
        }
        return expired.size();
    }

    @Override
    public HitlApprovalRequestDO getById(String id) {
        HitlApprovalRequestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return null;
        }
        return mapper.selectById(id);
    }

    @Override
    public Page<HitlApprovalRequestDO> page(int page, int size, String status,
                                             String agentType, String bizType, String bizId) {
        HitlApprovalRequestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return new Page<>();
        }
        LambdaQueryWrapper<HitlApprovalRequestDO> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(HitlApprovalRequestDO::getStatus, status.toUpperCase());
        }
        if (agentType != null && !agentType.isBlank()) {
            wrapper.eq(HitlApprovalRequestDO::getAgentType, agentType);
        }
        if (bizType != null && !bizType.isBlank()) {
            wrapper.eq(HitlApprovalRequestDO::getBizType, bizType);
        }
        if (bizId != null && !bizId.isBlank()) {
            wrapper.eq(HitlApprovalRequestDO::getBizId, bizId);
        }
        wrapper.orderByDesc(HitlApprovalRequestDO::getCreatedAt);
        return mapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<HitlApprovalRequestDO> listPending(int limit) {
        HitlApprovalRequestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        LambdaQueryWrapper<HitlApprovalRequestDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HitlApprovalRequestDO::getStatus, HitlApprovalStatus.PENDING.getCode())
               .orderByDesc(HitlApprovalRequestDO::getCreatedAt)
               .last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        return mapper.selectList(wrapper);
    }

    // ==================== 内部方法 ====================

    /**
     * 加载审批请求并校验状态迁移。
     */
    private HitlApprovalRequestDO loadAndValidate(String id, HitlApprovalStatus target) {
        HitlApprovalRequestMapper mapper = getMapperOrThrow();
        HitlApprovalRequestDO entity = mapper.selectById(id);
        if (entity == null) {
            throw new IllegalStateException("审批请求不存在: " + id);
        }
        HitlApprovalStatus current = HitlApprovalStatus.fromCode(entity.getStatus());
        if (current == null) {
            throw new IllegalStateException("审批请求状态异常: " + entity.getStatus());
        }
        if (!current.canTransitTo(target)) {
            throw new IllegalStateException(
                    "审批请求状态不允许从 " + current.getCode() + " 迁移到 " + target.getCode());
        }
        return entity;
    }

    /**
     * 反序列化快照并恢复 ReAct 循环。
     */
    private ReActResult resumeLoop(HitlApprovalRequestDO entity,
                                   HitlApprovalStatus approvalStatus, String comment) {
        ReActLoop reactLoop = reactLoopProvider.getIfAvailable();
        if (reactLoop == null) {
            log.warn("[HITL] ReActLoop 不可用，无法恢复循环: id={}", entity.getId());
            return ReActResult.failure("ReActLoop 不可用", List.of());
        }

        ReActSnapshot snapshot = deserializeSnapshot(entity.getSnapshotJson());
        if (snapshot == null) {
            log.error("[HITL] 快照反序列化失败: id={}", entity.getId());
            return ReActResult.failure("快照反序列化失败", List.of());
        }
        snapshot.withApproval(approvalStatus, comment);

        return reactLoop.resume(snapshot);
    }

    /**
     * 序列化快照为 JSON。
     */
    private String serializeSnapshot(ReActSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.error("[HITL] 快照序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 反序列化快照。
     */
    private ReActSnapshot deserializeSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ReActSnapshot.class);
        } catch (Exception e) {
            log.error("[HITL] 快照反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 序列化工具参数为 JSON。
     */
    private String serializeParameters(java.util.Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (Exception e) {
            log.warn("[HITL] 参数序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 获取 Mapper，不可用时抛异常。
     */
    private HitlApprovalRequestMapper getMapperOrThrow() {
        HitlApprovalRequestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException("HitlApprovalRequestMapper 不可用");
        }
        return mapper;
    }
}
