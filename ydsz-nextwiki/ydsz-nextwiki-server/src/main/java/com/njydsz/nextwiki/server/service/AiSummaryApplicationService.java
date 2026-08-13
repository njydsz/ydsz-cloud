package com.njydsz.nextwiki.server.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.njydsz.nextwiki.server.config.NextwikiProperties;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 文档摘要服务。
 * <p>基于 LLM 生成文件内容摘要。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Service
public class AiSummaryApplicationService {

    private final RestTemplate nextwikiRestTemplate;
    private final NextwikiProperties properties;

    public AiSummaryApplicationService(RestTemplate restTemplate, NextwikiProperties properties) {
        this.nextwikiRestTemplate = restTemplate;
        this.properties = properties;
    }

    /** 摘要最大句子数 */
    private static final int MAX_SENTENCES = 5;

    /** 关键词数量 */
    private static final int MAX_KEYWORDS = 10;

    /** 最小句子长度 */
    private static final int MIN_SENTENCE_LENGTH = 10;

    /**
     * 生成文档摘要（对外总入口）。
     * <p>优先走 LLM（需配置 {@code nextwiki.ai.llm-enabled=true} 且已配置 API 地址/Key），
     * 当 LLM 未启用、返回空或调用异常时，自动降级到本地 TextRank 算法，保证摘要能力始终可用。
     *
     * @param content 待摘要的文档纯文本；为 {@code null}/空时直接返回空串，不做任何处理
     * @return 摘要文本；输入为空时返回空串，正常情况下不会直接抛出异常
     * @throws 不会抛出受检或非受检异常（LLM 调用失败已被内部兜底捕获并降级）
     * @see #generateSummaryByLlm(String)
     * @see #generateSummaryByTextRank(String)
     * @complexity LLM 模式为网络 IO（受 {@code llmApiUrl} 响应时间影响）；
     *             TextRank 降级为 O(iter × N²)（iter=50 固定迭代，N 为句子数）
     * @note 纯计算/IO 调用，无共享可变状态、无事务边界，线程安全，可并发调用
     */
    public String generateSummary(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        if (properties.getAi().isLlmEnabled()) {
            return generateSummaryByLlm(content);
        }
        return generateSummaryByTextRank(content);
    }

    /**
     * 提取文档关键词（对外总入口）。
     * <p>与 {@link #generateSummary(String)} 同源：LLM 可用时调用大模型提取，否则降级到本地词频统计。
     *
     * @param content 文档纯文本；为 {@code null}/空时返回空列表
     * @return 关键词列表（最多 {@link #MAX_KEYWORDS} 个），输入为空时返回空列表
     * @throws 不会抛出非受检异常（LLM 失败已兜底降级）
     * @complexity LLM 模式为网络 IO；降级模式 O(N)（N 为 token 数）
     * @note 无事务边界，线程安全
     */
    public List<String> extractKeywords(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }

        if (properties.getAi().isLlmEnabled()) {
            return extractKeywordsByLlm(content);
        }
        return extractKeywordsByTextRank(content);
    }

    /**
     * 综合分析文档：聚合摘要、关键词、字数与预估阅读时长。
     * <p>基于字符数估算阅读时长（约 500 字/分钟粗略折算，下限为 1 分钟）。
     *
     * @param content 文档纯文本；为 {@code null}/空时摘要与关键词返回空、字数为 0
     * @return 文档分析结果 {@link DocumentAnalysis}，其字段含义见该内部类定义
     * @complexity 等于 {@link #generateSummary(String)} 与 {@link #extractKeywords(String)} 之和
     * @note 无副作用、无事务边界，线程安全
     */
    public DocumentAnalysis analyze(String content) {
        return DocumentAnalysis.builder()
                .summary(generateSummary(content))
                .keywords(extractKeywords(content))
                .wordCount(content.length())
                // 阅读时长：以约 500 字/分钟估算，Math.max 保证最短 1 分钟，避免展示 0 分钟
                .readingTimeEstimate(Math.max(1, content.length() / 500))
                .build();
    }

    // ==================== TextRank 算法 ====================

    /**
     * 基于 TextRank 的自动摘要
     * <p>
     * 算法步骤：
     * <ol>
     *   <li>将文本分割为句子</li>
     *   <li>计算句子间的相似度（基于词重叠率）</li>
     *   <li>迭代计算句子权重（PageRank 思想）</li>
     *   <li>选取权重最高的 N 个句子作为摘要</li>
     * </ol>
     */
    private String generateSummaryByTextRank(String content) {
        List<String> sentences = splitSentences(content);
        if (sentences.size() <= MAX_SENTENCES) {
            return String.join("。", sentences);
        }

        // 分词（简化版：按空格和标点分词）
        List<Set<String>> sentenceWords = sentences.stream()
                .map(this::tokenize)
                .collect(Collectors.toList());

        // 计算句子相似度矩阵
        int n = sentences.size();
        double[][] similarity = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                similarity[i][j] = similarity[j][i] = calculateSimilarity(sentenceWords.get(i), sentenceWords.get(j));
            }
        }

        // TextRank 迭代
        double[] scores = new double[n];
        Arrays.fill(scores, 1.0);
        double dampingCoefficient = 0.85; // 阻尼系数
        for (int iter = 0; iter < 50; iter++) {
            double[] newScores = new double[n];
            for (int i = 0; i < n; i++) {
                double sum = 0;
                for (int j = 0; j < n; j++) {
                    if (i == j) continue;
                    double outWeight = 0;
                    for (int k = 0; k < n; k++) {
                        if (k != j) outWeight += similarity[j][k];
                    }
                    if (outWeight > 0) {
                        sum += similarity[j][i] / outWeight * scores[j];
                    }
                }
                newScores[i] = (1 - dampingCoefficient) + dampingCoefficient * sum;
            }
            scores = newScores;
        }

        // 选取 Top-N 句子（按原始顺序排列）
        List<int[]> ranked = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ranked.add(new int[]{i, (int) (scores[i] * 1000)});
        }
        ranked.sort((a, b) -> b[1] - a[1]);

        Set<Integer> topIndices = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(MAX_SENTENCES, ranked.size()); i++) {
            topIndices.add(ranked.get(i)[0]);
        }

        List<Integer> sortedIndices = new ArrayList<>(topIndices);
        Collections.sort(sortedIndices);

        StringBuilder summary = new StringBuilder();
        for (int idx : sortedIndices) {
            if (summary.length() > 0) summary.append("。");
            summary.append(sentences.get(idx));
        }

        return summary.toString();
    }

    /**
     * 基于 TextRank 的关键词提取
     */
    private List<String> extractKeywordsByTextRank(String content) {
        List<String> words = tokenize(content).stream().toList();
        if (words.isEmpty()) {
            return List.of();
        }

        // 统计词频
        Map<String, Integer> wordFreq = new HashMap<>();
        for (String word : words) {
            wordFreq.merge(word, 1, Integer::sum);
        }

        // 按词频排序
        return wordFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(MAX_KEYWORDS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 句子分割
     */
    private List<String> splitSentences(String content) {
        // 中文句号、英文句号、感叹号、问号、换行
        Pattern pattern = Pattern.compile("[^。！？.!?\\n]+[。！？.!?]?");
        Matcher matcher = pattern.matcher(content);
        List<String> sentences = new ArrayList<>();
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (sentence.length() >= MIN_SENTENCE_LENGTH) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    /**
     * 分词（简化版）
     */
    private Set<String> tokenize(String text) {
        // 移除标点和特殊字符，按空格分词
        String cleaned = text.replaceAll("[\\p{Punct}\\p{IsPunctuation}0-9a-zA-Z\\s]+", " ");
        Set<String> words = new HashSet<>();
        // 简单的中文 n-gram 分词（2-4字）
        for (int len = 2; len <= 4; len++) {
            for (int i = 0; i <= cleaned.length() - len; i++) {
                String gram = cleaned.substring(i, i + len).trim();
                if (gram.length() == len && !gram.contains(" ")) {
                    words.add(gram);
                }
            }
        }
        return words;
    }

    /**
     * 计算两个句子集合的相似度（Jaccard 系数）
     */
    private double calculateSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() || set2.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        return (double) intersection.size() / union.size();
    }

    // ==================== LLM 模式 ====================

    /**
     * 通过 LLM API 生成摘要
     * <p>
     * 调用 OpenAI 兼容接口（/v1/chat/completions），失败时降级到 TextRank。
     */
    private String generateSummaryByLlm(String content) {
        log.info("[AiSummaryApplicationService] LLM 摘要生成（API URL: {}）", properties.getAi().getLlmApiUrl());
        try {
            String prompt = "请总结以下文档的关键要点（不超过500字）：\n"
                    + content.substring(0, Math.min(content.length(), 10000));
            String response = callLlm(prompt);
            if (response == null || response.isEmpty()) {
                log.warn("[AiSummaryApplicationService] LLM 返回空结果，降级到 TextRank");
                return generateSummaryByTextRank(content);
            }
            return response;
        } catch (Exception e) {
            log.warn("[AiSummaryApplicationService] LLM 摘要失败，降级到 TextRank: {}", e.getMessage());
            return generateSummaryByTextRank(content);
        }
    }

    /**
     * 通过 LLM API 提取关键词
     * <p>
     * 调用 OpenAI 兼容接口，失败时降级到 TextRank。
     */
    private List<String> extractKeywordsByLlm(String content) {
        log.info("[AiSummaryApplicationService] LLM 关键词提取");
        try {
            String prompt = "请从以下文档中提取 " + MAX_KEYWORDS
                    + " 个关键词，以逗号分隔返回：\n"
                    + content.substring(0, Math.min(content.length(), 10000));
            String response = callLlm(prompt);
            if (response == null || response.isEmpty()) {
                log.warn("[AiSummaryApplicationService] LLM 返回空结果，降级到 TextRank");
                return extractKeywordsByTextRank(content);
            }
            return Arrays.stream(response.split("[,，\\n]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .limit(MAX_KEYWORDS)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[AiSummaryApplicationService] LLM 关键词提取失败，降级到 TextRank: {}", e.getMessage());
            return extractKeywordsByTextRank(content);
        }
    }

    /**
     * 调用 LLM Chat Completions 接口
     *
     * @param prompt 用户提示词
     * @return 模型回复文本，失败返回 null
     */
    private String callLlm(String prompt) {
        String llmApiUrl = properties.getAi().getLlmApiUrl();
        if (llmApiUrl == null || llmApiUrl.isEmpty()) {
            log.warn("[AiSummaryApplicationService] LLM API URL 未配置");
            return null;
        }

        Map<String, Object> request = Map.of(
                "model", properties.getAi().getLlmModel(),
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String llmApiKey = properties.getAi().getLlmApiKey();
        if (llmApiKey != null && !llmApiKey.isEmpty()) {
            headers.setBearerAuth(llmApiKey);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        ParameterizedTypeReference<Map<String, Object>> typeRef = new ParameterizedTypeReference<>() {};
        ResponseEntity<Map<String, Object>> response = nextwikiRestTemplate.exchange(
                llmApiUrl, HttpMethod.POST, entity, typeRef);

        Map<String, Object> body = response.getBody();
        if (body == null) {
            return null;
        }

        Object choicesObj = body.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }

        if (!(choices.get(0) instanceof Map<?, ?> choice)) {
            return null;
        }

        if (!(choice.get("message") instanceof Map<?, ?> message)) {
            return null;
        }

        Object contentObj = message.get("content");
        return contentObj != null ? contentObj.toString().trim() : null;
    }

    /**
     * 文档分析结果
     */
    @Data
    @Builder
    public static class DocumentAnalysis {
        /** 文档摘要文本（LLM 或 TextRank 生成） */
        private String summary;
        /** 关键词列表（最多 {@link #MAX_KEYWORDS} 个） */
        private List<String> keywords;
        /** 文档字数（按字符数计，非分词数） */
        private int wordCount;
        /** 预估阅读时长（分钟），约 500 字/分钟折算，最小为 1，用于前端展示 */
        private int readingTimeEstimate;
    }
}
