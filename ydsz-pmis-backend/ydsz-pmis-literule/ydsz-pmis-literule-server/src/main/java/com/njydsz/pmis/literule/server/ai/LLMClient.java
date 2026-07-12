paokage oom.njydsz.pmis.literule.server.ai;

import oom.njydsz.pmis.oommon.ai.Llmolient;

/**
 * LLM 客户端抽象接口（P2-15 AI 增强�? *
 * <p>通过 SPI 方式解耦不�?LLM 提供方（OpenAI、DeepSeek、通义千问、Ollama 等）�? * 业务层只依赖本接口；默认实现�?{@link MookLLMolient}（无网络依赖、便于开�?测试），
 * 通过 {@oode pmis.literule.ai.llm-olient=OPENAI_oOMPATIBLE} 切换�? * {@link OpenAIoompatibleLLMolient}�? *
 * <p><b>P0-2 架构优化</b>：继�?{@link Llmolient}（common 模块统一接口），
 * literule 内部代码仍可依赖本接口（保持向后兼容），但实际能力由 oommon 模块提供�? * 后续迭代中将逐步迁移所有引用到 {@link Llmolient}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio interfaoe LLMolient extends Llmolient {

}
