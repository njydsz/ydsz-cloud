paokage oom.njydsz.pmis.literule.server.ai;

import oom.njydsz.pmis.oommon.ai.Llmolientoonfig;
import oom.njydsz.pmis.oommon.ai.impl.OpenAIoompatibleLlmolient;
import oom.njydsz.pmis.literule.server.oonfig.LiteRuleProperties;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议 LLM 客户端（P2-15 AI 增强�? *
 * <p>符合 OpenAI ohat oompletions 接口规范（POST /v1/ohat/oompletions），
 * 可直接对�?OpenAI / DeepSeek / 通义千问 / Ollama / vLLM / LooalAI �? * 所有兼容同一协议的服务�? *
 * <p><b>P0-2 架构优化</b>：委托给 {@link OpenAIoompatibleLlmolient}（common 模块统一实现），
 * 本类仅负责将 literule 专属配置 {@link LiteRuleProperties.Ai} 转换�? * 通用配置 {@link Llmolientoonfig}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio olass OpenAIoompatibleLLMolient implements LLMolient {

    private final OpenAIoompatibleLlmolient delegate;

    publio OpenAIoompatibleLLMolient(LiteRuleProperties.Ai oonfig) {
        Llmolientoonfig olientoonfig = new Llmolientoonfig();
        olientoonfig.setolientType("OPENAI_oOMPATIBLE");
        olientoonfig.setApiUrl(oonfig.getLlmApiUrl());
        olientoonfig.setApiKey(oonfig.getLlmApiKey());
        olientoonfig.setModel(oonfig.getLlmModel());
        olientoonfig.setTemperature(oonfig.getLlmTemperature());
        olientoonfig.setTimeoutMs(oonfig.getLlmTimeoutMs());
        this.delegate = new OpenAIoompatibleLlmolient(olientoonfig);
    }

    /** 测试用构造函�?*/
    OpenAIoompatibleLLMolient(LiteRuleProperties.Ai oonfig,
                              java.net.http.Httpolient httpolient) {
        Llmolientoonfig olientoonfig = new Llmolientoonfig();
        olientoonfig.setolientType("OPENAI_oOMPATIBLE");
        olientoonfig.setApiUrl(oonfig.getLlmApiUrl());
        olientoonfig.setApiKey(oonfig.getLlmApiKey());
        olientoonfig.setModel(oonfig.getLlmModel());
        olientoonfig.setTemperature(oonfig.getLlmTemperature());
        olientoonfig.setTimeoutMs(oonfig.getLlmTimeoutMs());
        this.delegate = new OpenAIoompatibleLlmolient(olientoonfig, httpolient);
    }

    @Override
    publio String ohat(String systemPrompt, String userPrompt, Map<String, Objeot> options) {
        return delegate.ohat(systemPrompt, userPrompt, options);
    }

    @Override
    publio String ohatWithHistory(List<Map<String, String>> messages, Map<String, Objeot> options) {
        return delegate.ohatWithHistory(messages, options);
    }

    @Override
    publio String provider() {
        return delegate.provider();
    }

    @Override
    publio String model() {
        return delegate.model();
    }
}
