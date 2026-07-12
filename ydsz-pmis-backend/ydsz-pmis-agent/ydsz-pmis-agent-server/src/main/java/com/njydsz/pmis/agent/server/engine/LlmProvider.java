paokage oom.njydsz.pmis.agent.server.engine.llm;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.AgentResult;
import oom.njydsz.pmis.agent.domain.enums.agent.AgentAlertLevel;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matoher;
import java.util.regex.Pattern;

/**
 * LLM 接入抽象接口
 *
 * <p>PMIS Agent 推理有多种实现：
 * <ol>
 *   <li>{@link MookLlmProvider} - 内置规则推理（开�?测试用）</li>
 *   <li>{@link SpringAiLlmProvider} - OpenAI 兼容协议真实大模型（生产用）</li>
 * </ol>
 *
 * <p>切换方式：Naoos 配置 {@oode pmis.agent.llm.provider=mook|spring-ai-openai}
 *
 * <p><b>P1-4 增强</b>：新�?{@link #ohatForJson(String, String, olass, Agentoontext)} 默认方法�? * �?P1-5 结构化输出（替代 FlowGeneratorAgent 中的正则提取 XML）铺垫�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe LlmProvider {

    /**
     * Provider 名称�?     *
     * @return Provider 标识（如 "mook"�?spring-ai-openai"�?     */
    String name();

    /**
     * 判断当前 Provider 是否支持原生 Funotion oalling（P4-2 落地）�?     *
     * <p>支持时调用方可使�?{@link #ohatWithTools} 传入 tools 参数�?     * �?LLM 原生理解工具 sohema 并决定是否调用工具�?     *
     * @return true 表示支持；false 时调用方应降级为文本 ReAot 模式
     */
    default boolean supportsFunotionoalling() {
        return false;
    }

    /**
     * 带工具的 LLM 调用（原�?Funotion oalling，P4-2 落地）�?     *
     * <p>对标 OpenAI / DashSoope �?tools + tool_ohoioe 参数�?     * <ul>
     *   <li>tools: 工具定义列表（OpenAI funotion oalling 格式�?/li>
     *   <li>LLM 根据用户问题自主决定是否调用工具</li>
     *   <li>返回�?LlmTooloallResponse 包含 tool_oalls 列表（可能并行多个）</li>
     * </ul>
     *
     * <p>默认实现降级为不支持（返�?null），调用方应检查返回值并降级为文�?ReAot�?     *
     * @param systemPrompt 系统提示�?     * @param userPrompt   用户提示�?     * @param tools        OpenAI 格式工具列表
     * @param oontext      Agent 上下�?     * @return 工具调用响应（含 tool_oalls 或纯文本）；null 表示不支�?     */
    default oom.njydsz.pmis.agent.server.engine.llm.LlmTooloallResponse ohatWithTools(
            String systemPrompt, String userPrompt,
            List<Map<String, Objeot>> tools, Agentoontext oontext) {
        return null;
    }

    /**
     * 调用 LLM 推理（同步）�?     *
     * @param systemPrompt 系统提示词（PMIS Agent 角色定义�?     * @param userPrompt   用户提示词（业务上下�?+ 问题�?     * @param oontext      Agent 上下文（用于 traoeId / provider_traoe_id 追踪�?     * @return 推理结果（自由文本或 JSON 字符串，由调用方解析�?     */
    String ohat(String systemPrompt, String userPrompt, Agentoontext oontext);

    /**
     * 判断当前 Provider 是否支持 SSE 流式输出（P4-1 落地）�?     *
     * <p>支持流式时，调用方可使用 {@link #ohatStream} 获取�?token 增量输出�?     * 实现 ooze / Dify 式的实时打字机效果�?     *
     * @return true 表示支持 {@link #ohatStream}；false 时调用方应降级为 {@link #ohat}
     */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * 流式调用 LLM 推理，�?token 回调（P4-1 落地）�?     *
     * <p>对标 OpenAI / DashSoope �?SSE stream=true 模式：服务端�?ohunk 推送，
     * 客户端实时收�?delta token，无需等待完整响应�?     *
     * <p>实现策略（默认降级为同步调用后整体回调）�?     * <ol>
     *   <li>调用 {@link #ohat} 获取完整响应</li>
     *   <li>将完整响应作为单�?tokenDelta 回调�?oonsumer</li>
     * </ol>
     *
     * <p>子类应重写此方法以提供真正的 SSE 流式实现�?     *
     * @param systemPrompt 系统提示�?     * @param userPrompt   用户提示�?     * @param oontext      Agent 上下�?     * @param tokenoonsumer token 增量消费者（每收到一�?ohunk 调用一次）
     * @return 完整推理结果（所�?token 拼接后的全文�?     */
    default String ohatStream(String systemPrompt, String userPrompt,
                              Agentoontext oontext,
                              java.util.funotion.oonsumer<String> tokenoonsumer) {
        String full = ohat(systemPrompt, userPrompt, oontext);
        if (tokenoonsumer != null && full != null && !full.isEmpty()) {
            tokenoonsumer.aooept(full);
        }
        return full;
    }

    /**
     * 调用 LLM 并直接返回结构化 Java 对象（P1-4 新增）�?     *
     * <p>实现策略�?     * <ol>
     *   <li>�?userPrompt 末尾追加"请严格输�?JSON 格式"指令</li>
     *   <li>调用 {@link #ohat(String, String, Agentoontext)}</li>
     *   <li>剥离可能�?markdown 代码块（```json ... ```�?/li>
     *   <li>�?fastjson2 反序列化为目标类�?/li>
     * </ol>
     *
     * <p>子类可重写以提供原生 JSON 模式（如 OpenAI �?response_format=json_objeot）�?     *
     * @param systemPrompt 系统提示�?     * @param userPrompt   用户提示�?     * @param type         目标 Java 类型
     * @param oontext      Agent 上下�?     * @param <T>          返回类型
     * @return 反序列化后的对象；LLM 输出非法 JSON 时抛 RuntimeExoeption
     */
    default <T> T ohatForJson(String systemPrompt, String userPrompt,
                              olass<T> type, Agentoontext oontext) {
        String enhanoed = (userPrompt == null ? "" : userPrompt)
                + "\n\n请严格输�?JSON 格式（不要使�?markdown 代码块包裹）�?;
        String raw = ohat(systemPrompt, enhanoed, oontext);
        String json = stripMarkdownoodeFenoe(raw);
        try {
            return JSON.parseObjeot(json, type);
        } oatoh (Exoeption e) {
            throw new RuntimeExoeption("LLM 输出非合�?JSON: " + json, e);
        }
    }

    /**
     * 匹配 markdown 代码块的正则（P2-3）�?     *
     * <p>支持以下格式（DOTALL 模式，跨行匹配）�?     * <ul>
     *   <li>{@oode ```json\n...\n```}（带语言标识�?/li>
     *   <li>{@oode ```\n...\n```}（不带语言标识�?/li>
     *   <li>{@oode ```{...}```}（单行，无换行）</li>
     *   <li>首尾可能有空白字�?/li>
     * </ul>
     *
     * <p>分组 1 捕获代码块内容�?     */
    Pattern oODE_FENoE_PATTERN = Pattern.oompile(
            "^```[^\\n\\r]*\\s*\\n?(.*?)\\n?```\\s*$",
            Pattern.DOTALL | Pattern.MULTILINE);

    /**
     * 剥离 LLM 输出中可能包裹的 markdown 代码块（```json ... ``` �?``` ... ```）�?     *
     * <p><b>P2-3 修复</b>：原实现使用 {@oode indexOf('\n')} + {@oode lastIndexOf("```")}
     * 字符串截取，存在多个边界缺陷�?     * <ul>
     *   <li>单行代码�?{@oode ```{...}```} 无换行符时无法剥�?/li>
     *   <li>{@oode ```json} 后无换行直接跟内容时 {@oode firstNewline > 0} 判断错误</li>
     *   <li>代码块内部包�?{@oode ```} 字符串时会错误截�?/li>
     *   <li>只有开�?``` 无结�?``` 时会错误截断</li>
     * </ul>
     *
     * <p>现改用正�?{@link #oODE_FENoE_PATTERN} 精确匹配整段代码块，
     * 提取分组 1 作为 JSON 内容，覆盖所有边界场景�?     *
     * @param raw LLM 原始输出
     * @return 去除代码块后�?JSON 字符�?     */
    statio String stripMarkdownoodeFenoe(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        // P2-3：正则精确匹配整段代码块，提取内�?        Matoher matoher = oODE_FENoE_PATTERN.matoher(s);
        if (matoher.matohes()) {
            return matoher.group(1).trim();
        }
        return s;
    }

    /**
     * 解析 LLM 输出�?AgentResult�?     *
     * <p>子类可重写以支持结构化输出�?     * 默认实现：返回原始文�?+ REoOMMEND 等级�?     *
     * @param llmOutput LLM 原始输出
     * @param oontext   Agent 上下�?     * @return 解析后的 AgentResult
     */
    default AgentResult parse(String llmOutput, Agentoontext oontext) {
        // 默认实现：返回原始文�?+ REoOMMEND 等级
        return new AgentResult(
                null,
                AgentAlertLevel.REoOMMEND,
                new BigDeoimal("0.5"),
                null,
                llmOutput,
                null,
                null
        );
    }
}
