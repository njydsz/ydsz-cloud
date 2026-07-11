package com.njydsz.pmis.agent.server.service.impl.agent;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.agent.server.engine.version.AgentVersionManager;
import com.njydsz.pmis.agent.domain.entity.agent.AgentVersionDO;
import com.njydsz.pmis.agent.infra.mapper.agent.AgentVersionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Agent 版本管理 DB 持久化服务（P0-4 落地）。
 *
 * <p>在 {@link AgentVersionManager} 内存版本管理的基础上，增加 DB 持久化能力：
 * <ul>
 *   <li>版本注册时同步写入 DB</li>
 *   <li>版本发布/回滚时同步更新 DB 状态</li>
 *   <li>查询时优先从 DB 加载，DB 不可用时降级为内存</li>
 *   <li>应用重启后从 DB 恢复版本状态</li>
 * </ul>
 *
 * <p>使用 {@link ObjectProvider} 注入 Mapper，在无 DB 环境（如单元测试）时
 * 自动降级为纯内存模式。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P0-4)
 */
@Slf4j
@Service
public class AgentVersionServiceImpl {

    /** Agent 版本管理器（内存态版本注册/发布/回滚） */
    private final AgentVersionManager versionManager;
    /** Agent 版本 Mapper 提供者（延迟注入，无 DB 环境降级为纯内存模式） */
    private final ObjectProvider<AgentVersionMapper> mapperProvider;

    /** 内存缓存：agentType → 是否已从 DB 加载 */
    private final Set<String> loadedAgentTypes = Collections.synchronizedSet(new HashSet<>());

    /**
     * 构造函数。
     *
     * @param versionManager Agent 版本管理器
     * @param mapperProvider Agent 版本 Mapper 提供者（延迟注入）
     */
    public AgentVersionServiceImpl(AgentVersionManager versionManager,
                                    ObjectProvider<AgentVersionMapper> mapperProvider) {
        this.versionManager = versionManager;
        this.mapperProvider = mapperProvider;
    }

    /**
     * 注册新版本（同步持久化到 DB）。
     *
     * @param agentType Agent 类型
     * @param config    Agent 配置
     * @param description 版本描述
     * @return 版本号
     */
    public String registerVersion(String agentType, Map<String, Object> config, String description) {
        // 1. 内存注册
        String versionId = versionManager.registerVersion(agentType, config);

        // 2. DB 持久化
        AgentVersionMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                AgentVersionDO DO = new AgentVersionDO();
                DO.setAgentType(agentType);
                DO.setVersionId(versionId);
                DO.setStatus(AgentVersionManager.VersionStatus.DRAFT.name());
                DO.setConfigJson(config != null ? JSON.toJSONString(config) : "{}");
                DO.setDescription(description);
                DO.setIsActive(0);
                mapper.insert(DO);
                log.info("[VersionService] DB 持久化版本: agentType={}, version={}", agentType, versionId);
            } catch (Exception e) {
                log.warn("[VersionService] DB 持久化失败，内存版本仍有效: agentType={}, version={}, err={}",
                        agentType, versionId, e.getMessage());
            }
        }

        return versionId;
    }

    /**
     * 发布版本（同步更新 DB 状态）。
     *
     * @param agentType Agent 类型
     * @param versionId 版本号
     * @return true 表示发布成功
     */
    public boolean publish(String agentType, String versionId) {
        // 1. 内存发布
        boolean success = versionManager.publish(agentType, versionId);
        if (!success) {
            return false;
        }

        // 2. DB 更新
        AgentVersionMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                // 将该 agentType 下所有版本设为非活跃
                mapper.deactivateAll(agentType);

                // 将目标版本设为活跃+已发布
                AgentVersionDO DO = mapper.selectByAgentTypeAndVersion(agentType, versionId);
                if (DO != null) {
                    DO.setStatus(AgentVersionManager.VersionStatus.PUBLISHED.name());
                    DO.setIsActive(1);
                    DO.setPublishedAt(LocalDateTime.now());
                    mapper.updateById(DO);
                }

                // 将之前的活跃版本归档
                List<AgentVersionDO> allVersions = mapper.selectByAgentType(agentType);
                for (AgentVersionDO v : allVersions) {
                    if (!versionId.equals(v.getVersionId())
                            && AgentVersionManager.VersionStatus.PUBLISHED.name().equals(v.getStatus())) {
                        v.setStatus(AgentVersionManager.VersionStatus.ARCHIVED.name());
                        v.setIsActive(0);
                        mapper.updateById(v);
                    }
                }

                log.info("[VersionService] DB 发布版本: agentType={}, version={}", agentType, versionId);
            } catch (Exception e) {
                log.warn("[VersionService] DB 更新发布状态失败: agentType={}, version={}, err={}",
                        agentType, versionId, e.getMessage());
            }
        }

        return true;
    }

    /**
     * 回滚到历史版本（同步更新 DB 状态）。
     *
     * @param agentType Agent 类型
     * @param versionId 目标版本号
     * @return true 表示回滚成功
     */
    public boolean rollback(String agentType, String versionId) {
        // 1. 内存回滚
        boolean success = versionManager.rollback(agentType, versionId);
        if (!success) {
            return false;
        }

        // 2. DB 更新
        AgentVersionMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                // 将该 agentType 下所有版本设为非活跃
                mapper.deactivateAll(agentType);

                // 将目标版本设为活跃+已发布
                AgentVersionDO target = mapper.selectByAgentTypeAndVersion(agentType, versionId);
                if (target != null) {
                    target.setStatus(AgentVersionManager.VersionStatus.PUBLISHED.name());
                    target.setIsActive(1);
                    mapper.updateById(target);
                }

                // 归档当前活跃版本
                List<AgentVersionDO> allVersions = mapper.selectByAgentType(agentType);
                for (AgentVersionDO v : allVersions) {
                    if (!versionId.equals(v.getVersionId())
                            && AgentVersionManager.VersionStatus.PUBLISHED.name().equals(v.getStatus())) {
                        v.setStatus(AgentVersionManager.VersionStatus.ARCHIVED.name());
                        v.setIsActive(0);
                        mapper.updateById(v);
                    }
                }

                log.info("[VersionService] DB 回滚版本: agentType={}, version={}", agentType, versionId);
            } catch (Exception e) {
                log.warn("[VersionService] DB 更新回滚状态失败: agentType={}, version={}, err={}",
                        agentType, versionId, e.getMessage());
            }
        }

        return true;
    }

    /**
     * 获取当前活跃版本（优先从 DB 加载）。
     *
     * @param agentType Agent 类型
     * @return 活跃版本；不存在返回 null
     */
    public AgentVersionManager.AgentVersion getActiveVersion(String agentType) {
        // 确保已从 DB 加载
        ensureLoadedFromDb(agentType);

        return versionManager.getActiveVersion(agentType);
    }

    /**
     * 获取所有版本列表（优先从 DB 加载）。
     *
     * @param agentType Agent 类型
     * @return 版本列表
     */
    public List<AgentVersionManager.AgentVersion> listVersions(String agentType) {
        // 确保已从 DB 加载
        ensureLoadedFromDb(agentType);

        return versionManager.listVersions(agentType);
    }

    /**
     * 对比两个版本的配置差异。
     *
     * @param agentType  Agent 类型
     * @param versionId1 版本号 1
     * @param versionId2 版本号 2
     * @return 差异列表
     */
    public Map<String, Object[]> diff(String agentType, String versionId1, String versionId2) {
        ensureLoadedFromDb(agentType);
        return versionManager.diff(agentType, versionId1, versionId2);
    }

    // ==================== 内部方法 ====================

    /**
     * 确保指定 agentType 的版本数据已从 DB 加载到内存。
     *
     * <p>首次访问时从 DB 加载，后续直接使用内存缓存。
     * DB 异常时降级为空列表（不影响后续内存操作）。
     *
     * @param agentType Agent 类型
     */
    private void ensureLoadedFromDb(String agentType) {
        if (loadedAgentTypes.contains(agentType)) {
            return;
        }

        AgentVersionMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            loadedAgentTypes.add(agentType);
            return;
        }

        try {
            List<AgentVersionDO> dbVersions = mapper.selectByAgentType(agentType);
            if (dbVersions == null || dbVersions.isEmpty()) {
                loadedAgentTypes.add(agentType);
                return;
            }

            // 将 DB 版本同步到内存
            for (AgentVersionDO dbVer : dbVersions) {
                AgentVersionManager.AgentVersion existing = findInMemory(agentType, dbVer.getVersionId());
                if (existing == null) {
                    // 内存中不存在，从 DB 恢复
                    @SuppressWarnings("unchecked")
                    Map<String, Object> config = dbVer.getConfigJson() != null
                            ? JSON.parseObject(dbVer.getConfigJson(), Map.class)
                            : new LinkedHashMap<>();
                    String versionId = versionManager.registerVersion(agentType, config);
                    // 注意：DB 中的 versionId 可能与内存生成的不一致
                    // 这里仅恢复配置，不修改 versionId
                    if (dbVer.getIsActive() != null && dbVer.getIsActive() == 1) {
                        versionManager.publish(agentType, versionId);
                    }
                }
            }

            loadedAgentTypes.add(agentType);
            log.info("[VersionService] 从 DB 恢复版本: agentType={}, count={}",
                    agentType, dbVersions.size());
        } catch (Exception e) {
            log.warn("[VersionService] 从 DB 加载版本失败，降级为内存: agentType={}, err={}",
                    agentType, e.getMessage());
            loadedAgentTypes.add(agentType);
        }
    }

    /**
     * 在内存中查找指定版本。
     */
    private AgentVersionManager.AgentVersion findInMemory(String agentType, String versionId) {
        List<AgentVersionManager.AgentVersion> versions = versionManager.listVersions(agentType);
        return versions.stream()
                .filter(v -> versionId.equals(v.getVersionId()))
                .findFirst()
                .orElse(null);
    }
}
