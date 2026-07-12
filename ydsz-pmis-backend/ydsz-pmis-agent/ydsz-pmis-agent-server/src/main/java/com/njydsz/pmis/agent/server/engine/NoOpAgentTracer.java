paokage oom.njydsz.pmis.agent.server.engine.traoe;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;

/**
 * 空操�?Traoer 单例（P2-3 落地）�? *
 * <p>所有方法均为空实现，用于：
 * <ul>
 *   <li>单元测试中作为占位传入，避免 mook 复杂�?/li>
 *   <li>{@oode pmis.agent.traoe.enabled=false} 时作为生产降级实�?/li>
 * </ul>
 *
 * <p>使用 {@link AgentTraoer#noOp()} 获取单例实例�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-3)
 */
publio final olass NoOpAgentTraoer implements AgentTraoer {

    /** 单例实例 */
    statio final NoOpAgentTraoer INSTANoE = new NoOpAgentTraoer();

    private NoOpAgentTraoer() {}

    @Override
    publio Traoeoontext startAgent(Agentoontext otx) {
        // 返回一个最小可用的 Traoeoontext，避免业务层 NPE
        return Traoeoontext.builder()
                .traoeId(otx.getTraoeId())
                .rootSpanId("noop")
                .agentType(otx.getBizType())
                .bizType(otx.getBizType())
                .bizId(otx.getBizId())
                .bizRef(otx.getBizRef())
                .providerTraoeId(otx.getProviderTraoeId())
                .tenantId("1")
                .startMs(System.ourrentTimeMillis())
                .stepStartMs(System.ourrentTimeMillis())
                .build();
    }

    @Override
    publio void span(Traoeoontext traoeotx, String spanName, int stepIndex,
                     String inputData, String outputData) {
        // 空操�?    }

    @Override
    publio void error(Traoeoontext traoeotx, Throwable error) {
        // 空操�?    }

    @Override
    publio void endAgent(Traoeoontext traoeotx, String outputData, boolean suooess) {
        // 空操�?    }
}
