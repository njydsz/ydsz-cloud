package com.njydsz.pmis.agent.orchestration;

import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentMessage 消息体测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AgentMessage 编排消息体")
class AgentMessageTest {

    @Test
    @DisplayName("input 工厂方法")
    void inputFactory() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("k", "v");
        AgentMessage m = AgentMessage.input("COORDINATOR", payload);
        assertThat(m.getType()).isEqualTo("INPUT");
        assertThat(m.getFrom()).isEqualTo("COORDINATOR");
        assertThat(m.getTo()).isNull();
        assertThat(m.getPayload()).containsEntry("k", "v");
        assertThat(m.getTs()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("output 工厂方法")
    void outputFactory() {
        AgentResult r = new AgentResult();
        r.setAgentType(AgentType.RISK_WARNING);
        r.setAlertLevel(AgentAlertLevel.RED);
        r.setScore(new BigDecimal("80"));
        AgentMessage m = AgentMessage.output("RISK_WARNING", r);
        assertThat(m.getType()).isEqualTo("OUTPUT");
        assertThat(m.getFrom()).isEqualTo("RISK_WARNING");
        assertThat(m.getResult()).isNotNull();
        assertThat(m.getResult().getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
    }

    @Test
    @DisplayName("control 工厂方法")
    void controlFactory() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "STOP");
        AgentMessage m = AgentMessage.control("COORDINATOR", "AGENT_A", payload);
        assertThat(m.getType()).isEqualTo("CONTROL");
        assertThat(m.getFrom()).isEqualTo("COORDINATOR");
        assertThat(m.getTo()).isEqualTo("AGENT_A");
        assertThat(m.getPayload()).containsEntry("action", "STOP");
    }

    @Test
    @DisplayName("全字段构造 + Getter")
    void allArgsCtor() {
        AgentResult r = new AgentResult();
        Map<String, Object> p = new HashMap<>();
        p.put("x", 1);
        AgentMessage m = new AgentMessage("OUTPUT", "A", "B", r, p, 123L);
        assertThat(m.getType()).isEqualTo("OUTPUT");
        assertThat(m.getFrom()).isEqualTo("A");
        assertThat(m.getTo()).isEqualTo("B");
        assertThat(m.getResult()).isSameAs(r);
        assertThat(m.getPayload()).isSameAs(p);
        assertThat(m.getTs()).isEqualTo(123L);
    }
}
