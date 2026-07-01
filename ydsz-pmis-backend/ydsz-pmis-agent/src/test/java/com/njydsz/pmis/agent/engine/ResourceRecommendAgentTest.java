package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资源调度推荐 Agent 测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ResourceRecommendAgent 资源推荐")
class ResourceRecommendAgentTest {

    private final ResourceRecommendAgent agent = new ResourceRecommendAgent();

    @Test
    @DisplayName("类型-RESOURCE_RECOMMEND")
    void type() {
        assertThat(agent.type()).isEqualTo(AgentType.RESOURCE_RECOMMEND);
    }

    @Test
    @DisplayName("无候选 提示")
    void noCandidates() {
        AgentContext ctx = new AgentContext();
        ctx.setParams(new HashMap<>());
        AgentResult r = agent.execute(ctx);
        assertThat(r.getAgentType()).isEqualTo(AgentType.RESOURCE_RECOMMEND);
        assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.INFO);
        assertThat(r.getSuggestion()).contains("未提供");
    }

    @Test
    @DisplayName("空列表 提示")
    void emptyList() {
        Map<String, Object> p = new HashMap<>();
        p.put("candidates", new ArrayList<>());
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getSuggestion()).contains("无可推荐");
    }

    @Test
    @DisplayName("按综合分排序")
    void rankByScore() {
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(candidate("a", "L3", new BigDecimal("800"), "0.9", "0.9"));
        candidates.add(candidate("b", "L4", new BigDecimal("1500"), "0.7", "0.7"));
        candidates.add(candidate("c", "L3", new BigDecimal("1000"), "0.6", "0.6"));
        Map<String, Object> p = new HashMap<>();
        p.put("candidates", candidates);
        p.put("topN", 2);
        p.put("requiredLevel", "L3");
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) r.getPayload().get("top");
        assertThat(top).hasSize(2);
        assertThat(top.get(0).get("_score")).isNotNull();
    }

    @Test
    @DisplayName("topN 限制")
    void topNLimit() {
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            candidates.add(candidate("u" + i, "L3", new BigDecimal("800"),
                    "0." + (5 + i), "0.8"));
        }
        Map<String, Object> p = new HashMap<>();
        p.put("candidates", candidates);
        p.put("topN", 1);
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) r.getPayload().get("top");
        assertThat(top).hasSize(1);
    }

    @Test
    @DisplayName("级匹配")
    void levelMatch() {
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(candidate("a", "L3", new BigDecimal("800"), "0.9", "0.9"));
        candidates.add(candidate("b", "L4", new BigDecimal("800"), "0.9", "0.9"));
        Map<String, Object> p = new HashMap<>();
        p.put("candidates", candidates);
        p.put("requiredLevel", "L3");
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) r.getPayload().get("top");
        // L3 应当排在 L4 之前（同 skill/avail/cost 下）
        assertThat(top.get(0).get("name")).isEqualTo("a");
    }

    @Test
    @DisplayName("高分推荐 RECOMMEND")
    void recommendLevel() {
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(candidate("a", "L3", new BigDecimal("500"), "1.0", "1.0"));
        Map<String, Object> p = new HashMap<>();
        p.put("candidates", candidates);
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RECOMMEND);
    }

    private Map<String, Object> candidate(String name, String level, BigDecimal cost,
                                           String skill, String avail) {
        Map<String, Object> c = new HashMap<>();
        c.put("name", name);
        c.put("level", level);
        c.put("dailyCost", cost);
        c.put("skillMatch", new BigDecimal(skill));
        c.put("availability", new BigDecimal(avail));
        return c;
    }
}
