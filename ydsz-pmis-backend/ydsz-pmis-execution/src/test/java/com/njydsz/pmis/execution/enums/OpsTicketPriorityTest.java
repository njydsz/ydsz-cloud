package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpsTicketPriority 工单优先级 + SLA 测试")
class OpsTicketPriorityTest {

    @Test
    @DisplayName("P1 紧急：15 分钟响应 / 4 小时解决")
    void p1() {
        OpsTicketPriority p = OpsTicketPriority.P1;
        assertThat(p.getResponseMinutes()).isEqualTo(15);
        assertThat(p.getResolveMinutes()).isEqualTo(4 * 60);
    }

    @Test
    @DisplayName("P2 高：1 小时响应 / 24 小时解决")
    void p2() {
        OpsTicketPriority p = OpsTicketPriority.P2;
        assertThat(p.getResponseMinutes()).isEqualTo(60);
        assertThat(p.getResolveMinutes()).isEqualTo(24 * 60);
    }

    @Test
    @DisplayName("P3 中：4 小时响应 / 72 小时解决")
    void p3() {
        OpsTicketPriority p = OpsTicketPriority.P3;
        assertThat(p.getResponseMinutes()).isEqualTo(4 * 60);
        assertThat(p.getResolveMinutes()).isEqualTo(72 * 60);
    }

    @Test
    @DisplayName("P4 低：8 小时响应 / 7 天解决")
    void p4() {
        OpsTicketPriority p = OpsTicketPriority.P4;
        assertThat(p.getResponseMinutes()).isEqualTo(8 * 60);
        assertThat(p.getResolveMinutes()).isEqualTo(7 * 24 * 60);
    }

    @Test
    @DisplayName("fromCode 忽略大小写")
    void fromCode() {
        assertThat(OpsTicketPriority.fromCode("p1")).isEqualTo(OpsTicketPriority.P1);
        assertThat(OpsTicketPriority.fromCode("P3")).isEqualTo(OpsTicketPriority.P3);
        assertThat(OpsTicketPriority.fromCode(null)).isNull();
        assertThat(OpsTicketPriority.fromCode("P5")).isNull();
    }
}
