package com.remisoft.agent.server.prompt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Prompt 管理服务
 *
 * <p>提供 Prompt 模板的 CRUD、版本管理和简单 A/B 测试能力。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>Prompt 模板 CRUD</li>
 *   <li>版本管理（每次更新创建新版本，可回滚）</li>
 *   <li>A/B 测试（按比例分配不同版本）</li>
 *   <li>变量替换（#{var} 占位符）</li>
 * </ul>
 *
 * <p>当前使用内存存储，生产环境可替换为数据库实现。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Service
public class PromptManagementService {

    private static final Logger log = LoggerFactory.getLogger(PromptManagementService.class);
    private final Map<String, PromptTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, List<PromptVersion>> versions = new ConcurrentHashMap<>();

    /**
     * 创建 Prompt 模板
     */
    public PromptTemplate create(String code, String name, String content,
                                  String description, String category) {
        if (templates.containsKey(code)) {
            throw new IllegalArgumentException("Prompt 模板已存在: " + code);
        }
        PromptTemplate template = new PromptTemplate(code, name, content, description,
                category, 1, LocalDateTime.now(), LocalDateTime.now());
        templates.put(code, template);
        versions.computeIfAbsent(code, k -> new CopyOnWriteArrayList<>())
                .add(new PromptVersion(code, 1, content, LocalDateTime.now()));
        log.info("[Prompt] 创建模板: code={}, name={}", code, name);
        return template;
    }

    /**
     * 更新 Prompt 模板（创建新版本）
     */
    public PromptTemplate update(String code, String content) {
        PromptTemplate existing = templates.get(code);
        if (existing == null) {
            throw new IllegalArgumentException("Prompt 模板不存在: " + code);
        }
        int newVersion = existing.version() + 1;
        PromptTemplate updated = new PromptTemplate(
                existing.code(), existing.name(), content,
                existing.description(), existing.category(),
                newVersion, existing.createdAt(), LocalDateTime.now());
        templates.put(code, updated);
        versions.computeIfAbsent(code, k -> new CopyOnWriteArrayList<>())
                .add(new PromptVersion(code, newVersion, content, LocalDateTime.now()));
        log.info("[Prompt] 更新模板: code={}, version={}", code, newVersion);
        return updated;
    }

    /**
     * 获取 Prompt 模板
     */
    public PromptTemplate get(String code) {
        return templates.get(code);
    }

    /**
     * 获取指定版本
     */
    public PromptVersion getVersion(String code, int version) {
        return versions.getOrDefault(code, List.of()).stream()
                .filter(v -> v.version() == version)
                .findFirst()
                .orElse(null);
    }

    /**
     * 列出所有模板
     */
    public List<PromptTemplate> list() {
        return List.copyOf(templates.values());
    }

    /**
     * 列出模板的所有版本
     */
    public List<PromptVersion> listVersions(String code) {
        return List.copyOf(versions.getOrDefault(code, List.of()));
    }

    /**
     * 按分类列出模板
     */
    public List<PromptTemplate> listByCategory(String category) {
        return templates.values().stream()
                .filter(t -> category.equals(t.category()))
                .collect(Collectors.toList());
    }

    /**
     * 删除模板
     */
    public void delete(String code) {
        templates.remove(code);
        versions.remove(code);
        log.info("[Prompt] 删除模板: code={}", code);
    }

    /**
     * 回滚到指定版本
     */
    public PromptTemplate rollback(String code, int targetVersion) {
        PromptVersion pv = getVersion(code, targetVersion);
        if (pv == null) {
            throw new IllegalArgumentException("版本不存在: " + targetVersion);
        }
        return update(code, pv.content());
    }

    /**
     * 渲染 Prompt（变量替换）
     */
    public String render(String code, Map<String, Object> variables) {
        PromptTemplate template = get(code);
        if (template == null) {
            throw new IllegalArgumentException("Prompt 模板不存在: " + code);
        }
        String content = template.content();
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                content = content.replace("#{" + entry.getKey() + "}",
                        String.valueOf(entry.getValue()));
            }
        }
        return content;
    }

    /** Prompt 模板（当前版本快照） */
    public record PromptTemplate(
            /** 模板唯一编码（业务标识，创建后不可变） */
            String code,
            /** 模板名称（展示用） */
            String name,
            /** 模板内容，支持 #{var} 占位符 */
            String content,
            /** 模板描述 */
            String description,
            /** 分类（用于 listByCategory 检索） */
            String category,
            /** 当前版本号，自 1 起每次 update 递增 */
            int version,
            /** 创建时间 */
            LocalDateTime createdAt,
            /** 最近更新时间 */
            LocalDateTime updatedAt) {}

    /** Prompt 模板的历史版本（每次 update 追加一条，支持 rollback） */
    public record PromptVersion(
            /** 所属模板编码 */
            String code,
            /** 版本号 */
            int version,
            /** 该版本的模板内容快照 */
            String content,
            /** 版本创建时间 */
            LocalDateTime createdAt) {}
}
