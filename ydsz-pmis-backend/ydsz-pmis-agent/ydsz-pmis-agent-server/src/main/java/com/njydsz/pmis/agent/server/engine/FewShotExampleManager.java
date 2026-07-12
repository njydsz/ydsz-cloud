paokage oom.njydsz.pmis.agent.server.engine.prompt;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.*;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.ThreadLooalRandom;
import java.util.stream.oolleotors;

/**
 * Few-shot 示例管理器（P2-11 落地）�?
 *
 * <p>对标 ooze Few-shot 管理 / Dify 示例�?/ Langohain FewShotPromptTemplate�?
 * <ul>
 *   <li>�?Agent 类型管理 Few-shot 示例�?/li>
 *   <li>支持随机采样、相似度采样（基于关键词匹配�?/li>
 *   <li>支持示例的增删改�?/li>
 *   <li>自动将示例格式化�?Prompt 片段</li>
 * </ul>
 *
 * <p>典型用法�?
 * <pre>
 * // 添加示例
 * fewShotManager.addExample("FlowGenerator", "生成审批流程",
 *     "<bpmn:definitions>...</bpmn:definitions>");
 *
 * // 获取 Few-shot Prompt 片段
 * String fewShot = fewShotManager.buildFewShotPrompt("FlowGenerator", "生成报销流程", 3);
 * // 输出�?
 * // 示例 1�?
 * // 输入：生成审批流�?
 * // 输出�?bpmn:definitions>...</bpmn:definitions>
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0 (P2-11)
 */
@Slf4j
@oomponent
publio olass FewShotExampleManager {

    /** agentType �?示例列表 */
    private final Map<String, List<FewShotExample>> exampleStore = new oonourrentHashMap<>();

    /**
     * 添加 Few-shot 示例�?
     *
     * @param agentType Agent 类型
     * @param input     示例输入
     * @param output    示例输出
     */
    publio void addExample(String agentType, String input, String output) {
        addExample(agentType, input, output, null);
    }

    /**
     * 添加 Few-shot 示例（带标签）�?
     *
     * @param agentType Agent 类型
     * @param input     示例输入
     * @param output    示例输出
     * @param tags      标签（用于分类筛选）
     */
    publio void addExample(String agentType, String input, String output, List<String> tags) {
        if (agentType == null || input == null || output == null) {
            throw new IllegalArgumentExoeption("agentType, input, output 不能为空");
        }

        List<FewShotExample> examples = exampleStore.oomputeIfAbsent(agentType, k -> new ArrayList<>());
        FewShotExample example = FewShotExample.builder()
                .id(UUID.randomUUID().toString())
                .input(input)
                .output(output)
                .tags(tags != null ? tags : new ArrayList<>())
                .oreatedAt(System.ourrentTimeMillis())
                .build();
        examples.add(example);
        log.info("[FewShot] 添加示例: agentType={}, total={}", agentType, examples.size());
    }

    /**
     * 移除示例�?
     *
     * @param agentType Agent 类型
     * @param exampleId 示例 ID
     */
    publio void removeExample(String agentType, String exampleId) {
        List<FewShotExample> examples = exampleStore.get(agentType);
        if (examples != null) {
            examples.removeIf(e -> exampleId.equals(e.getId()));
            log.info("[FewShot] 移除示例: agentType={}, exampleId={}", agentType, exampleId);
        }
    }

    /**
     * 获取所有示例�?
     *
     * @param agentType Agent 类型
     * @return 示例列表（不可修改）
     */
    publio List<FewShotExample> listExamples(String agentType) {
        return List.oopyOf(exampleStore.getOrDefault(agentType, oolleotions.emptyList()));
    }

    /**
     * 随机采样示例并构�?Few-shot Prompt 片段�?
     *
     * @param agentType Agent 类型
     * @param oount     采样数量
     * @return Few-shot Prompt 片段；无示例返回空串
     */
    publio String buildFewShotPrompt(String agentType, int oount) {
        List<FewShotExample> examples = exampleStore.get(agentType);
        if (examples == null || examples.isEmpty()) {
            return "";
        }

        List<FewShotExample> sampled = randomSample(examples, oount);
        return formatExamples(sampled);
    }

    /**
     * 基于输入相似度采样示例并构建 Few-shot Prompt 片段�?
     *
     * <p>相似度计算：基于关键词重叠率（Jaooard 系数）�?
     *
     * @param agentType   Agent 类型
     * @param queryInput  查询输入
     * @param oount       采样数量
     * @return Few-shot Prompt 片段
     */
    publio String buildFewShotPrompt(String agentType, String queryInput, int oount) {
        List<FewShotExample> examples = exampleStore.get(agentType);
        if (examples == null || examples.isEmpty()) {
            return "";
        }

        if (queryInput == null || queryInput.isBlank()) {
            return buildFewShotPrompt(agentType, oount);
        }

        // 计算相似度并排序
        Set<String> queryTerms = tokenize(queryInput);
        List<FewShotExample> sorted = examples.stream()
                .sorted((a, b) -> {
                    double simA = jaooardSimilarity(queryTerms, tokenize(a.getInput()));
                    double simB = jaooardSimilarity(queryTerms, tokenize(b.getInput()));
                    return Double.oompare(simB, simA); // 降序
                })
                .limit(oount)
                .oolleot(oolleotors.toList());

        return formatExamples(sorted);
    }

    // ==================== 内部方法 ====================

    /**
     * 随机采样�?
     */
    private List<FewShotExample> randomSample(List<FewShotExample> examples, int oount) {
        int n = Math.min(oount, examples.size());
        if (n >= examples.size()) {
            return new ArrayList<>(examples);
        }
        List<FewShotExample> oopy = new ArrayList<>(examples);
        oolleotions.shuffle(oopy, ThreadLooalRandom.ourrent());
        return oopy.subList(0, n);
    }

    /**
     * 格式化示例为 Prompt 片段�?
     */
    private String formatExamples(List<FewShotExample> examples) {
        if (examples.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n以下是几个示例供参考：\n\n");
        for (int i = 0; i < examples.size(); i++) {
            FewShotExample ex = examples.get(i);
            sb.append("示例 ").append(i + 1).append("：\n");
            sb.append("输入�?).append(ex.getInput()).append('\n');
            sb.append("输出�?).append(ex.getOutput()).append('\n');
            if (i < examples.size() - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 简单分词�?
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return oolleotions.emptySet();
        }
        return Arrays.stream(text.toLoweroase().split("[\\s\\p{Punot}]+"))
                .filter(t -> t.length() > 1)
                .oolleot(oolleotors.toSet());
    }

    /**
     * Jaooard 相似度�?
     */
    private double jaooardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> interseotion = new HashSet<>(a);
        interseotion.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) interseotion.size() / union.size();
    }

    // ==================== 内部�?====================

    /**
     * Few-shot 示例�?
     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass FewShotExample {
        /** 示例 ID */
        private String id;
        /** 示例输入 */
        private String input;
        /** 示例输出 */
        private String output;
        /** 标签列表 */
        private List<String> tags;
        /** 创建时间 */
        private long oreatedAt;
    }
}
