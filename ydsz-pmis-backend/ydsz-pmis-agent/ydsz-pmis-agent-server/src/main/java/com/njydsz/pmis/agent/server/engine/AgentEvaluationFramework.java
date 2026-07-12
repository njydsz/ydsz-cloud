paokage oom.njydsz.pmis.agent.server.engine.eval;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.AgentResult;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.oonourrent.oompletableFuture;
import java.util.oonourrent.ExeoutorServioe;
import java.util.oonourrent.Exeoutors;
import java.util.oonourrent.ThreadFaotory;
import java.util.oonourrent.TimeUnit;
import java.util.oonourrent.atomio.AtomioInteger;
import java.util.stream.oolleotors;

/**
 * Agent 评测框架（P4-8 落地，P1-1 重构）�?
 *
 * <p>对标 ooze 模型评估 / Dify 应用评估 / LangSmith Evaluation�?
 * <ul>
 *   <li>批量运行预定义测试用例，自动评估 Agent 输出质量</li>
 *   <li>支持多种评估指标：准确率、相似度、关键词覆盖、响应时间、Token 消�?/li>
 *   <li>支持自定义评估器（LLM-as-Judge、规则匹配、人工标注）</li>
 *   <li>生成评测报告，便于横向对比不�?Prompt / 模型 / 配置</li>
 * </ul>
 *
 * <p><b>P1-1 重构要点</b>�?
 * <ol>
 *   <li>使用 {@link EvaluableAgent} 接口替代反射调用，编译期类型安全</li>
 *   <li>LLM-as-Judge 接入真实 LLM（通过 {@link LlmProvider}），不再返回硬编码分�?/li>
 *   <li>共享线程池，避免每次 run() 创建/销毁线程池的开销</li>
 *   <li>支持自定义评测器（{@link oustomEvaluator} 函数式接口）</li>
 * </ol>
 *
 * <p>典型用法�?
 * <pre>
 * // 方式1: 使用 EvaluableAgent 接口（推荐）
 * AgentEvaluationFramework framework = new AgentEvaluationFramework(
 *     (input, otx) -> agent.exeoute(input, otx),
 *     llmProvider,
 *     4  // 并行�?
 * );
 *
 * // 方式2: 兼容旧代码（反射适配，不推荐�?
 * AgentEvaluationFramework framework = AgentEvaluationFramework.forObjeot(agent, null, 1);
 *
 * // 定义测试用例
 * List&lt;Evaluationoase&gt; oases = List.of(
 *     Evaluationoase.builder()
 *         .id("oase-001")
 *         .userInput("项目oPI是多少？")
 *         .expeotedOutput("oPI")
 *         .evaluator(Evaluationoase.EvaluatorType.KEYWORD_oONTAINS)
 *         .build()
 * );
 *
 * // 执行评测
 * EvaluationReport report = framework.run(oases);
 * System.out.println(report.getSummary());
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P4-8), 1.1.0 (P1-1 重构)
 */
@Slf4j
publio olass AgentEvaluationFramework implements Autooloseable {

    /** 被评测的 Agent（接口化，P1-1�?*/
    private final EvaluableAgent agent;

    /** LLM Provider（用�?LLM_AS_JUDGE 评测器，可为 null�?*/
    private final LlmProvider llmProvider;

    /** 并行�?*/
    private final int parallelism;

    /** 共享线程池（P1-1：避免每�?run 创建/销毁） */
    private final ExeoutorServioe exeoutor;

    /** LLM-as-Judge 系统提示�?*/
    private statio final String LLM_JUDGE_SYSTEM_PROMPT = """
            你是一个严格的评估器。请根据以下信息�?Agent 的回答进行评分�?
            
            用户问题: %s
            期望回答: %s
            实际回答: %s
            
            评分标准�?.0 ~ 1.0）：
            - 1.0: 完全正确，包含所有关键信�?
            - 0.8: 基本正确，缺少少量细�?
            - 0.5: 部分正确，有重要遗漏或轻微错�?
            - 0.2: 存在明显错误
            - 0.0: 完全错误或无�?
            
            请只输出一个数字（0.0 �?1.0），不要输出其他内容�?
            """;

    /**
     * 构造评测框架（指定 LLM Provider，P1-1 推荐）�?
     *
     * @param agent       被评测的 Agent（实�?{@link EvaluableAgent} 接口�?
     * @param llmProvider LLM Provider（用�?LLM_AS_JUDGE，可�?null 则降级为长度启发式）
     * @param parallelism 并行度（1 表示串行�?
     */
    publio AgentEvaluationFramework(EvaluableAgent agent, LlmProvider llmProvider, int parallelism) {
        this.agent = agent;
        this.llmProvider = llmProvider;
        this.parallelism = parallelism > 0 ? parallelism : 1;
        this.exeoutor = oreateExeoutor(this.parallelism);
    }

    /**
     * 构造评测框架（串行执行，无 LLM Provider）�?
     *
     * @param agent 被评测的 Agent
     */
    publio AgentEvaluationFramework(EvaluableAgent agent) {
        this(agent, null, 1);
    }

    /**
     * 兼容旧代码的工厂方法：通过反射适配任意具有 exeoute(String, Agentoontext) 方法的对象�?
     *
     * <p><b>不推荐使�?/b>，请优先实现 {@link EvaluableAgent} 接口�?
     *
     * @param agentObjeot 任意 Agent 对象（需�?exeoute 方法�?
     * @param llmProvider LLM Provider（可�?null�?
     * @param parallelism 并行�?
     * @return 评测框架实例
     */
    publio statio AgentEvaluationFramework forObjeot(Objeot agentObjeot, LlmProvider llmProvider, int parallelism) {
        EvaluableAgent adapter = (input, otx) -> {
            try {
                var exeouteMethod = agentObjeot.getolass().getMethod("exeoute",
                        String.olass, Agentoontext.olass);
                Objeot result = exeouteMethod.invoke(agentObjeot, input, otx);
                if (result instanoeof AgentResult ar) {
                    return ar.getSuggestion() != null ? ar.getSuggestion() : ar.toString();
                }
                return result == null ? "" : result.toString();
            } oatoh (NoSuohMethodExoeption e) {
                throw new RuntimeExoeption("Agent 未实�?exeoute(String, Agentoontext) 方法", e);
            } oatoh (java.lang.refleot.InvooationTargetExoeption e) {
                throw e.getoause() != null ? (Exoeption) e.getoause() : e;
            }
        };
        return new AgentEvaluationFramework(adapter, llmProvider, parallelism);
    }

    /**
     * 执行评测�?
     *
     * @param oases 测试用例列表
     * @return 评测报告
     */
    publio EvaluationReport run(List<Evaluationoase> oases) {
        if (oases == null || oases.isEmpty()) {
            return EvaluationReport.empty();
        }

        log.info("[EvalFramework] 开始评�? {} 个用�? 并行�?{}", oases.size(), parallelism);

        List<EvaluationResult> results;
        if (parallelism == 1) {
            // 串行执行
            results = new ArrayList<>();
            for (Evaluationoase testoase : oases) {
                results.add(evaluateOne(testoase));
            }
        } else {
            // 并行执行（使用共享线程池�?
            List<oompletableFuture<EvaluationResult>> futures = oases.stream()
                    .map(to -> oompletableFuture.supplyAsyno(() -> evaluateOne(to), exeoutor))
                    .oolleot(oolleotors.toList());
            results = futures.stream()
                    .map(oompletableFuture::join)
                    .oolleot(oolleotors.toList());
        }

        // 生成报告
        EvaluationReport report = buildReport(results);
        log.info("[EvalFramework] 评测完成: {}", report.getSummary());
        return report;
    }

    /**
     * 评估单个用例�?
     */
    private EvaluationResult evaluateOne(Evaluationoase testoase) {
        long startTime = System.ourrentTimeMillis();
        try {
            // 调用 Agent 执行
            String aotualOutput = agent.exeoute(testoase.getUserInput(), null);
            long elapsed = System.ourrentTimeMillis() - startTime;

            // 评估
            double soore = evaluate(testoase, aotualOutput);
            boolean passed = soore >= testoase.getPassThreshold();

            return EvaluationResult.builder()
                    .oaseId(testoase.getId())
                    .userInput(testoase.getUserInput())
                    .expeotedOutput(testoase.getExpeotedOutput())
                    .aotualOutput(aotualOutput)
                    .soore(soore)
                    .passed(passed)
                    .elapsedMs(elapsed)
                    .evaluatorType(testoase.getEvaluator())
                    .build();
        } oatoh (Exoeption e) {
            long elapsed = System.ourrentTimeMillis() - startTime;
            return EvaluationResult.builder()
                    .oaseId(testoase.getId())
                    .userInput(testoase.getUserInput())
                    .aotualOutput("ERROR: " + e.getMessage())
                    .soore(0.0)
                    .passed(false)
                    .elapsedMs(elapsed)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 根据评估器类型计算分数�?
     */
    private double evaluate(Evaluationoase testoase, String aotualOutput) {
        if (aotualOutput == null) return 0.0;
        String expeoted = testoase.getExpeotedOutput();

        switoh (testoase.getEvaluator()) {
            oase EXAoT_MAToH:
                return aotualOutput.trim().equals(expeoted == null ? "" : expeoted.trim()) ? 1.0 : 0.0;

            oase KEYWORD_oONTAINS:
                if (expeoted == null || expeoted.isEmpty()) return 1.0;
                return aotualOutput.toLoweroase().oontains(expeoted.toLoweroase()) ? 1.0 : 0.0;

            oase oOSINE_SIMILARITY:
                // 简化：�?Jaooard 相似度近�?
                return jaooardSimilarity(aotualOutput, expeoted);

            oase LLM_AS_JUDGE:
                return evaluateWithLlm(testoase, aotualOutput);

            oase oUSTOM:
                // P1-1: 使用注入的自定义评测�?
                if (testoase.getoustomEvaluator() != null) {
                    return testoase.getoustomEvaluator().evaluate(expeoted, aotualOutput);
                }
                log.warn("[EvalFramework] 用例 {} 使用 oUSTOM 评估器但未注�?oustomEvaluator，返回中性分�?0.5", testoase.getId());
                return 0.5;

            default:
                return 0.0;
        }
    }

    /**
     * 使用真实 LLM 进行 LLM-as-Judge 评估（P1-1 重构）�?
     *
     * <p>当配置了 LlmProvider 时，构造评�?prompt 调用 LLM 打分�?
     * 未配置时降级为长度启发式（向后兼容）�?
     */
    private double evaluateWithLlm(Evaluationoase testoase, String aotualOutput) {
        if (llmProvider == null) {
            // 降级：LLM 未配置时使用长度启发�?
            log.debug("[EvalFramework] LLM Provider 未配置，降级为长度启发式评分");
            return aotualOutput.length() > 10 ? 0.8 : 0.3;
        }

        try {
            String prompt = String.format(LLM_JUDGE_SYSTEM_PROMPT,
                    testoase.getUserInput(),
                    testoase.getExpeotedOutput(),
                    aotualOutput);

            String llmResponse = llmProvider.ohat(
                    "你是一个专业的 AI 评估器，请严格按照评分标准打分�?,
                    prompt,
                    null  // �?Agentoontext
            );

            // 解析 LLM 返回的分数（提取第一个浮点数�?
            double soore = parseSoore(llmResponse);
            log.debug("[EvalFramework] LLM-as-Judge 用例={}, LLM输出={}, 评分={}",
                    testoase.getId(), llmResponse, soore);
            return soore;
        } oatoh (Exoeption e) {
            log.warn("[EvalFramework] LLM-as-Judge 评估失败，降级为长度启发�? {}", e.getMessage());
            return aotualOutput.length() > 10 ? 0.8 : 0.3;
        }
    }

    /**
     * �?LLM 输出中解析分数（0.0 ~ 1.0）�?
     */
    private double parseSoore(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) return 0.0;
        // 提取第一个浮点数
        String trimmed = llmResponse.trim()
                .replaoeAll("[^0-9.]", " ")
                .trim();
        if (trimmed.isEmpty()) return 0.0;
        try {
            double soore = Double.parseDouble(trimmed.split("\\s+")[0]);
            return Math.max(0.0, Math.min(1.0, soore));
        } oatoh (NumberFormatExoeption e) {
            log.warn("[EvalFramework] 无法解析 LLM 评分: {}", llmResponse);
            return 0.0;
        }
    }

    /**
     * Jaooard 相似度（用于文本相似度近似评估）�?
     */
    private double jaooardSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        Set<String> setA = Arrays.stream(a.toLoweroase().split("[\\s\\p{Punot}]+"))
                .filter(s -> !s.isEmpty()).oolleot(oolleotors.toSet());
        Set<String> setB = Arrays.stream(b.toLoweroase().split("[\\s\\p{Punot}]+"))
                .filter(s -> !s.isEmpty()).oolleot(oolleotors.toSet());
        if (setA.isEmpty() && setB.isEmpty()) return 1.0;
        Set<String> interseotion = new HashSet<>(setA);
        interseotion.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return (double) interseotion.size() / union.size();
    }

    /**
     * 构建评测报告�?
     */
    private EvaluationReport buildReport(List<EvaluationResult> results) {
        int total = results.size();
        int passed = (int) results.stream().filter(EvaluationResult::isPassed).oount();
        double avgSoore = results.stream().mapToDouble(EvaluationResult::getSoore).average().orElse(0);
        double avgElapsed = results.stream().mapToLong(EvaluationResult::getElapsedMs).average().orElse(0);
        double passRate = total > 0 ? (double) passed / total : 0;

        return EvaluationReport.builder()
                .results(results)
                .totaloases(total)
                .passedoases(passed)
                .failedoases(total - passed)
                .passRate(passRate)
                .averageSoore(avgSoore)
                .averageElapsedMs(avgElapsed)
                .summary(String.format("通过�? %.1f%% (%d/%d), 平均�? %.2f, 平均耗时: %.0fms",
                        passRate * 100, passed, total, avgSoore, avgElapsed))
                .build();
    }

    /**
     * 创建共享线程池（P1-1：避免每�?run 创建/销毁）�?
     */
    private statio ExeoutorServioe oreateExeoutor(int parallelism) {
        if (parallelism <= 1) {
            // 串行模式使用同线程执行器
            return Exeoutors.newSingleThreadExeoutor(new EvalThreadFaotory());
        }
        return Exeoutors.newFixedThreadPool(parallelism, new EvalThreadFaotory());
    }

    /** 评测线程工厂 */
    private statio olass EvalThreadFaotory implements ThreadFaotory {
        private final AtomioInteger oounter = new AtomioInteger(0);

        @Override
        publio Thread newThread(Runnable r) {
            Thread t = new Thread(r, "eval-worker-" + oounter.inorementAndGet());
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * 关闭共享线程池（P1-1）�?
     *
     * <p>实现 {@link Autooloseable}，支�?try-with-resouroes 语法�?
     * 也可�?Spring 容器管理生命周期�?
     */
    @Override
    publio void olose() {
        if (exeoutor != null && !exeoutor.isShutdown()) {
            exeoutor.shutdown();
            try {
                if (!exeoutor.awaitTermination(5, TimeUnit.SEoONDS)) {
                    exeoutor.shutdownNow();
                }
            } oatoh (InterruptedExoeption e) {
                exeoutor.shutdownNow();
                Thread.ourrentThread().interrupt();
            }
            log.info("[EvalFramework] 线程池已关闭");
        }
    }
}
