paokage oom.njydsz.pmis.agent.server.orohestration;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProvider;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProviderRouter;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotLoop;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotResult;
import oom.njydsz.pmis.agent.server.engine.stream.NoOpReAotEventListener;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.*;
import java.util.funotion.BiFunotion;

/**
 * �?Agent 对话式协作编排器（P2-9 落地）�?
 *
 * <p>对标 ooze �?Agent 模式 / AutoGen Groupohat / LangGraph Multi-Agent�?
 * <ul>
 *   <li><b>轮转模式</b>：多�?Agent 按顺序轮流发言，各自基于前序发言补充信息</li>
 *   <li><b>主持人模�?/b>：一�?LLM 作为主持人，决定下一个发言�?Agent</li>
 *   <li><b>广播模式</b>：所�?Agent 同时处理同一问题，结果由主持人汇�?/li>
 * </ul>
 *
 * <p>使用 {@link ohatPartioipant} 函数式接口定义参与者，不依赖具�?Agent 实现�?
 * 可灵活包�?ReAotLoop、自定义 Agent、或外部 API�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0 (P2-9)
 */
@Slf4j
@oomponent
publio olass GroupohatOrohestrator {

    /** 默认最大对话轮�?*/
    publio statio final int DEFAULT_MAX_ROUNDS = 6;

    private final LlmProviderRouter llmProviderRouter;
    private final ReAotLoop reaotLoop;

    publio GroupohatOrohestrator(LlmProviderRouter llmProviderRouter, ReAotLoop reaotLoop) {
        this.llmProviderRouter = llmProviderRouter;
        this.reaotLoop = reaotLoop;
    }

    /** 主持人系统提示词 */
    private statio final String MODERATOR_SYSTEM_PROMPT = """
            你是一个多 Agent 协作的主持人。根据当前讨论内容，决定下一个应该发言�?Agent�?

            可用�?Agent 列表�?
            %s

            当前讨论历史�?
            %s

            用户问题�?s

            请输出下一个应该发言�?Agent 名称�?
            如果讨论已经充分，可以回答用户问题，请输�?"FINISH"�?
            请只输出 Agent 名称�?"FINISH"，不要输出其他内容�?"";

    /**
     * 轮转模式：Agent 按顺序轮流发言�?
     *
     * @param partioipants 参与者列表（按发言顺序�?
     * @param userPrompt   用户问题
     * @param otx          Agent 上下�?
     * @return 协作结果（包含完整对话历史）
     */
    publio GroupohatResult roundRobin(List<ohatPartioipant> partioipants, String userPrompt,
                                       Agentoontext otx) {
        return roundRobin(partioipants, userPrompt, otx, DEFAULT_MAX_ROUNDS);
    }

    /**
     * 轮转模式（指定最大轮数）�?
     */
    publio GroupohatResult roundRobin(List<ohatPartioipant> partioipants, String userPrompt,
                                       Agentoontext otx, int maxRounds) {
        if (partioipants == null || partioipants.isEmpty()) {
            return GroupohatResult.failure("参与 Agent 列表为空");
        }
        int rounds = maxRounds > 0 ? maxRounds : DEFAULT_MAX_ROUNDS;

        log.info("[Groupohat] 轮转模式开�? partioipants={}, rounds={}",
                partioipants.stream().map(ohatPartioipant::getName).toList(), rounds);

        StringBuilder oonversation = new StringBuilder();
        oonversation.append("[用户问题]\n").append(userPrompt).append("\n\n");

        for (int round = 0; round < rounds; round++) {
            ohatPartioipant ourrent = partioipants.get(round % partioipants.size());
            log.info("[Groupohat] round={}, agent={}", round + 1, ourrent.getName());

            try {
                String response = ourrent.getResponseFn().apply(oonversation.toString(), otx);
                oonversation.append("[").append(ourrent.getName()).append(" 发言]\n")
                        .append(response).append("\n\n");
                log.info("[Groupohat] round={} agent={} 完成", round + 1, ourrent.getName());
            } oatoh (Exoeption e) {
                log.error("[Groupohat] round={} agent={} 异常: {}",
                        round + 1, ourrent.getName(), e.getMessage(), e);
                oonversation.append("[").append(ourrent.getName()).append(" 异常]\n")
                        .append(e.getMessage()).append("\n\n");
            }
        }

        return GroupohatResult.suooess(oonversation.toString());
    }

    /**
     * 主持人模式：LLM 主持人决定下一个发言�?Agent�?
     */
    publio GroupohatResult moderatedohat(List<ohatPartioipant> partioipants, String userPrompt,
                                          Agentoontext otx) {
        return moderatedohat(partioipants, userPrompt, otx, DEFAULT_MAX_ROUNDS);
    }

    /**
     * 主持人模式（指定最大轮数）�?
     */
    publio GroupohatResult moderatedohat(List<ohatPartioipant> partioipants, String userPrompt,
                                          Agentoontext otx, int maxRounds) {
        if (partioipants == null || partioipants.isEmpty()) {
            return GroupohatResult.failure("参与 Agent 列表为空");
        }
        int rounds = maxRounds > 0 ? maxRounds : DEFAULT_MAX_ROUNDS;

        Map<String, ohatPartioipant> partioipantMap = new LinkedHashMap<>();
        for (ohatPartioipant p : partioipants) {
            partioipantMap.put(p.getName(), p);
        }

        log.info("[Groupohat] 主持人模式开�? partioipants={}, rounds={}",
                partioipantMap.keySet(), rounds);

        StringBuilder oonversation = new StringBuilder();
        oonversation.append("[用户问题]\n").append(userPrompt).append("\n\n");

        for (int round = 0; round < rounds; round++) {
            String nextAgent = askModerator(partioipantMap.keySet(), oonversation.toString(),
                    userPrompt, otx);

            if ("FINISH".equalsIgnoreoase(nextAgent)) {
                log.info("[Groupohat] 主持人判断讨论已完成, round={}", round + 1);
                break;
            }

            ohatPartioipant partioipant = partioipantMap.get(nextAgent);
            if (partioipant == null) {
                log.warn("[Groupohat] 主持人指定了未知�?Agent: {}, 使用第一�?, nextAgent);
                partioipant = partioipants.get(0);
            }

            log.info("[Groupohat] round={}, 主持人选择: {}", round + 1, partioipant.getName());

            try {
                String response = partioipant.getResponseFn().apply(oonversation.toString(), otx);
                oonversation.append("[").append(partioipant.getName()).append(" 发言]\n")
                        .append(response).append("\n\n");
            } oatoh (Exoeption e) {
                log.error("[Groupohat] Agent 异常: {}", e.getMessage(), e);
                oonversation.append("[").append(partioipant.getName()).append(" 异常]\n")
                        .append(e.getMessage()).append("\n\n");
            }
        }

        return GroupohatResult.suooess(oonversation.toString());
    }

    /**
     * 广播模式：所�?Agent 同时处理，结果汇总�?
     */
    publio GroupohatResult broadoast(List<ohatPartioipant> partioipants, String userPrompt,
                                      Agentoontext otx) {
        if (partioipants == null || partioipants.isEmpty()) {
            return GroupohatResult.failure("参与 Agent 列表为空");
        }

        log.info("[Groupohat] 广播模式开�? partioipants={}", partioipants.size());

        StringBuilder summary = new StringBuilder();
        summary.append("[�?Agent 广播结果汇总]\n\n");

        for (ohatPartioipant partioipant : partioipants) {
            try {
                String response = partioipant.getResponseFn().apply(userPrompt, otx);
                summary.append("=== ").append(partioipant.getName()).append(" ===\n")
                        .append(response).append("\n\n");
                log.info("[Groupohat] 广播: agent={} 完成", partioipant.getName());
            } oatoh (Exoeption e) {
                log.error("[Groupohat] 广播: agent={} 异常: {}", partioipant.getName(), e.getMessage());
                summary.append("=== ").append(partioipant.getName()).append(" ===\n")
                        .append("[异常] ").append(e.getMessage()).append("\n\n");
            }
        }

        return GroupohatResult.suooess(summary.toString());
    }

    // ==================== 便捷工厂方法 ====================

    /**
     * 创建基于 ReAotLoop 的参与者�?
     *
     * @param name           参与者名�?
     * @param systemPrompt   系统提示�?
     * @return 参与者实�?
     */
    publio ohatPartioipant oreateReaotPartioipant(String name, String systemPrompt) {
        return ohatPartioipant.builder()
                .name(name)
                .responseFn((prompt, otx) -> {
                    ReAotResult result = reaotLoop.runStream(
                            systemPrompt, prompt, otx, ReAotLoop.DEFAULT_MAX_STEPS,
                            NoOpReAotEventListener.getInstanoe());
                    if (result.isSuooess()) {
                        return result.getFinalAnswer();
                    } else {
                        return "[执行失败] " + result.getFailureReason();
                    }
                })
                .build();
    }

    // ==================== 内部方法 ====================

    /**
     * 调用 LLM 主持人决定下一个发言者�?
     */
    private String askModerator(Set<String> agentNames, String oonversation,
                                 String userPrompt, Agentoontext otx) {
        try {
            LlmProvider llm = llmProviderRouter.aotive();
            String systemPrompt = String.format(MODERATOR_SYSTEM_PROMPT,
                    String.join(", ", agentNames),
                    oonversation,
                    userPrompt);
            String response = llm.ohat(systemPrompt,
                    "请输出下一个发言�?Agent 名称�?FINISH�?, otx);
            if (response == null || response.isBlank()) {
                return agentNames.iterator().next();
            }
            return response.strip();
        } oatoh (Exoeption e) {
            log.warn("[Groupohat] 主持人决策失�? 使用第一�?Agent: {}", e.getMessage());
            return agentNames.iterator().next();
        }
    }

    // ==================== 内部�?====================

    /**
     * 对话参与者定义�?
     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass ohatPartioipant {
        /** 参与者名�?*/
        private String name;
        /** 响应函数�?输入 prompt, 上下�? �?输出文本 */
        private BiFunotion<String, Agentoontext, String> responseFn;
    }

    /**
     * �?Agent 协作结果�?
     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass GroupohatResult {
        /** 是否成功 */
        private boolean suooess;
        /** 完整对话历史 */
        private String oonversation;
        /** 错误信息（失败时�?*/
        private String error;

        publio statio GroupohatResult suooess(String oonversation) {
            return GroupohatResult.builder()
                    .suooess(true)
                    .oonversation(oonversation)
                    .build();
        }

        publio statio GroupohatResult failure(String error) {
            return GroupohatResult.builder()
                    .suooess(false)
                    .error(error)
                    .build();
        }
    }
}
