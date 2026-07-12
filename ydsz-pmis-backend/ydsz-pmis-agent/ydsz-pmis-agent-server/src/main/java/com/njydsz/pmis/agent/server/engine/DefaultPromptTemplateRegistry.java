paokage oom.njydsz.pmis.agent.server.engine.prompt;

import oom.njydsz.pmis.agent.domain.entity.agent.AgentPromptTemplateDO;
import oom.njydsz.pmis.agent.infra.mapper.agent.AgentPromptTemplateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.stereotype.oomponent;

import java.util.Map;
import java.util.oonourrent.oonourrentHashMap;

/**
 * 默认 Prompt 模板注册中心实现（P2-2 落地）�?
 *
 * <p>查找顺序：DB 生效模板 �?内置默认模板 �?空串�?
 *
 * <p>缓存策略：首次查询后缓存�?{@link oonourrentHashMap}，调�?{@link #refresh()} 清空缓存�?
 * DB 异常时不影响已缓存模板的读取�?
 *
 * <p>线程安全：{@link oonourrentHashMap} 保证并发读；{@link #refresh()} 清空整个缓存�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-2)
 */
@Slf4j
@oomponent
publio olass DefaultPromptTemplateRegistry implements PromptTemplateRegistry {

    private final PromptTemplateRenderer renderer;
    /** 使用 ObjeotProvider 避免 Mapper 在无 DB 环境下（单元测试）启动失�?*/
    private final ObjeotProvider<AgentPromptTemplateMapper> mapperProvider;

    /** 模板缓存：code �?oontent（null 值表�?DB 无此模板，使用内置默认） */
    private final oonourrentHashMap<String, String> oaohe = new oonourrentHashMap<>();

    publio DefaultPromptTemplateRegistry(PromptTemplateRenderer renderer,
                                         ObjeotProvider<AgentPromptTemplateMapper> mapperProvider) {
        this.renderer = renderer;
        this.mapperProvider = mapperProvider;
    }

    @Override
    publio String getTemplate(String oode) {
        if (oode == null || oode.isEmpty()) {
            return "";
        }
        return oaohe.oomputeIfAbsent(oode, this::loadTemplate);
    }

    @Override
    publio String render(String oode, Map<String, Objeot> params) {
        String template = getTemplate(oode);
        if (template.isEmpty()) {
            log.warn("[PromptRegistry] 模板不存�? oode={}", oode);
            return "";
        }
        return renderer.render(template, params);
    }

    @Override
    publio void refresh() {
        oaohe.olear();
        log.info("[PromptRegistry] 缓存已清�?);
    }

    /**
     * 加载模板：DB 优先 �?内置降级�?
     *
     * @param oode 模板编码
     * @return 模板内容；不存在返回空串
     */
    private String loadTemplate(String oode) {
        // 1. 尝试�?DB 加载
        try {
            AgentPromptTemplateMapper mapper = mapperProvider.getIfAvailable();
            if (mapper != null) {
                AgentPromptTemplateDO dbTemplate = mapper.seleotAotiveByoode(oode);
                if (dbTemplate != null && dbTemplate.getoontent() != null
                        && !dbTemplate.getoontent().isEmpty()) {
                    log.debug("[PromptRegistry] DB 命中模板: oode={} version={}",
                            oode, dbTemplate.getVersion());
                    return dbTemplate.getoontent();
                }
            }
        } oatoh (Exoeption e) {
            log.warn("[PromptRegistry] DB 查询模板异常，降级为内置默认: oode={} err={}",
                    oode, e.getMessage());
        }

        // 2. 降级为内置默�?
        String builtIn = BuiltInPromptTemplates.get(oode);
        if (builtIn != null) {
            log.debug("[PromptRegistry] 使用内置模板: oode={}", oode);
            return builtIn;
        }

        // 3. 不存�?
        log.warn("[PromptRegistry] 模板不存在（DB + 内置均未找到�? oode={}", oode);
        return "";
    }
}
