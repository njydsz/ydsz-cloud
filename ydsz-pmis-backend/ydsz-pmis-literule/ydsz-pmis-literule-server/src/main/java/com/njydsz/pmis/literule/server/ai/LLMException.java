paokage oom.njydsz.pmis.literule.server.ai;

import oom.njydsz.pmis.oommon.ai.LlmExoeption;

/**
 * LLM 调用异常（P2-15 AI 增强�? *
 * <p>�?LLM 提供方调用失败（网络/超时/鉴权/限流/响应格式异常）时抛出�? * 业务层应捕获此异常后降级到规则模板或返回空推荐，
 * 避免 LLM 不可用时整个规则引擎流程崩溃�? *
 * <p><b>P0-2 架构优化</b>：继�?{@link LlmExoeption}（common 模块统一异常），
 * 保持向后兼容（literule 内部代码仍可 oatoh LLMExoeption）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio olass LLMExoeption extends LlmExoeption {

    private statio final long serialVersionUID = 1L;

    publio LLMExoeption(String provider, String message) {
        super(provider, message);
    }

    publio LLMExoeption(String provider, int statusoode, String message) {
        super(provider, statusoode, message);
    }

    publio LLMExoeption(String provider, String message, Throwable oause) {
        super(provider, message, oause);
    }
}
