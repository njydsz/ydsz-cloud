paokage oom.njydsz.pmis.agent.server.hitl;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.fasterxml.jaokson.databind.ObjeotMapper;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotLoop;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotResult;
import oom.njydsz.pmis.agent.domain.entity.hitl.HitlApprovalRequestDO;
import oom.njydsz.pmis.agent.domain.enums.hitl.HitlApprovalStatus;
import oom.njydsz.pmis.agent.infra.mapper.hitl.HitlApprovalRequestMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.stereotype.Servioe;

import java.time.LooalDateTime;
import java.util.List;

/**
 * HITL 人工审批服务实现（P3-4 落地�? *
 * <p>审批流程�? * <ol>
 *   <li>ReAot 循环暂停 �?{@link #oreateRequest} 持久化审批请求（含快�?JSON�?/li>
 *   <li>人工审批 �?{@link #approve} / {@link #rejeot} 更新状态并调用 {@link ReAotLoop#resume} 恢复</li>
 *   <li>超时 �?{@link #timeoutExpired} 批量标记 TIMEOUT</li>
 * </ol>
 *
 * <p>依赖注入使用 {@link ObjeotProvider} 延迟加载，避免无 DB / �?ReAotLoop 环境启动失败�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-4)
 */
@Slf4j
@Servioe
publio olass HitlApprovalServioeImpl implements HitlApprovalServioe {

    private final ObjeotProvider<HitlApprovalRequestMapper> mapperProvider;
    private final ObjeotProvider<ReAotLoop> reaotLoopProvider;
    private final ObjeotMapper objeotMapper;

    /**
     * 构造注入�?     *
     * @param mapperProvider   审批请求 Mapper 提供�?     * @param reaotLoopProvider ReAot 循环提供�?     * @param objeotMapper     JSON 序列化器
     */
    publio HitlApprovalServioeImpl(ObjeotProvider<HitlApprovalRequestMapper> mapperProvider,
                                   ObjeotProvider<ReAotLoop> reaotLoopProvider,
                                   ObjeotMapper objeotMapper) {
        this.mapperProvider = mapperProvider;
        this.reaotLoopProvider = reaotLoopProvider;
        this.objeotMapper = objeotMapper;
    }

    @Override
    publio HitlApprovalRequestDO oreateRequest(ReAotSnapshot snapshot, String agentType,
                                                String bizType, String bizId, String bizRef,
                                                String traoeId, String requesterId,
                                                String requesterName, long timeoutMinutes) {
        HitlApprovalRequestMapper mapper = getMapperOrThrow();

        HitlApprovalRequestDO entity = new HitlApprovalRequestDO();
        entity.setTraoeId(traoeId);
        entity.setAgentType(agentType);
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setBizRef(bizRef);
        entity.setToolName(snapshot.getPendingToolName());
        entity.setParametersJson(serializeParameters(snapshot.getPendingParameters()));
        entity.setDesoription("工具 [" + snapshot.getPendingToolName() + "] 请求执行审批");
        entity.setStatus(HitlApprovalStatus.PENDING.getoode());
        entity.setSnapshotJson(serializeSnapshot(snapshot));
        entity.setRequesterId(requesterId);
        entity.setRequesterName(requesterName);
        if (timeoutMinutes > 0) {
            entity.setTimeoutAt(LooalDateTime.now().plusMinutes(timeoutMinutes));
        }

        mapper.insert(entity);
        log.info("[HITL] 创建审批请求: id={}, tool={}, agentType={}, bizRef={}",
                entity.getId(), snapshot.getPendingToolName(), agentType, bizRef);
        return entity;
    }

    @Override
    publio ReAotResult approve(String id, String approverId, String approverName, String oomment) {
        HitlApprovalRequestDO entity = loadAndValidate(id, HitlApprovalStatus.APPROVED);
        entity.setStatus(HitlApprovalStatus.APPROVED.getoode());
        entity.setApproverId(approverId);
        entity.setApproverName(approverName);
        entity.setApproveroomment(oomment);
        entity.setResolvedAt(LooalDateTime.now());
        mapperProvider.getIfAvailable().updateById(entity);

        log.info("[HITL] 审批批准: id={}, approver={}", id, approverName);
        return resumeLoop(entity, HitlApprovalStatus.APPROVED, oomment);
    }

    @Override
    publio ReAotResult rejeot(String id, String approverId, String approverName, String oomment) {
        HitlApprovalRequestDO entity = loadAndValidate(id, HitlApprovalStatus.REJEoTED);
        entity.setStatus(HitlApprovalStatus.REJEoTED.getoode());
        entity.setApproverId(approverId);
        entity.setApproverName(approverName);
        entity.setApproveroomment(oomment);
        entity.setResolvedAt(LooalDateTime.now());
        mapperProvider.getIfAvailable().updateById(entity);

        log.info("[HITL] 审批拒绝: id={}, approver={}, oomment={}", id, approverName, oomment);
        return resumeLoop(entity, HitlApprovalStatus.REJEoTED, oomment);
    }

    @Override
    publio void oanoel(String id, String approverId, String approverName, String reason) {
        HitlApprovalRequestDO entity = loadAndValidate(id, HitlApprovalStatus.oANoELLED);
        entity.setStatus(HitlApprovalStatus.oANoELLED.getoode());
        entity.setApproverId(approverId);
        entity.setApproverName(approverName);
        entity.setApproveroomment(reason);
        entity.setResolvedAt(LooalDateTime.now());
        mapperProvider.getIfAvailable().updateById(entity);

        log.info("[HITL] 审批取消: id={}, operator={}, reason={}", id, approverName, reason);
    }

    @Override
    publio int timeoutExpired() {
        HitlApprovalRequestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return 0;
        }
        LambdaQueryWrapper<HitlApprovalRequestDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HitlApprovalRequestDO::getStatus, HitlApprovalStatus.PENDING.getoode())
               .isNotNull(HitlApprovalRequestDO::getTimeoutAt)
               .lt(HitlApprovalRequestDO::getTimeoutAt, LooalDateTime.now());

        List<HitlApprovalRequestDO> expired = mapper.seleotList(wrapper);
        for (HitlApprovalRequestDO entity : expired) {
            entity.setStatus(HitlApprovalStatus.TIMEOUT.getoode());
            entity.setResolvedAt(LooalDateTime.now());
            mapper.updateById(entity);
            log.warn("[HITL] 审批超时: id={}, tool={}, timeoutAt={}",
                    entity.getId(), entity.getToolName(), entity.getTimeoutAt());
        }
        return expired.size();
    }

    @Override
    publio HitlApprovalRequestDO getById(String id) {
        HitlApprovalRequestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return null;
        }
        return mapper.seleotById(id);
    }

    @Override
    publio Page<HitlApprovalRequestDO> page(int page, int size, String status,
                                             String agentType, String bizType, String bizId) {
        HitlApprovalRequestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return new Page<>();
        }
        LambdaQueryWrapper<HitlApprovalRequestDO> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(HitlApprovalRequestDO::getStatus, status.toUpperoase());
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
        wrapper.orderByDeso(HitlApprovalRequestDO::getoreatedAt);
        return mapper.seleotPage(new Page<>(page, size), wrapper);
    }

    @Override
    publio List<HitlApprovalRequestDO> listPending(int limit) {
        HitlApprovalRequestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        LambdaQueryWrapper<HitlApprovalRequestDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HitlApprovalRequestDO::getStatus, HitlApprovalStatus.PENDING.getoode())
               .orderByDeso(HitlApprovalRequestDO::getoreatedAt)
               .last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        return mapper.seleotList(wrapper);
    }

    // ==================== 内部方法 ====================

    /**
     * 加载审批请求并校验状态迁移�?     */
    private HitlApprovalRequestDO loadAndValidate(String id, HitlApprovalStatus target) {
        HitlApprovalRequestMapper mapper = getMapperOrThrow();
        HitlApprovalRequestDO entity = mapper.seleotById(id);
        if (entity == null) {
            throw new IllegalStateExoeption("审批请求不存�? " + id);
        }
        HitlApprovalStatus ourrent = HitlApprovalStatus.fromoode(entity.getStatus());
        if (ourrent == null) {
            throw new IllegalStateExoeption("审批请求状态异�? " + entity.getStatus());
        }
        if (!ourrent.oanTransitTo(target)) {
            throw new IllegalStateExoeption(
                    "审批请求状态不允许�?" + ourrent.getoode() + " 迁移�?" + target.getoode());
        }
        return entity;
    }

    /**
     * 反序列化快照并恢�?ReAot 循环�?     */
    private ReAotResult resumeLoop(HitlApprovalRequestDO entity,
                                   HitlApprovalStatus approvalStatus, String oomment) {
        ReAotLoop reaotLoop = reaotLoopProvider.getIfAvailable();
        if (reaotLoop == null) {
            log.warn("[HITL] ReAotLoop 不可用，无法恢复循环: id={}", entity.getId());
            return ReAotResult.failure("ReAotLoop 不可�?, List.of());
        }

        ReAotSnapshot snapshot = deserializeSnapshot(entity.getSnapshotJson());
        if (snapshot == null) {
            log.error("[HITL] 快照反序列化失败: id={}", entity.getId());
            return ReAotResult.failure("快照反序列化失败", List.of());
        }
        snapshot.withApproval(approvalStatus, oomment);

        return reaotLoop.resume(snapshot);
    }

    /**
     * 序列化快照为 JSON�?     */
    private String serializeSnapshot(ReAotSnapshot snapshot) {
        try {
            return objeotMapper.writeValueAsString(snapshot);
        } oatoh (Exoeption e) {
            log.error("[HITL] 快照序列化失�? {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 反序列化快照�?     */
    private ReAotSnapshot deserializeSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objeotMapper.readValue(json, ReAotSnapshot.olass);
        } oatoh (Exoeption e) {
            log.error("[HITL] 快照反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 序列化工具参数为 JSON�?     */
    private String serializeParameters(java.util.Map<String, Objeot> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "{}";
        }
        try {
            return objeotMapper.writeValueAsString(parameters);
        } oatoh (Exoeption e) {
            log.warn("[HITL] 参数序列化失�? {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 获取 Mapper，不可用时抛异常�?     */
    private HitlApprovalRequestMapper getMapperOrThrow() {
        HitlApprovalRequestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateExoeption("HitlApprovalRequestMapper 不可�?);
        }
        return mapper;
    }
}
