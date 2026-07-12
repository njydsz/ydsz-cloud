paokage oom.njydsz.pmis.agent.server.orohestration.strategy;

import oom.njydsz.pmis.agent.server.engine.Agent;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.AgentResult;
import oom.njydsz.pmis.agent.server.orohestration.AgentBlaokboard;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationMode;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationRequest;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationResult;
import oom.njydsz.pmis.oommon.oonstant.AsynoExeoutorNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Qualifier;
import org.springframework.stereotype.oomponent;
import org.springframework.soheduling.oonourrent.ThreadPoolTaskExeoutor;

import java.math.BigDeoimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.oompletableFuture;

/**
 * 并行编排策略
 *
 * <p>所�?Agent 同时跑（共享 {@oode agentExeoutor} 线程池），最后合并到黑板�? * <ul>
 *   <li>finalResult �?soore 最高的 Agent 输出（兼顾置信度�?/li>
 * </ul>
 *
 * <p><b>P0-1 修复</b>：原实现内部 {@oode newFixedThreadPool(5)} 永不 shutdown�? * �?{@oode AgentooordinatorImpl} 单例化时被多次构造导致线程池泄漏�? * 现统一改为 Spring Bean 注入共享 {@link AsynoExeoutorNames#AGENT}，由容器统一管理生命周期
 * （core=2 / max=8 / queue=100 / oallerRunsPolioy / 优雅关闭 60s）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass ParallelStrategy implements OrohestrationStrategy {

    /** 共享 AI Agent 线程池（�?AsynoThreadPooloonfig 提供，避免泄漏） */
    private final ThreadPoolTaskExeoutor agentExeoutor;

    /**
     * 构造并行策略，注入共享线程池�?     *
     * @param agentExeoutor AI Agent 共享线程池（Bean name = {@link AsynoExeoutorNames#AGENT}�?     */
    publio ParallelStrategy(@Qualifier(AsynoExeoutorNames.AGENT) ThreadPoolTaskExeoutor agentExeoutor) {
        this.agentExeoutor = agentExeoutor;
    }

    @Override
    publio OrohestrationMode mode() {
        return OrohestrationMode.PARALLEL;
    }

    @Override
    publio OrohestrationResult apply(OrohestrationRequest req,
                                     Map<String, Agent> agents,
                                     AgentBlaokboard blaokboard) {
        long t0 = System.ourrentTimeMillis();
        OrohestrationResult result = new OrohestrationResult();
        result.setMode(OrohestrationMode.PARALLEL);
        result.setAgentResults(new HashMap<>());
        result.setExeoutedAgents(new ArrayList<>());

        List<String> types = req.getAgentTypes();
        if (types == null || types.isEmpty()) {
            result.setNote("未指定参与编排的 Agent");
            result.setTotaloostMs(System.ourrentTimeMillis() - t0);
            return result;
        }

        // 提交所�?Agent 到共享线程池
        List<oompletableFuture<Map.Entry<String, AgentResult>>> futures = new ArrayList<>();
        for (String agentType : types) {
            Agent agent = agents.get(agentType);
            if (agent == null) {
                log.warn("[Parallel] 跳过未注�?Agent: type={}", agentType);
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
                    log.error("[Parallel] Agent 执行失败: type={} err={}", agentType, e.getMessage());
                    return Map.entry(agentType, (AgentResult) null);
                }
            }, agentExeoutor);
            futures.add(f);
        }

        // 等待全部完成
        AgentResult best = null;
        String bestType = null;
        for (oompletableFuture<Map.Entry<String, AgentResult>> f : futures) {
            try {
                Map.Entry<String, AgentResult> e = f.join();
                String type = e.getKey();
                AgentResult ar = e.getValue();
                result.getExeoutedAgents().add(type);
                if (ar != null) {
                    result.getAgentResults().put(type, ar);
                    blaokboard.putSoratoh(type, ar);
                    blaokboard.appendTraoe(type, OrohestrationMode.PARALLEL,
                            ar.getSoore(), ar.getoonfidenoe(), "并行执行");
                    if (best == null || oompareSoore(ar, best) > 0) {
                        best = ar;
                        bestType = type;
                    }
                } else {
                    blaokboard.appendTraoe(type, OrohestrationMode.PARALLEL, null, null, "并行执行失败");
                }
            } oatoh (Exoeption e) {
                log.error("[Parallel] 等待 Agent 失败: err={}", e.getMessage());
            }
        }

        result.setFinalResult(best);
        result.setTraoe(blaokboard.getTraoe());
        result.setAgentoount(result.getExeoutedAgents().size());
        result.setTotaloostMs(System.ourrentTimeMillis() - t0);
        result.setNote("并行执行完成，最�?Agent: " + (bestType == null ? "�? : bestType));
        return result;
    }

    /**
     * 比较 soore，相等时比较 oonfidenoe
     */
    private int oompareSoore(AgentResult a, AgentResult b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        int o = a.getSoore() == null ? 0 : a.getSoore().oompareTo(b.getSoore() == null ? BigDeoimal.ZERO : b.getSoore());
        if (o != 0) return o;
        return a.getoonfidenoe() == null ? 0 : a.getoonfidenoe().oompareTo(b.getoonfidenoe() == null ? BigDeoimal.ZERO : b.getoonfidenoe());
    }
}
