package com.njydsz.pmis.agent.server.engine.eval;

import com.njydsz.pmis.agent.server.engine.AgentContext;
import com.njydsz.pmis.agent.server.engine.AgentResult;
import com.njydsz.pmis.agent.server.engine.llm.LlmProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Agent 评测框架（P4-8 落地，P1-1 重构）。
 *
 * <p>对标 Coze 模型评估 / Dify 应用评估 / LangSmith Evaluation：
 * <ul>
 *   <li>批量运行预定义测试用例，自动评估 Agent 输出质量</li>
 *   <li>支持多种评估指标：准确率、相似度、关键词覆盖、响应时间、Token 消耗</li>
 *   <li>支持自定义评估器（LLM-as-Judge、规则匹配、人工标注）</li>
 *   <li>生成评测报告，便于横向对比不同 Prompt / 模型 / 配置</li>
 * </ul>
 *
 * <p><b>P1-1 重构要点</b>：
 * <ol>
 *   <li>使用 {@link EvaluableAgent} 接口替代反射调用，编译期类型安全</li>
 *   <li>LLM-as-Judge 接入真实 LLM（通过 {@link LlmProvider}），不再返回硬编码分数</li>
 *   <li>共享线程池，避免每次 run() 创建/销毁线程池的开销</li>
 *   <li>支持自定义评测器（{@link CustomEvaluator} 函数式接口）</li>
 * </ol>
 *
 * <p>典型用法：
 * <pre>
 * // 方式1: 使用 EvaluableAgent 接口（推荐）
 * AgentEvaluationFramework framework = new AgentEvaluationFramework(
 *     (input, ctx) -> agent.execute(input, ctx),
 *     llmProvider,
 *     4  // 并行度
 * );
 *
 * // 方式2: 兼容旧代码（反射适配，不推荐）
 * AgentEvaluationFramework framework = AgentEvaluationFramework.forObject(agent, null, 1);
 *
 * // 定义测试用例
 * List&lt;EvaluationCase&gt; cases = List.of(
 *     EvaluationCase.builder()
 *         .id("case-001")
 *         .userInput("项目CPI是多少？")
 *         .expectedOutput("CPI")
 *         .evaluator(EvaluationCase.EvaluatorType.KEYWORD_CONTAINS)
 *         .build()
 * );
 *
 * // 执行评测
 * EvaluationReport report = framework.run(cases);
 * System.out.println(report.getSummary());
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-8), 1.1.0 (P1-1 重构)
 */
@Slf4j
public class AgentEvaluationFramework implements AutoCloseable {

    /** 被评测的 Agent（接口化，P1-1） */
    private final EvaluableAgent agent;

    /** LLM Provider（用于 LLM_AS_JUDGE 评测器，可为 null） */
    private final LlmProvider llmProvider;

    /** 并行度 */
    private final int parallelism;

    /** 共享线程池（P1-1：避免每次 run 创建/销毁） */
    private final ExecutorService executor;

    /** LLM-as-Judge 系统提示词 */
    private static final String LLM_JUDGE_SYSTEM_PROMPT = """
            你是一个严格的评估器。请根据以下信息对 Agent 的回答进行评分。
            
            用户问题: %s
            期望回答: %s
            实际回答: %s
            
            评分标准（0.0 ~ 1.0）：
            - 1.0: 完全正确，包含所有关键信息
            - 0.8: 基本正确，缺少少量细节
            - 0.5: 部分正确，有重要遗漏或轻微错误
            - 0.2: 存在明显错误
            - 0.0: 完全错误或无关
            
            请只输出一个数字（0.0 到 1.0），不要输出其他内容。
            """;

    /**
     * 构造评测框架（指定 LLM Provider，P1-1 推荐）。
     *
     * @param agent       被评测的 Agent（实现 {@link EvaluableAgent} 接口）
     * @param llmProvider LLM Provider（用于 LLM_AS_JUDGE，可为 null 则降级为长度启发式）
     * @param parallelism 并行度（1 表示串行）
     */
    public AgentEvaluationFramework(EvaluableAgent agent, LlmProvider llmProvider, int parallelism) {
        this.agent = agent;
        this.llmProvider = llmProvider;
        this.parallelism = parallelism > 0 ? parallelism : 1;
        this.executor = createExecutor(this.parallelism);
    }

    /**
     * 构造评测框架（串行执行，无 LLM Provider）。
     *
     * @param agent 被评测的 Agent
     */
    public AgentEvaluationFramework(EvaluableAgent agent) {
        this(agent, null, 1);
    }

    /**
     * 兼容旧代码的工厂方法：通过反射适配任意具有 execute(String, AgentContext) 方法的对象。
     *
     * <p><b>不推荐使用</b>，请优先实现 {@link EvaluableAgent} 接口。
     *
     * @param agentObject 任意 Agent 对象（需有 execute 方法）
     * @param llmProvider LLM Provider（可为 null）
     * @param parallelism 并行度
     * @return 评测框架实例
     */
    public static AgentEvaluationFramework forObject(Object agentObject, LlmProvider llmProvider, int parallelism) {
        EvaluableAgent adapter = (input, ctx) -> {
            try {
                var executeMethod = agentObject.getClass().getMethod("execute",
                        String.class, AgentContext.class);
                Object result = executeMethod.invoke(agentObject, input, ctx);
                if (result instanceof AgentResult ar) {
                    return ar.getSuggestion() != null ? ar.getSuggestion() : ar.toString();
                }
                return result == null ? "" : result.toString();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Agent 未实现 execute(String, AgentContext) 方法", e);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause() != null ? (Exception) e.getCause() : e;
            }
        };
        return new AgentEvaluationFramework(adapter, llmProvider, parallelism);
    }

    /**
     * 执行评测。
     *
     * @param cases 测试用例列表
     * @return 评测报告
     */
    public EvaluationReport run(List<EvaluationCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return EvaluationReport.empty();
        }

        log.info("[EvalFramework] 开始评测, {} 个用例, 并行度={}", cases.size(), parallelism);

        List<EvaluationResult> results;
        if (parallelism == 1) {
            // 串行执行
            results = new ArrayList<>();
            for (EvaluationCase testCase : cases) {
                results.add(evaluateOne(testCase));
            }
        } else {
            // 并行执行（使用共享线程池）
            List<CompletableFuture<EvaluationResult>> futures = cases.stream()
                    .map(tc -> CompletableFuture.supplyAsync(() -> evaluateOne(tc), executor))
                    .collect(Collectors.toList());
            results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
        }

        // 生成报告
        EvaluationReport report = buildReport(results);
        log.info("[EvalFramework] 评测完成: {}", report.getSummary());
        return report;
    }

    /**
     * 评估单个用例。
     */
    private EvaluationResult evaluateOne(EvaluationCase testCase) {
        long startTime = System.currentTimeMillis();
        try {
            // 调用 Agent 执行
            String actualOutput = agent.execute(testCase.getUserInput(), null);
            long elapsed = System.currentTimeMillis() - startTime;

            // 评估
            double score = evaluate(testCase, actualOutput);
            boolean passed = score >= testCase.getPassThreshold();

            return EvaluationResult.builder()
                    .caseId(testCase.getId())
                    .userInput(testCase.getUserInput())
                    .expectedOutput(testCase.getExpectedOutput())
                    .actualOutput(actualOutput)
                    .score(score)
                    .passed(passed)
                    .elapsedMs(elapsed)
                    .evaluatorType(testCase.getEvaluator())
                    .build();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            return EvaluationResult.builder()
                    .caseId(testCase.getId())
                    .userInput(testCase.getUserInput())
                    .actualOutput("ERROR: " + e.getMessage())
                    .score(0.0)
                    .passed(false)
                    .elapsedMs(elapsed)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 根据评估器类型计算分数。
     */
    private double evaluate(EvaluationCase testCase, String actualOutput) {
        if (actualOutput == null) return 0.0;
        String expected = testCase.getExpectedOutput();

        switch (testCase.getEvaluator()) {
            case EXACT_MATCH:
                return actualOutput.trim().equals(expected == null ? "" : expected.trim()) ? 1.0 : 0.0;

            case KEYWORD_CONTAINS:
                if (expected == null || expected.isEmpty()) return 1.0;
                return actualOutput.toLowerCase().contains(expected.toLowerCase()) ? 1.0 : 0.0;

            case COSINE_SIMILARITY:
                // 简化：用 Jaccard 相似度近似
                return jaccardSimilarity(actualOutput, expected);

            case LLM_AS_JUDGE:
                return evaluateWithLlm(testCase, actualOutput);

            case CUSTOM:
                // P1-1: 使用注入的自定义评测器
                if (testCase.getCustomEvaluator() != null) {
                    return testCase.getCustomEvaluator().evaluate(expected, actualOutput);
                }
                log.warn("[EvalFramework] 用例 {} 使用 CUSTOM 评估器但未注入 customEvaluator，返回中性分数 0.5", testCase.getId());
                return 0.5;

            default:
                return 0.0;
        }
    }

    /**
     * 使用真实 LLM 进行 LLM-as-Judge 评估（P1-1 重构）。
     *
     * <p>当配置了 LlmProvider 时，构造评估 prompt 调用 LLM 打分。
     * 未配置时降级为长度启发式（向后兼容）。
     */
    private double evaluateWithLlm(EvaluationCase testCase, String actualOutput) {
        if (llmProvider == null) {
            // 降级：LLM 未配置时使用长度启发式
            log.debug("[EvalFramework] LLM Provider 未配置，降级为长度启发式评分");
            return actualOutput.length() > 10 ? 0.8 : 0.3;
        }

        try {
            String prompt = String.format(LLM_JUDGE_SYSTEM_PROMPT,
                    testCase.getUserInput(),
                    testCase.getExpectedOutput(),
                    actualOutput);

            String llmResponse = llmProvider.chat(
                    "你是一个专业的 AI 评估器，请严格按照评分标准打分。",
                    prompt,
                    null  // 无 AgentContext
            );

            // 解析 LLM 返回的分数（提取第一个浮点数）
            double score = parseScore(llmResponse);
            log.debug("[EvalFramework] LLM-as-Judge 用例={}, LLM输出={}, 评分={}",
                    testCase.getId(), llmResponse, score);
            return score;
        } catch (Exception e) {
            log.warn("[EvalFramework] LLM-as-Judge 评估失败，降级为长度启发式: {}", e.getMessage());
            return actualOutput.length() > 10 ? 0.8 : 0.3;
        }
    }

    /**
     * 从 LLM 输出中解析分数（0.0 ~ 1.0）。
     */
    private double parseScore(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) return 0.0;
        // 提取第一个浮点数
        String trimmed = llmResponse.trim()
                .replaceAll("[^0-9.]", " ")
                .trim();
        if (trimmed.isEmpty()) return 0.0;
        try {
            double score = Double.parseDouble(trimmed.split("\\s+")[0]);
            return Math.max(0.0, Math.min(1.0, score));
        } catch (NumberFormatException e) {
            log.warn("[EvalFramework] 无法解析 LLM 评分: {}", llmResponse);
            return 0.0;
        }
    }

    /**
     * Jaccard 相似度（用于文本相似度近似评估）。
     */
    private double jaccardSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        Set<String> setA = Arrays.stream(a.toLowerCase().split("[\\s\\p{Punct}]+"))
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        Set<String> setB = Arrays.stream(b.toLowerCase().split("[\\s\\p{Punct}]+"))
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        if (setA.isEmpty() && setB.isEmpty()) return 1.0;
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return (double) intersection.size() / union.size();
    }

    /**
     * 构建评测报告。
     */
    private EvaluationReport buildReport(List<EvaluationResult> results) {
        int total = results.size();
        int passed = (int) results.stream().filter(EvaluationResult::isPassed).count();
        double avgScore = results.stream().mapToDouble(EvaluationResult::getScore).average().orElse(0);
        double avgElapsed = results.stream().mapToLong(EvaluationResult::getElapsedMs).average().orElse(0);
        double passRate = total > 0 ? (double) passed / total : 0;

        return EvaluationReport.builder()
                .results(results)
                .totalCases(total)
                .passedCases(passed)
                .failedCases(total - passed)
                .passRate(passRate)
                .averageScore(avgScore)
                .averageElapsedMs(avgElapsed)
                .summary(String.format("通过率: %.1f%% (%d/%d), 平均分: %.2f, 平均耗时: %.0fms",
                        passRate * 100, passed, total, avgScore, avgElapsed))
                .build();
    }

    /**
     * 创建共享线程池（P1-1：避免每次 run 创建/销毁）。
     */
    private static ExecutorService createExecutor(int parallelism) {
        if (parallelism <= 1) {
            // 串行模式使用同线程执行器
            return Executors.newSingleThreadExecutor(new EvalThreadFactory());
        }
        return Executors.newFixedThreadPool(parallelism, new EvalThreadFactory());
    }

    /** 评测线程工厂 */
    private static class EvalThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "eval-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * 关闭共享线程池（P1-1）。
     *
     * <p>实现 {@link AutoCloseable}，支持 try-with-resources 语法。
     * 也可由 Spring 容器管理生命周期。
     */
    @Override
    public void close() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("[EvalFramework] 线程池已关闭");
        }
    }
}
