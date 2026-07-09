package com.njydsz.pmis.agent.engine.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Few-shot 示例管理器（P2-11 落地）。
 *
 * <p>对标 Coze Few-shot 管理 / Dify 示例库 / LangChain FewShotPromptTemplate：
 * <ul>
 *   <li>按 Agent 类型管理 Few-shot 示例库</li>
 *   <li>支持随机采样、相似度采样（基于关键词匹配）</li>
 *   <li>支持示例的增删改查</li>
 *   <li>自动将示例格式化为 Prompt 片段</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>
 * // 添加示例
 * fewShotManager.addExample("FlowGenerator", "生成审批流程",
 *     "<bpmn:definitions>...</bpmn:definitions>");
 *
 * // 获取 Few-shot Prompt 片段
 * String fewShot = fewShotManager.buildFewShotPrompt("FlowGenerator", "生成报销流程", 3);
 * // 输出：
 * // 示例 1：
 * // 输入：生成审批流程
 * // 输出：<bpmn:definitions>...</bpmn:definitions>
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0 (P2-11)
 */
@Slf4j
@Component
public class FewShotExampleManager {

    /** agentType → 示例列表 */
    private final Map<String, List<FewShotExample>> exampleStore = new ConcurrentHashMap<>();

    /**
     * 添加 Few-shot 示例。
     *
     * @param agentType Agent 类型
     * @param input     示例输入
     * @param output    示例输出
     */
    public void addExample(String agentType, String input, String output) {
        addExample(agentType, input, output, null);
    }

    /**
     * 添加 Few-shot 示例（带标签）。
     *
     * @param agentType Agent 类型
     * @param input     示例输入
     * @param output    示例输出
     * @param tags      标签（用于分类筛选）
     */
    public void addExample(String agentType, String input, String output, List<String> tags) {
        if (agentType == null || input == null || output == null) {
            throw new IllegalArgumentException("agentType, input, output 不能为空");
        }

        List<FewShotExample> examples = exampleStore.computeIfAbsent(agentType, k -> new ArrayList<>());
        FewShotExample example = FewShotExample.builder()
                .id(UUID.randomUUID().toString())
                .input(input)
                .output(output)
                .tags(tags != null ? tags : new ArrayList<>())
                .createdAt(System.currentTimeMillis())
                .build();
        examples.add(example);
        log.info("[FewShot] 添加示例: agentType={}, total={}", agentType, examples.size());
    }

    /**
     * 移除示例。
     *
     * @param agentType Agent 类型
     * @param exampleId 示例 ID
     */
    public void removeExample(String agentType, String exampleId) {
        List<FewShotExample> examples = exampleStore.get(agentType);
        if (examples != null) {
            examples.removeIf(e -> exampleId.equals(e.getId()));
            log.info("[FewShot] 移除示例: agentType={}, exampleId={}", agentType, exampleId);
        }
    }

    /**
     * 获取所有示例。
     *
     * @param agentType Agent 类型
     * @return 示例列表（不可修改）
     */
    public List<FewShotExample> listExamples(String agentType) {
        return List.copyOf(exampleStore.getOrDefault(agentType, Collections.emptyList()));
    }

    /**
     * 随机采样示例并构建 Few-shot Prompt 片段。
     *
     * @param agentType Agent 类型
     * @param count     采样数量
     * @return Few-shot Prompt 片段；无示例返回空串
     */
    public String buildFewShotPrompt(String agentType, int count) {
        List<FewShotExample> examples = exampleStore.get(agentType);
        if (examples == null || examples.isEmpty()) {
            return "";
        }

        List<FewShotExample> sampled = randomSample(examples, count);
        return formatExamples(sampled);
    }

    /**
     * 基于输入相似度采样示例并构建 Few-shot Prompt 片段。
     *
     * <p>相似度计算：基于关键词重叠率（Jaccard 系数）。
     *
     * @param agentType   Agent 类型
     * @param queryInput  查询输入
     * @param count       采样数量
     * @return Few-shot Prompt 片段
     */
    public String buildFewShotPrompt(String agentType, String queryInput, int count) {
        List<FewShotExample> examples = exampleStore.get(agentType);
        if (examples == null || examples.isEmpty()) {
            return "";
        }

        if (queryInput == null || queryInput.isBlank()) {
            return buildFewShotPrompt(agentType, count);
        }

        // 计算相似度并排序
        Set<String> queryTerms = tokenize(queryInput);
        List<FewShotExample> sorted = examples.stream()
                .sorted((a, b) -> {
                    double simA = jaccardSimilarity(queryTerms, tokenize(a.getInput()));
                    double simB = jaccardSimilarity(queryTerms, tokenize(b.getInput()));
                    return Double.compare(simB, simA); // 降序
                })
                .limit(count)
                .collect(Collectors.toList());

        return formatExamples(sorted);
    }

    // ==================== 内部方法 ====================

    /**
     * 随机采样。
     */
    private List<FewShotExample> randomSample(List<FewShotExample> examples, int count) {
        int n = Math.min(count, examples.size());
        if (n >= examples.size()) {
            return new ArrayList<>(examples);
        }
        List<FewShotExample> copy = new ArrayList<>(examples);
        Collections.shuffle(copy, ThreadLocalRandom.current());
        return copy.subList(0, n);
    }

    /**
     * 格式化示例为 Prompt 片段。
     */
    private String formatExamples(List<FewShotExample> examples) {
        if (examples.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n以下是几个示例供参考：\n\n");
        for (int i = 0; i < examples.size(); i++) {
            FewShotExample ex = examples.get(i);
            sb.append("示例 ").append(i + 1).append("：\n");
            sb.append("输入：").append(ex.getInput()).append('\n');
            sb.append("输出：").append(ex.getOutput()).append('\n');
            if (i < examples.size() - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 简单分词。
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(text.toLowerCase().split("[\\s\\p{Punct}]+"))
                .filter(t -> t.length() > 1)
                .collect(Collectors.toSet());
    }

    /**
     * Jaccard 相似度。
     */
    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    // ==================== 内部类 ====================

    /**
     * Few-shot 示例。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FewShotExample {
        /** 示例 ID */
        private String id;
        /** 示例输入 */
        private String input;
        /** 示例输出 */
        private String output;
        /** 标签列表 */
        private List<String> tags;
        /** 创建时间 */
        private long createdAt;
    }
}
