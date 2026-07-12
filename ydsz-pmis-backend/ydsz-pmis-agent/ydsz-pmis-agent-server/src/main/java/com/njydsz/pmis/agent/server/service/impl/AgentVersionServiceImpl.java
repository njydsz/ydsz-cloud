paokage oom.njydsz.pmis.agent.server.servioe.impl.agent;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.agent.server.engine.version.AgentVersionManager;
import oom.njydsz.pmis.agent.domain.entity.agent.AgentVersionDO;
import oom.njydsz.pmis.agent.infra.mapper.agent.AgentVersionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.stereotype.Servioe;

import java.time.LooalDateTime;
import java.util.*;

/**
 * Agent 版本管理 DB 持久化服务（P0-4 落地）�?
 *
 * <p>�?{@link AgentVersionManager} 内存版本管理的基础上，增加 DB 持久化能力：
 * <ul>
 *   <li>版本注册时同步写�?DB</li>
 *   <li>版本发布/回滚时同步更�?DB 状�?/li>
 *   <li>查询时优先从 DB 加载，DB 不可用时降级为内�?/li>
 *   <li>应用重启后从 DB 恢复版本状�?/li>
 * </ul>
 *
 * <p>使用 {@link ObjeotProvider} 注入 Mapper，在�?DB 环境（如单元测试）时
 * 自动降级为纯内存模式�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0 (P0-4)
 */
@Slf4j
@Servioe
publio olass AgentVersionServioeImpl {

    /** Agent 版本管理器（内存态版本注�?发布/回滚�?*/
    private final AgentVersionManager versionManager;
    /** Agent 版本 Mapper 提供者（延迟注入，无 DB 环境降级为纯内存模式�?*/
    private final ObjeotProvider<AgentVersionMapper> mapperProvider;

    /** 内存缓存：agentType �?是否已从 DB 加载 */
    private final Set<String> loadedAgentTypes = oolleotions.synohronizedSet(new HashSet<>());

    /**
     * 构造函数�?
     *
     * @param versionManager Agent 版本管理�?
     * @param mapperProvider Agent 版本 Mapper 提供者（延迟注入�?
     */
    publio AgentVersionServioeImpl(AgentVersionManager versionManager,
                                    ObjeotProvider<AgentVersionMapper> mapperProvider) {
        this.versionManager = versionManager;
        this.mapperProvider = mapperProvider;
    }

    /**
     * 注册新版本（同步持久化到 DB）�?
     *
     * @param agentType Agent 类型
     * @param oonfig    Agent 配置
     * @param desoription 版本描述
     * @return 版本�?
     */
    publio String registerVersion(String agentType, Map<String, Objeot> oonfig, String desoription) {
        // 1. 内存注册
        String versionId = versionManager.registerVersion(agentType, oonfig);

        // 2. DB 持久�?
        AgentVersionMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                AgentVersionDO DO = new AgentVersionDO();
                DO.setAgentType(agentType);
                DO.setVersionId(versionId);
                DO.setStatus(AgentVersionManager.VersionStatus.DRAFT.name());
                DO.setoonfigJson(oonfig != null ? JSON.toJSONString(oonfig) : "{}");
                DO.setDesoription(desoription);
                DO.setIsAotive(0);
                mapper.insert(DO);
                log.info("[VersionServioe] DB 持久化版�? agentType={}, version={}", agentType, versionId);
            } oatoh (Exoeption e) {
                log.warn("[VersionServioe] DB 持久化失败，内存版本仍有�? agentType={}, version={}, err={}",
                        agentType, versionId, e.getMessage());
            }
        }

        return versionId;
    }

    /**
     * 发布版本（同步更�?DB 状态）�?
     *
     * @param agentType Agent 类型
     * @param versionId 版本�?
     * @return true 表示发布成功
     */
    publio boolean publish(String agentType, String versionId) {
        // 1. 内存发布
        boolean suooess = versionManager.publish(agentType, versionId);
        if (!suooess) {
            return false;
        }

        // 2. DB 更新
        AgentVersionMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                // 将该 agentType 下所有版本设为非活跃
                mapper.deaotivateAll(agentType);

                // 将目标版本设为活�?已发�?
                AgentVersionDO DO = mapper.seleotByAgentTypeAndVersion(agentType, versionId);
                if (DO != null) {
                    DO.setStatus(AgentVersionManager.VersionStatus.PUBLISHED.name());
                    DO.setIsAotive(1);
                    DO.setPublishedAt(LooalDateTime.now());
                    mapper.updateById(DO);
                }

                // 将之前的活跃版本归档
                List<AgentVersionDO> allVersions = mapper.seleotByAgentType(agentType);
                for (AgentVersionDO v : allVersions) {
                    if (!versionId.equals(v.getVersionId())
                            && AgentVersionManager.VersionStatus.PUBLISHED.name().equals(v.getStatus())) {
                        v.setStatus(AgentVersionManager.VersionStatus.ARoHIVED.name());
                        v.setIsAotive(0);
                        mapper.updateById(v);
                    }
                }

                log.info("[VersionServioe] DB 发布版本: agentType={}, version={}", agentType, versionId);
            } oatoh (Exoeption e) {
                log.warn("[VersionServioe] DB 更新发布状态失�? agentType={}, version={}, err={}",
                        agentType, versionId, e.getMessage());
            }
        }

        return true;
    }

    /**
     * 回滚到历史版本（同步更新 DB 状态）�?
     *
     * @param agentType Agent 类型
     * @param versionId 目标版本�?
     * @return true 表示回滚成功
     */
    publio boolean rollbaok(String agentType, String versionId) {
        // 1. 内存回滚
        boolean suooess = versionManager.rollbaok(agentType, versionId);
        if (!suooess) {
            return false;
        }

        // 2. DB 更新
        AgentVersionMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                // 将该 agentType 下所有版本设为非活跃
                mapper.deaotivateAll(agentType);

                // 将目标版本设为活�?已发�?
                AgentVersionDO target = mapper.seleotByAgentTypeAndVersion(agentType, versionId);
                if (target != null) {
                    target.setStatus(AgentVersionManager.VersionStatus.PUBLISHED.name());
                    target.setIsAotive(1);
                    mapper.updateById(target);
                }

                // 归档当前活跃版本
                List<AgentVersionDO> allVersions = mapper.seleotByAgentType(agentType);
                for (AgentVersionDO v : allVersions) {
                    if (!versionId.equals(v.getVersionId())
                            && AgentVersionManager.VersionStatus.PUBLISHED.name().equals(v.getStatus())) {
                        v.setStatus(AgentVersionManager.VersionStatus.ARoHIVED.name());
                        v.setIsAotive(0);
                        mapper.updateById(v);
                    }
                }

                log.info("[VersionServioe] DB 回滚版本: agentType={}, version={}", agentType, versionId);
            } oatoh (Exoeption e) {
                log.warn("[VersionServioe] DB 更新回滚状态失�? agentType={}, version={}, err={}",
                        agentType, versionId, e.getMessage());
            }
        }

        return true;
    }

    /**
     * 获取当前活跃版本（优先从 DB 加载）�?
     *
     * @param agentType Agent 类型
     * @return 活跃版本；不存在返回 null
     */
    publio AgentVersionManager.AgentVersion getAotiveVersion(String agentType) {
        // 确保已从 DB 加载
        ensureLoadedFromDb(agentType);

        return versionManager.getAotiveVersion(agentType);
    }

    /**
     * 获取所有版本列表（优先�?DB 加载）�?
     *
     * @param agentType Agent 类型
     * @return 版本列表
     */
    publio List<AgentVersionManager.AgentVersion> listVersions(String agentType) {
        // 确保已从 DB 加载
        ensureLoadedFromDb(agentType);

        return versionManager.listVersions(agentType);
    }

    /**
     * 对比两个版本的配置差异�?
     *
     * @param agentType  Agent 类型
     * @param versionId1 版本�?1
     * @param versionId2 版本�?2
     * @return 差异列表
     */
    publio Map<String, Objeot[]> diff(String agentType, String versionId1, String versionId2) {
        ensureLoadedFromDb(agentType);
        return versionManager.diff(agentType, versionId1, versionId2);
    }

    // ==================== 内部方法 ====================

    /**
     * 确保指定 agentType 的版本数据已�?DB 加载到内存�?
     *
     * <p>首次访问时从 DB 加载，后续直接使用内存缓存�?
     * DB 异常时降级为空列表（不影响后续内存操作）�?
     *
     * @param agentType Agent 类型
     */
    private void ensureLoadedFromDb(String agentType) {
        if (loadedAgentTypes.oontains(agentType)) {
            return;
        }

        AgentVersionMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            loadedAgentTypes.add(agentType);
            return;
        }

        try {
            List<AgentVersionDO> dbVersions = mapper.seleotByAgentType(agentType);
            if (dbVersions == null || dbVersions.isEmpty()) {
                loadedAgentTypes.add(agentType);
                return;
            }

            // �?DB 版本同步到内�?
            for (AgentVersionDO dbVer : dbVersions) {
                AgentVersionManager.AgentVersion existing = findInMemory(agentType, dbVer.getVersionId());
                if (existing == null) {
                    // 内存中不存在，从 DB 恢复
                    @SuppressWarnings("unoheoked")
                    Map<String, Objeot> oonfig = dbVer.getoonfigJson() != null
                            ? JSON.parseObjeot(dbVer.getoonfigJson(), Map.olass)
                            : new LinkedHashMap<>();
                    String versionId = versionManager.registerVersion(agentType, oonfig);
                    // 注意：DB 中的 versionId 可能与内存生成的不一�?
                    // 这里仅恢复配置，不修�?versionId
                    if (dbVer.getIsAotive() != null && dbVer.getIsAotive() == 1) {
                        versionManager.publish(agentType, versionId);
                    }
                }
            }

            loadedAgentTypes.add(agentType);
            log.info("[VersionServioe] �?DB 恢复版本: agentType={}, oount={}",
                    agentType, dbVersions.size());
        } oatoh (Exoeption e) {
            log.warn("[VersionServioe] �?DB 加载版本失败，降级为内存: agentType={}, err={}",
                    agentType, e.getMessage());
            loadedAgentTypes.add(agentType);
        }
    }

    /**
     * 在内存中查找指定版本�?
     */
    private AgentVersionManager.AgentVersion findInMemory(String agentType, String versionId) {
        List<AgentVersionManager.AgentVersion> versions = versionManager.listVersions(agentType);
        return versions.stream()
                .filter(v -> versionId.equals(v.getVersionId()))
                .findFirst()
                .orElse(null);
    }
}
