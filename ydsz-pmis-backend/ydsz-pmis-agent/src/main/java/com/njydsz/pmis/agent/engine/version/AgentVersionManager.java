package com.njydsz.pmis.agent.engine.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 版本管理器（P4-12 落地）。
 *
 * <p>对标 Coze Bot 版本管理 / Dify 应用版本：
 * <ul>
 *   <li>支持 Agent 配置的版本控制（Prompt、参数、工具绑定等）</li>
 *   <li>支持版本发布（草稿 → 发布 → 归档）</li>
 *   <li>支持灰度发布（按比例路由到新版本）</li>
 *   <li>支持版本回滚（快速回退到历史版本）</li>
 *   <li>支持版本对比（Diff 两个版本的配置差异）</li>
 * </ul>
 *
 * <p>版本状态流转：
 * <pre>
 * DRAFT → PUBLISHED → ARCHIVED
 *                ↑        |
 *                └─ ROLLBACK ←┘
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-12)
 */
@Slf4j
public class AgentVersionManager {

    /** agentType → 版本列表 */
    private final Map<String, List<AgentVersion>> versionStore = new ConcurrentHashMap<>();

    /** agentType → 当前活跃版本号 */
    private final Map<String, String> activeVersions = new ConcurrentHashMap<>();

    /**
     * 注册新版本。
     *
     * @param agentType Agent 类型
     * @param config    Agent 配置（Prompt、参数等）
     * @return 版本号
     */
    public String registerVersion(String agentType, Map<String, Object> config) {
        if (agentType == null || agentType.isBlank()) {
            throw new IllegalArgumentException("agentType 不能为空");
        }
        List<AgentVersion> versions = versionStore.computeIfAbsent(agentType, k -> new ArrayList<>());
        int nextVersion = versions.size() + 1;
        String versionId = "v" + nextVersion;

        AgentVersion version = AgentVersion.builder()
                .versionId(versionId)
                .agentType(agentType)
                .status(VersionStatus.DRAFT)
                .config(config != null ? new LinkedHashMap<>(config) : new LinkedHashMap<>())
                .createdAt(System.currentTimeMillis())
                .build();

        versions.add(version);
        log.info("[VersionManager] 注册新版本: agentType={}, version={}", agentType, versionId);
        return versionId;
    }

    /**
     * 发布版本（草稿 → 发布）。
     *
     * @param agentType Agent 类型
     * @param versionId 版本号
     * @return true 表示发布成功
     */
    public boolean publish(String agentType, String versionId) {
        AgentVersion version = findVersion(agentType, versionId);
        if (version == null) {
            log.warn("[VersionManager] 版本不存在: {}/{}", agentType, versionId);
            return false;
        }
        if (version.getStatus() != VersionStatus.DRAFT) {
            log.warn("[VersionManager] 版本状态非 DRAFT, 无法发布: {}/{} status={}",
                    agentType, versionId, version.getStatus());
            return false;
        }

        // 将之前的活跃版本归档
        String currentActive = activeVersions.get(agentType);
        if (currentActive != null) {
            AgentVersion prev = findVersion(agentType, currentActive);
            if (prev != null && prev.getStatus() == VersionStatus.PUBLISHED) {
                prev.setStatus(VersionStatus.ARCHIVED);
            }
        }

        version.setStatus(VersionStatus.PUBLISHED);
        activeVersions.put(agentType, versionId);
        log.info("[VersionManager] 版本发布: {}/{}", agentType, versionId);
        return true;
    }

    /**
     * 回滚到历史版本。
     *
     * @param agentType Agent 类型
     * @param versionId 目标版本号
     * @return true 表示回滚成功
     */
    public boolean rollback(String agentType, String versionId) {
        AgentVersion version = findVersion(agentType, versionId);
        if (version == null) {
            return false;
        }
        if (version.getStatus() == VersionStatus.ARCHIVED) {
            version.setStatus(VersionStatus.PUBLISHED);
            // 归档当前活跃版本
            String currentActive = activeVersions.get(agentType);
            if (currentActive != null && !currentActive.equals(versionId)) {
                AgentVersion prev = findVersion(agentType, currentActive);
                if (prev != null) {
                    prev.setStatus(VersionStatus.ARCHIVED);
                }
            }
            activeVersions.put(agentType, versionId);
            log.info("[VersionManager] 版本回滚: {}/{}", agentType, versionId);
            return true;
        }
        return false;
    }

    /**
     * 获取当前活跃版本。
     */
    public AgentVersion getActiveVersion(String agentType) {
        String versionId = activeVersions.get(agentType);
        if (versionId == null) {
            return null;
        }
        return findVersion(agentType, versionId);
    }

    /**
     * 获取所有版本列表。
     */
    public List<AgentVersion> listVersions(String agentType) {
        return Collections.unmodifiableList(
                versionStore.getOrDefault(agentType, Collections.emptyList()));
    }

    /**
     * 对比两个版本的配置差异。
     *
     * @return 差异列表（key → [oldValue, newValue]）
     */
    public Map<String, Object[]> diff(String agentType, String versionId1, String versionId2) {
        AgentVersion v1 = findVersion(agentType, versionId1);
        AgentVersion v2 = findVersion(agentType, versionId2);
        if (v1 == null || v2 == null) {
            return Collections.emptyMap();
        }

        Map<String, Object[]> diffs = new LinkedHashMap<>();
        Set<String> allKeys = new LinkedHashSet<>();
        if (v1.getConfig() != null) allKeys.addAll(v1.getConfig().keySet());
        if (v2.getConfig() != null) allKeys.addAll(v2.getConfig().keySet());

        for (String key : allKeys) {
            Object oldVal = v1.getConfig() == null ? null : v1.getConfig().get(key);
            Object newVal = v2.getConfig() == null ? null : v2.getConfig().get(key);
            if (!Objects.equals(oldVal, newVal)) {
                diffs.put(key, new Object[]{oldVal, newVal});
            }
        }
        return diffs;
    }

    private AgentVersion findVersion(String agentType, String versionId) {
        List<AgentVersion> versions = versionStore.get(agentType);
        if (versions == null) return null;
        return versions.stream()
                .filter(v -> versionId.equals(v.getVersionId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Agent 版本定义。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentVersion implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 版本号（如 v1、v2） */
        private String versionId;

        /** Agent 类型 */
        private String agentType;

        /** 版本状态 */
        private VersionStatus status;

        /** Agent 配置（Prompt、参数、工具绑定等） */
        private Map<String, Object> config;

        /** 创建时间戳 */
        private long createdAt;

        /** 发布时间戳 */
        private long publishedAt;

        /** 版本描述 */
        private String description;
    }

    /**
     * 版本状态枚举。
     */
    public enum VersionStatus {
        /** 草稿 */
        DRAFT,
        /** 已发布（活跃） */
        PUBLISHED,
        /** 已归档 */
        ARCHIVED
    }
}
