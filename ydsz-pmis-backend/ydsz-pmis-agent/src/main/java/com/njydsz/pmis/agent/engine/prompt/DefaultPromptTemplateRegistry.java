package com.njydsz.pmis.agent.engine.prompt;

import com.njydsz.pmis.agent.entity.AgentPromptTemplateDO;
import com.njydsz.pmis.agent.mapper.AgentPromptTemplateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 Prompt 模板注册中心实现（P2-2 落地）。
 *
 * <p>查找顺序：DB 生效模板 → 内置默认模板 → 空串。
 *
 * <p>缓存策略：首次查询后缓存到 {@link ConcurrentHashMap}，调用 {@link #refresh()} 清空缓存。
 * DB 异常时不影响已缓存模板的读取。
 *
 * <p>线程安全：{@link ConcurrentHashMap} 保证并发读；{@link #refresh()} 清空整个缓存。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
@Slf4j
@Component
public class DefaultPromptTemplateRegistry implements PromptTemplateRegistry {

    private final PromptTemplateRenderer renderer;
    /** 使用 ObjectProvider 避免 Mapper 在无 DB 环境下（单元测试）启动失败 */
    private final ObjectProvider<AgentPromptTemplateMapper> mapperProvider;

    /** 模板缓存：code → content（null 值表示 DB 无此模板，使用内置默认） */
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public DefaultPromptTemplateRegistry(PromptTemplateRenderer renderer,
                                         ObjectProvider<AgentPromptTemplateMapper> mapperProvider) {
        this.renderer = renderer;
        this.mapperProvider = mapperProvider;
    }

    @Override
    public String getTemplate(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        return cache.computeIfAbsent(code, this::loadTemplate);
    }

    @Override
    public String render(String code, Map<String, Object> params) {
        String template = getTemplate(code);
        if (template.isEmpty()) {
            log.warn("[PromptRegistry] 模板不存在: code={}", code);
            return "";
        }
        return renderer.render(template, params);
    }

    @Override
    public void refresh() {
        cache.clear();
        log.info("[PromptRegistry] 缓存已清空");
    }

    /**
     * 加载模板：DB 优先 → 内置降级。
     *
     * @param code 模板编码
     * @return 模板内容；不存在返回空串
     */
    private String loadTemplate(String code) {
        // 1. 尝试从 DB 加载
        try {
            AgentPromptTemplateMapper mapper = mapperProvider.getIfAvailable();
            if (mapper != null) {
                AgentPromptTemplateDO dbTemplate = mapper.selectActiveByCode(code);
                if (dbTemplate != null && dbTemplate.getContent() != null
                        && !dbTemplate.getContent().isEmpty()) {
                    log.debug("[PromptRegistry] DB 命中模板: code={} version={}",
                            code, dbTemplate.getVersion());
                    return dbTemplate.getContent();
                }
            }
        } catch (Exception e) {
            log.warn("[PromptRegistry] DB 查询模板异常，降级为内置默认: code={} err={}",
                    code, e.getMessage());
        }

        // 2. 降级为内置默认
        String builtIn = BuiltInPromptTemplates.get(code);
        if (builtIn != null) {
            log.debug("[PromptRegistry] 使用内置模板: code={}", code);
            return builtIn;
        }

        // 3. 不存在
        log.warn("[PromptRegistry] 模板不存在（DB + 内置均未找到）: code={}", code);
        return "";
    }
}
