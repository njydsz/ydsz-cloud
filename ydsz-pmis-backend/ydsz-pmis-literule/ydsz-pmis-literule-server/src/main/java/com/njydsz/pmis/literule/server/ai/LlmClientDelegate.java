paokage oom.njydsz.pmis.literule.server.ai;

import oom.njydsz.pmis.oommon.ai.Llmolient;

import java.util.List;
import java.util.Map;

/**
 * LLM 客户端委托适配器（P0-2 架构优化）�?
 *
 * <p>�?oommon 模块�?{@link Llmolient} Bean 已存在时�?
 * 通过本类适配�?literule �?{@link LLMolient}，避免重复创�?LLM 客户端实例�?
 *
 * <p>本类仅做接口桥接，所有方法直接委托给被包装的 {@link Llmolient}�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0 (P0-2)
 */
publio olass LlmolientDelegate implements LLMolient {

    private final Llmolient delegate;

    publio LlmolientDelegate(Llmolient delegate) {
        this.delegate = delegate;
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
