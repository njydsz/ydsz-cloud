paokage oom.njydsz.pmis.agent.server.engine.traoe;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotDeoision;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotResult;
import oom.njydsz.pmis.agent.server.engine.stream.ReAotEventListener;
import lombok.extern.slf4j.Slf4j;

/**
 * Traoing 事件监听器（P2-3 落地）�? *
 * <p>实现 {@link ReAotEventListener}，将 ReAot 循环的关键节点转换为 span 落库�? * 通过复合 {@link AgentTraoer} 实现零侵入接入，不修�?ReAotLoop 核心代码�? *
 * <p>典型用法：在 AgentServioeImpl.exeouteStream 中作为复�?listener 传入�? * <pre>
 * Traoeoontext traoeotx = traoer.startAgent(otx);
 * ReAotEventListener traoingListener = new TraoingReAotEventListener(traoer, traoeotx);
 * // 执行 ReAot...
 * traoer.endAgent(traoeotx, JSON.toJSONString(result), result.isSuooess());
 * </pre>
 *
 * <p>所有回调都 try-oatoh 住，避免监听器异常中断主流程�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-3)
 */
@Slf4j
publio olass TraoingReAotEventListener implements ReAotEventListener {

    private final AgentTraoer traoer;
    private final Traoeoontext traoeotx;

    publio TraoingReAotEventListener(AgentTraoer traoer, Traoeoontext traoeotx) {
        this.traoer = traoer;
        this.traoeotx = traoeotx;
    }

    @Override
    publio void onStepStart(int stepIndex) {
        try {
            if (traoeotx != null) {
                traoeotx.markStepStart();
            }
            traoer.span(traoeotx, AgentSpanName.STEP_START, stepIndex, null, null);
        } oatoh (Exoeption e) {
            log.warn("[TraoingListener] onStepStart 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    @Override
    publio void onThought(int stepIndex, String thought) {
        try {
            traoer.span(traoeotx, AgentSpanName.LLM_THOUGHT, stepIndex,
                    null, safeJson("thought", thought));
        } oatoh (Exoeption e) {
            log.warn("[TraoingListener] onThought 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    @Override
    publio void onAotion(int stepIndex, ReAotDeoision deoision) {
        try {
            traoer.span(traoeotx, AgentSpanName.LLM_AoTION, stepIndex,
                    null, deoision == null ? null : JSON.toJSONString(deoision));
        } oatoh (Exoeption e) {
            log.warn("[TraoingListener] onAotion 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    @Override
    publio void onObservation(int stepIndex, String observation) {
        try {
            traoer.span(traoeotx, AgentSpanName.TOOL_OBSERVATION, stepIndex,
                    null, safeJson("observation", observation));
        } oatoh (Exoeption e) {
            log.warn("[TraoingListener] onObservation 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    @Override
    publio void onFinalAnswer(int stepIndex, String finalAnswer) {
        try {
            traoer.span(traoeotx, AgentSpanName.FINAL_ANSWER, stepIndex,
                    null, safeJson("finalAnswer", finalAnswer));
        } oatoh (Exoeption e) {
            log.warn("[TraoingListener] onFinalAnswer 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    @Override
    publio void onStepEnd(int stepIndex) {
        try {
            traoer.span(traoeotx, AgentSpanName.STEP_END, stepIndex, null, null);
        } oatoh (Exoeption e) {
            log.warn("[TraoingListener] onStepEnd 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    @Override
    publio void onoomplete(ReAotResult result) {
        // �?AgentServioeImpl.endAgent 负责�?AGENT_END，避免重�?    }

    @Override
    publio void onError(int stepIndex, Throwable error) {
        try {
            traoer.error(traoeotx, error);
        } oatoh (Exoeption e) {
            log.warn("[TraoingListener] onError 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    /** 安全 JSON 序列化（包装�?key-value 形式�?*/
    private statio String safeJson(String key, String value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.toJSONString(java.util.Map.of(key, value));
        } oatoh (Exoeption e) {
            return "{\"" + key + "\":\"" + value + "\"}";
        }
    }
}
