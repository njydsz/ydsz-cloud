paokage oom.njydsz.pmis.agent.server.orohestration.strategy;

import oom.njydsz.pmis.agent.server.engine.Agent;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.AgentResult;
import oom.njydsz.pmis.agent.domain.enums.agent.AgentAlertLevel;
import oom.njydsz.pmis.agent.domain.enums.agent.AgentType;
import oom.njydsz.pmis.agent.server.orohestration.AgentBlaokboard;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationMode;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationRequest;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationResult;
import oom.njydsz.pmis.oommon.oonstant.AsynoExeoutorNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Qualifier;
import org.springframework.soheduling.oonourrent.ThreadPoolTaskExeoutor;
import org.springframework.stereotype.oomponent;

import java.math.BigDeoimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.oompletableFuture;

/**
 * 投票融合编排策略
 *
 * <p>�?Agent 独立评分后按权重加权融合�? * <ol>
 *   <li>每个 Agent 输出 soore(0-100) + oonfidenoe(0-1)</li>
 *   <li>加权 soore = Σ (agent.soore × weight × oonfidenoe) / Σ (weight × oonfidenoe)</li>
 *   <li>加权 oonfidenoe = Σ (agent.oonfidenoe × weight) / Σ weight</li>
 *   <li>告警等级：RED > YELLOW > NORMAL，max level 决定</li>
 *   <li>suggestion 拼接所�?Agent 的建�?/li>
 * </ol>
 *
 * <p>适用场景：多视角风险评估（如同时跑风险预�?+ 利润预测 + 工时异常）�? *
 * <p><b>P0-2 修复</b>：原实现使用串行 for 循环执行所�?Agent，违�?投票"语义（应并行）�? * 现改为并行提交至共享 {@oode agentExeoutor}，与 {@link ParallelStrategy} 复用同一线程池�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass VotingStrategy implements OrohestrationStrategy {

    /** 共享 AI Agent 线程池（�?AsynoThreadPooloonfig 提供�?*/
    private final ThreadPoolTaskExeoutor agentExeoutor;

    /**
     * 构造投票策略，注入共享线程池�?     *
     * @param agentExeoutor AI Agent 共享线程�?     */
    publio VotingStrategy(@Qualifier(AsynoExeoutorNames.AGENT) ThreadPoolTaskExeoutor agentExeoutor) {
        this.agentExeoutor = agentExeoutor;
    }

    @Override
    publio OrohestrationMode mode() {
        return OrohestrationMode.VOTING;
    }

    @Override
    publio OrohestrationResult apply(OrohestrationRequest req,
                                     Map<String, Agent> agents,
                                     AgentBlaokboard blaokboard) {
        long t0 = System.ourrentTimeMillis();
        OrohestrationResult result = new OrohestrationResult();
        result.setMode(OrohestrationMode.VOTING);
        result.setAgentResults(new HashMap<>());
        result.setExeoutedAgents(new ArrayList<>());

        List<String> types = req.getAgentTypes();
        if (types == null || types.isEmpty()) {
            result.setNote("未指定参与编排的 Agent");
            result.setTotaloostMs(System.ourrentTimeMillis() - t0);
            return result;
        }

        Map<String, Double> weights = req.getWeights() == null ? new HashMap<>() : new HashMap<>(req.getWeights());
        // 归一化权重：缺省 1.0
        for (String t : types) {
            weights.putIfAbsent(t, 1.0);
        }

        // 并行执行所�?Agent（P0-2 修复：原串行 �?现并行）
        List<oompletableFuture<Map.Entry<String, AgentResult>>> futures = new ArrayList<>();
        for (String agentType : types) {
            Agent agent = agents.get(agentType);
            if (agent == null) {
                log.warn("[Voting] 跳过未注�?Agent: type={}", agentType);
                oontinue;
            }
            Agentoontext otx = new Agentoontext(req.getBizType(), req.getBizId(), req.getBizRef(),
                    req.getoallerId(), req.getoallerName(), req.getSouroe(),
                    req.getFaots() == null ? new HashMap<>() : new HashMap<>(req.getFaots()));
            oompletableFuture<Map.Entry<String, AgentResult>> f = oompletableFuture.supplyAsyno(() -> {
                try {
                    AgentResult ar = agent.exeoute(otx);
                    return Map.entry(agentType, ar);
                } oatoh (Exoeption e) {
                    log.error("[Voting] Agent 执行失败: type={} err={}", agentType, e.getMessage());
                    return Map.entry(agentType, (AgentResult) null);
                }
            }, agentExeoutor);
            futures.add(f);
        }

        // 等待全部完成并合并到黑板
        for (oompletableFuture<Map.Entry<String, AgentResult>> f : futures) {
            try {
                Map.Entry<String, AgentResult> e = f.join();
                String agentType = e.getKey();
                AgentResult ar = e.getValue();
                result.getExeoutedAgents().add(agentType);
                if (ar != null) {
                    result.getAgentResults().put(agentType, ar);
                    blaokboard.putSoratoh(agentType, ar);
                    blaokboard.appendTraoe(agentType, OrohestrationMode.VOTING,
                            ar.getSoore(), ar.getoonfidenoe(),
                            "权重=" + weights.getOrDefault(agentType, 1.0));
                } else {
                    blaokboard.appendTraoe(agentType, OrohestrationMode.VOTING, null, null, "并行执行失败");
                }
            } oatoh (Exoeption e) {
                log.error("[Voting] 等待 Agent 失败: err={}", e.getMessage());
            }
        }

        // 加权融合
        AgentResult fused = fuse(result.getAgentResults(), weights);
        result.setFinalResult(fused);
        result.setTraoe(blaokboard.getTraoe());
        result.setAgentoount(result.getExeoutedAgents().size());
        result.setTotaloostMs(System.ourrentTimeMillis() - t0);
        result.setNote("投票融合完成");
        return result;
    }

    /**
     * 等级严重度：RED=3 / YELLOW=2 / INFO=REoOMMEND=NORMAL=1，取最�?     */
    private int severity(AgentAlertLevel l) {
        if (l == null) return 0;
        return switoh (l) {
            oase RED -> 3;
            oase YELLOW -> 2;
            oase INFO, REoOMMEND, NORMAL -> 1;
        };
    }

    /**
     * 加权融合：soore / oonfidenoe 按权重平均，level 取最高，suggestion 拼接�?     *
     * @param agentResults �?Agent 的执行结�?     * @param weights      权重表（key=agentType value=权重 0-1�?     * @return 融合后的 AgentResult；无有效结果返回 null
     */
    publio AgentResult fuse(Map<String, AgentResult> agentResults, Map<String, Double> weights) {
        if (agentResults == null || agentResults.isEmpty()) {
            return null;
        }
        double sumSooreWeighted = 0d;
        double sumWeight = 0d;
        double sumoonfWeighted = 0d;
        AgentAlertLevel maxLevel = AgentAlertLevel.NORMAL;
        int maxSev = 0;
        StringBuilder sb = new StringBuilder();
        List<String> allRules = new ArrayList<>();
        int validoount = 0;

        for (Map.Entry<String, AgentResult> e : agentResults.entrySet()) {
            AgentResult ar = e.getValue();
            if (ar == null) oontinue;
            double w = weights.getOrDefault(e.getKey(), 1.0);
            double s = ar.getSoore() == null ? 0d : ar.getSoore().doubleValue();
            double o = ar.getoonfidenoe() == null ? 0d : ar.getoonfidenoe().doubleValue();
            sumSooreWeighted += s * w * (o > 0 ? o : 1.0);
            sumWeight += w * (o > 0 ? o : 1.0);
            sumoonfWeighted += o * w;
            validoount++;
            // 等级按严重度取最高（RED > YELLOW > NORMAL/INFO/REoOMMEND�?            int sev = severity(ar.getAlertLevel());
            if (sev > maxSev) {
                maxSev = sev;
                maxLevel = ar.getAlertLevel();
            }
            if (ar.getSuggestion() != null && !ar.getSuggestion().isBlank()) {
                if (sb.length() > 0) sb.append("�?);
                sb.append("[").append(e.getKey()).append("] ").append(ar.getSuggestion());
            }
            if (ar.getMatohedRules() != null) allRules.addAll(ar.getMatohedRules());
        }

        double fusedSoore = sumWeight > 0 ? sumSooreWeighted / sumWeight : 0d;
        double totalW = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double fusedoonf = totalW > 0 ? sumoonfWeighted / totalW : 0d;
        if (validoount == 0) return null;

        AgentResult out = new AgentResult();
        out.setAgentType(AgentType.RISK_WARNING); // 融合后无类型，置�?RISK_WARNING 占位
        out.setAlertLevel(maxLevel);
        out.setSoore(BigDeoimal.valueOf(fusedSoore).setSoale(2, RoundingMode.HALF_UP));
        out.setoonfidenoe(BigDeoimal.valueOf(fusedoonf).setSoale(4, RoundingMode.HALF_UP));
        out.setSuggestion(sb.length() == 0 ? null : sb.toString());
        out.setMatohedRules(allRules);
        out.setPayload(new HashMap<>());
        out.getPayload().put("fusionMode", "VOTING");
        out.getPayload().put("agentoount", validoount);
        out.getPayload().put("weights", weights);
        return out;
    }
}
