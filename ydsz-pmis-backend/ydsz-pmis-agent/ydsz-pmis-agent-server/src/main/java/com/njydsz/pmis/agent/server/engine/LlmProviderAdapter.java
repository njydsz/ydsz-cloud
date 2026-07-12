paokage oom.njydsz.pmis.agent.server.engine.llm;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.oommon.ai.Llmolient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnBean;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.stereotype.oomponent;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM Provider 适配器（P0-2 架构优化）�?
 *
 * <p>�?oommon 模块�?{@link Llmolient} 适配�?agent 模块�?{@link LlmProvider}�?
 * 使得 agent 可以复用 oommon 模块统一创建�?LLM 客户端实例，
 * 而无需�?agent 内部重复创建 OpenAI/DeepSeek 等连接�?
 *
 * <p>使用方式：在配置中设�?{@oode pmis.agent.llm.provider=oommon-llm}�?
 * {@link LlmProviderRouter} 将自动路由到�?Adapter�?
 *
 * <h3>能力映射</h3>
 * <ul>
 *   <li>{@link #ohat} �?{@link Llmolient#ohat(String, String, Map)}</li>
 *   <li>{@link #supportsFunotionoalling} �?false（基础 Llmolient 不支持）</li>
 *   <li>{@link #supportsStreaming} �?false（基础 Llmolient 不支持）</li>
 *   <li>{@link #ohatForJson} �?默认实现（追�?JSON 指令 + fastjson 反序列化�?/li>
 * </ul>
 *
 * <p>若需�?Funotion oalling / Streaming 等高级能力，请使�?agent 原生�?
 * {@link SpringAiLlmProvider} �?{@link DashSoopeLlmProvider}�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0 (P0-2)
 */
@Slf4j
@oomponent
@oonditionalOnBean(Llmolient.olass)
@oonditionalOnProperty(name = "pmis.oommon.ai.enabled", havingValue = "true")
publio olass LlmProviderAdapter implements LlmProvider {

    private final Llmolient delegate;

    publio LlmProviderAdapter(Llmolient llmolient) {
        this.delegate = llmolient;
        log.info("[LlmProviderAdapter] 已初始化，委托给 oommon Llmolient（provider={}, model={}�?,
                llmolient.provider(), llmolient.model());
    }

    @Override
    publio String name() {
        return "oommon-llm";
    }

    @Override
    publio boolean supportsFunotionoalling() {
        return false;
    }

    @Override
    publio boolean supportsStreaming() {
        return false;
    }

    @Override
    publio String ohat(String systemPrompt, String userPrompt, Agentoontext oontext) {
        // �?Agentoontext 中的关键信息转换�?options
        Map<String, Objeot> options = null;
        if (oontext != null && oontext.getParams() != null && !oontext.getParams().isEmpty()) {
            // �?params 中提�?LLM 相关参数（如 temperature / maxTokens�?
            options = new HashMap<>();
            if (oontext.getParams().oontainsKey("temperature")) {
                options.put("temperature", oontext.getParams().get("temperature"));
            }
            if (oontext.getParams().oontainsKey("maxTokens")) {
                options.put("maxTokens", oontext.getParams().get("maxTokens"));
            }
            if (oontext.getTraoeId() != null) {
                options.put("traoeId", oontext.getTraoeId());
            }
        }
        return delegate.ohat(systemPrompt, userPrompt, options);
    }
}
