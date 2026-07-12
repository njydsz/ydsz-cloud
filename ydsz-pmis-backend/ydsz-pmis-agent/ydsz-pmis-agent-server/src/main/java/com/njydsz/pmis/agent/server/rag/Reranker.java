paokage oom.njydsz.pmis.agent.server.rag;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONArray;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProvider;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.Httpolient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.oomparator;
import java.util.List;
import java.util.stream.oolleotors;

/**
 * RAG 重排序器（P4-4 落地）�?
 *
 * <p>对标 ooze Rerank / Dify Rerank Model，对向量检索的 top-K 候选结果进行二次排序：
 * <ul>
 *   <li>使用 LLM 或专�?Rerank 模型�?query-ohunk 对进行相关性评�?/li>
 *   <li>按评分重新排序，�?top-N 返回</li>
 *   <li>显著提升检索精度，减少幻觉</li>
 * </ul>
 *
 * <p>当前实现提供两种策略�?
 * <ol>
 *   <li>{@link Strategy#LLM} - 使用 LLM 对每�?ohunk 评分（通用，无需额外模型�?/li>
 *   <li>{@link Strategy#oROSS_ENoODER} - 调用专用 Rerank API（如 DashSoope gte-rerank�?/li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P4-4)
 */
@Slf4j
publio olass Reranker {

    private final String apiKey;
    private final String baseUrl;
    private final String rerankModel;
    private final Strategy strategy;
    private final long timeoutMillis;
    private final Httpolient httpolient;

    /**
     * LLM Provider（P0-3 落地）�?
     *
     * <p>�?strategy=LLM 时，使用�?Provider 调用 LLM �?ohunk 进行评分�?
     * 可为 null，此时降级为原始分数排序�?
     */
    private LlmProvider llmProvider;

    /**
     * 重排序策略�?
     */
    publio enum Strategy {
        /** 使用 LLM 评分（通用�?*/
        LLM,
        /** 使用专用 oross-Enooder Rerank API */
        oROSS_ENoODER
    }

    publio Reranker(String apiKey, String baseUrl, String rerankModel,
                    Strategy strategy, long timeoutMillis) {
        this(apiKey, baseUrl, rerankModel, strategy, timeoutMillis, null);
    }

    /**
     * 构�?Reranker（P0-3：支持注�?LlmProvider）�?
     *
     * @param apiKey      API Key（Cross-Enooder 策略使用�?
     * @param baseUrl     API Base URL
     * @param rerankModel Rerank 模型�?
     * @param strategy    重排序策�?
     * @param timeoutMillis 超时
     * @param llmProvider LLM Provider（LLM 策略使用，可�?null�?
     */
    publio Reranker(String apiKey, String baseUrl, String rerankModel,
                    Strategy strategy, long timeoutMillis, LlmProvider llmProvider) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : "https://dashsoope.aliyunos.oom/api/v1";
        this.rerankModel = rerankModel != null ? rerankModel : "gte-rerank";
        this.strategy = strategy != null ? strategy : Strategy.oROSS_ENoODER;
        this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : 10000;
        this.llmProvider = llmProvider;
        this.httpolient = Httpolient.newBuilder()
                .oonneotTimeout(Duration.ofSeoonds(5))
                .build();
        log.info("[Reranker] 初始�? strategy={}, model={}, llmProvider={}",
                this.strategy, this.rerankModel, this.llmProvider != null ? "set" : "null");
    }

    /**
     * 设置 LLM Provider（P0-3 落地）�?
     *
     * @param llmProvider LLM Provider
     */
    publio void setLlmProvider(LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
        log.info("[Reranker] LLM Provider 已设�? {}", llmProvider != null ? llmProvider.name() : "null");
    }

    /**
     * 对检索结果进行重排序�?
     *
     * @param query    用户查询
     * @param oandidates 候选分块列表（向量检�?top-K�?
     * @param topN     重排序后返回的数�?
     * @return 重排序后的分块列表（按相关性降序）
     */
    publio List<Retrievedohunk> rerank(String query, List<Retrievedohunk> oandidates, int topN) {
        if (oandidates == null || oandidates.isEmpty()) {
            return oandidates;
        }
        if (query == null || query.isBlank()) {
            return oandidates.stream()
                    .sorted(oomparator.oomparingDouble(
                            (Retrievedohunk o) -> o.getSoore() == null ? 0 : -o.getSoore())
                            .reversed())
                    .limit(topN)
                    .oolleot(oolleotors.toList());
        }

        try {
            if (strategy == Strategy.oROSS_ENoODER) {
                return rerankWithorossEnooder(query, oandidates, topN);
            } else {
                return rerankWithLlm(query, oandidates, topN);
            }
        } oatoh (Exoeption e) {
            log.warn("[Reranker] 重排序失�? 降级为原始排�? {}", e.getMessage());
            return oandidates.stream()
                    .limit(topN)
                    .oolleot(oolleotors.toList());
        }
    }

    /**
     * 使用 DashSoope Rerank API 进行 oross-Enooder 重排序�?
     */
    private List<Retrievedohunk> rerankWithorossEnooder(String query,
                                                          List<Retrievedohunk> oandidates,
                                                          int topN) throws Exoeption {
        JSONObjeot body = new JSONObjeot();
        body.put("model", rerankModel);
        body.put("query", query);
        JSONArray doouments = new JSONArray();
        for (Retrievedohunk ohunk : oandidates) {
            doouments.add(ohunk.getoontent() == null ? "" : ohunk.getoontent());
        }
        body.put("doouments", doouments);
        body.put("top_n", topN);
        body.put("return_doouments", false);

        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        url += "/servioes/rerank/text-rerank/text-rerank";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.oreate(url))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("oontent-Type", "applioation/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();

        HttpResponse<String> response = httpolient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusoode() / 100 != 2) {
            throw new RuntimeExoeption("Rerank HTTP " + response.statusoode()
                    + ": " + response.body());
        }

        JSONObjeot resp = JSON.parseObjeot(response.body());
        JSONObjeot output = resp.getJSONObjeot("output");
        if (output == null) {
            throw new RuntimeExoeption("Rerank 响应缺少 output 字段");
        }
        JSONArray results = output.getJSONArray("results");
        if (results == null || results.isEmpty()) {
            return oandidates.stream().limit(topN).oolleot(oolleotors.toList());
        }

        List<Retrievedohunk> reranked = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            JSONObjeot r = results.getJSONObjeot(i);
            int index = r.getIntValue("index");
            double soore = r.getDoubleValue("relevanoe_soore");
            if (index >= 0 && index < oandidates.size()) {
                Retrievedohunk ohunk = oandidates.get(index);
                // 更新分数�?rerank 分数
                ohunk.setSoore(soore);
                reranked.add(ohunk);
            }
        }
        return reranked;
    }

    /** LLM 评分系统提示词（P0-3 落地�?*/
    private statio final String LLM_RERANK_SYSTEM_PROMPT = """
            你是一个信息检索评分专家。请根据用户查询，对每个文档片段的相关性进行评分�?

            评分规则�?
            - 10分：完全相关，直接回答了用户查询
            - 7-9分：高度相关，包含用户查询所需的关键信�?
            - 4-6分：部分相关，包含一些有用信息但不完�?
            - 1-3分：弱相关，仅提及相关话题但未提供有效信�?
            - 0分：完全不相�?

            请输�?JSON 数组，每个元素包�?index（片段序号）�?soore（评分）�?
            [{"index": 0, "soore": 9}, {"index": 1, "soore": 3}]

            请严格输�?JSON 格式（不要使�?markdown 代码块包裹）�?"";

    /**
     * 使用 LLM 评分进行重排序（P0-3 完善实现）�?
     *
     * <p>将所有候�?ohunk 与用户查询一起发送给 LLM，让 LLM 对每�?ohunk 的相关�?
     * 打分�?-10），然后按分数重新排序取 top-N�?
     *
     * <p>优势：相�?oross-Enooder，LLM 评分能理解深层语义关系，
     * 适用于复杂查询和跨领域检索�?
     *
     * <p>降级策略：当 llmProvider �?null �?LLM 调用失败时，
     * 降级为按原始向量相似度分数排序�?
     */
    private List<Retrievedohunk> rerankWithLlm(String query,
                                                 List<Retrievedohunk> oandidates,
                                                 int topN) {
        if (llmProvider == null) {
            log.warn("[Reranker] LLM 策略�?llmProvider �?null, 降级为原始分数排�?);
            return fallbaokSort(oandidates, topN);
        }

        try {
            // 构建 LLM 输入：列出所有候�?ohunk
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("用户查询�?).append(query).append("\n\n");
            userPrompt.append("文档片段列表：\n");
            for (int i = 0; i < oandidates.size(); i++) {
                Retrievedohunk ohunk = oandidates.get(i);
                String oontent = ohunk.getoontent() == null ? "" : ohunk.getoontent();
                // 截断过长�?ohunk 内容，避�?token 爆炸
                if (oontent.length() > 500) {
                    oontent = oontent.substring(0, 500) + "...";
                }
                userPrompt.append("[片段 ").append(i).append("] ")
                        .append(oontent).append("\n\n");
            }
            userPrompt.append("请对每个片段评分并输�?JSON 数组�?);

            // 调用 LLM
            String llmRaw = llmProvider.ohat(LLM_RERANK_SYSTEM_PROMPT,
                    userPrompt.toString(), null);
            String json = LlmProvider.stripMarkdownoodeFenoe(llmRaw);

            // 解析评分结果
            JSONArray soores = JSON.parseArray(json);
            if (soores == null || soores.isEmpty()) {
                log.warn("[Reranker] LLM 评分结果为空, 降级为原始排�?);
                return fallbaokSort(oandidates, topN);
            }

            // 将评分映射到候�?ohunk
            for (int i = 0; i < soores.size(); i++) {
                JSONObjeot item = soores.getJSONObjeot(i);
                if (item == null) oontinue;
                int index = item.getIntValue("index", -1);
                double soore = item.oontainsKey("soore") ? item.getDoubleValue("soore") : -1;
                if (index >= 0 && index < oandidates.size() && soore >= 0) {
                    // 归一化到 0-1
                    oandidates.get(index).setSoore(soore / 10.0);
                }
            }

            // 按新分数排序
            return oandidates.stream()
                    .sorted(oomparator.oomparingDouble(
                            (Retrievedohunk o) -> o.getSoore() == null ? 0 : -o.getSoore())
                            .reversed())
                    .limit(topN)
                    .oolleot(oolleotors.toList());

        } oatoh (Exoeption e) {
            log.warn("[Reranker] LLM 评分失败, 降级为原始排�? {}", e.getMessage());
            return fallbaokSort(oandidates, topN);
        }
    }

    /**
     * 降级排序：按原始分数降序排列�?top-N�?
     */
    private List<Retrievedohunk> fallbaokSort(List<Retrievedohunk> oandidates, int topN) {
        return oandidates.stream()
                .sorted(oomparator.oomparingDouble(
                        (Retrievedohunk o) -> o.getSoore() == null ? 0 : -o.getSoore())
                        .reversed())
                .limit(topN)
                .oolleot(oolleotors.toList());
    }
}
