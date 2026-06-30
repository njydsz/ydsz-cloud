package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.entity.OpsTicketDO;
import com.njydsz.pmis.execution.enums.OpsTicketPriority;
import com.njydsz.pmis.execution.enums.OpsTicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SlaCalculator SLA 计算器测试")
class SlaCalculatorTest {

    @Test
    @DisplayName("P1 截止时间偏移 15 分钟响应 / 4 小时解决")
    void p1_deadline() {
        LocalDateTime t0 = LocalDateTime.of(2026, 7, 1, 9, 0);
        SlaCalculator.SlaDeadline d = SlaCalculator.calc(OpsTicketPriority.P1, t0);
        assertThat(d.responseDueAt()).isEqualTo(t0.plusMinutes(15));
        assertThat(d.resolveDueAt()).isEqualTo(t0.plusMinutes(4 * 60));
    }

    @Test
    @DisplayName("P4 截止时间偏移 8 小时响应 / 7 天解决")
    void p4_deadline() {
        LocalDateTime t0 = LocalDateTime.of(2026, 7, 1, 9, 0);
        SlaCalculator.SlaDeadline d = SlaCalculator.calc(OpsTicketPriority.P4, t0);
        assertThat(d.responseDueAt()).isEqualTo(t0.plusHours(8));
        assertThat(d.resolveDueAt()).isEqualTo(t0.plusDays(7));
    }

    @Test
    @DisplayName("null 输入返回 null 截止时间")
    void nullArgs() {
        SlaCalculator.SlaDeadline d = SlaCalculator.calc(null, null);
        assertThat(d.responseDueAt()).isNull();
        assertThat(d.resolveDueAt()).isNull();
    }

    @Test
    @DisplayName("isResponseBreached 当前时间 > responseDueAt")
    void responseBreach() {
        OpsTicketDO t = new OpsTicketDO();
        t.setResponseDueAt(LocalDateTime.of(2026, 7, 1, 9, 15));
        assertThat(SlaCalculator.isResponseBreached(t, LocalDateTime.of(2026, 7, 1, 9, 16)))
                .isTrue();
        assertThat(SlaCalculator.isResponseBreached(t, LocalDateTime.of(2026, 7, 1, 9, 14)))
                .isFalse();
    }

    @Test
    @DisplayName("isResolveBreached 当前时间 > resolveDueAt")
    void resolveBreach() {
        OpsTicketDO t = new OpsTicketDO();
        t.setResolveDueAt(LocalDateTime.of(2026, 7, 2, 9, 0));
        assertThat(SlaCalculator.isResolveBreached(t, LocalDateTime.of(2026, 7, 2, 9, 0, 1)))
                .isTrue();
    }

    @Test
    @DisplayName("responseRemainMinutes 剩余时间正/负")
    void responseRemain() {
        OpsTicketDO t = new OpsTicketDO();
        t.setResponseDueAt(LocalDateTime.of(2026, 7, 1, 9, 15));
        long remain = SlaCalculator.responseRemainMinutes(t, LocalDateTime.of(2026, 7, 1, 9, 10));
        assertThat(remain).isEqualTo(5L);
        long overdue = SlaCalculator.responseRemainMinutes(t, LocalDateTime.of(2026, 7, 1, 9, 20));
        assertThat(overdue).isEqualTo(-5L);
    }

    @Test
    @DisplayName("isAssigned 状态在 ASSIGNED/IN_PROGRESS/RESOLVED/CLOSED")
    void isAssigned() {
        OpsTicketDO t = new OpsTicketDO();
        t.setStatus(OpsTicketStatus.OPEN.getCode());
        assertThat(SlaCalculator.isAssigned(t)).isFalse();
        t.setStatus(OpsTicketStatus.ASSIGNED.getCode());
        assertThat(SlaCalculator.isAssigned(t)).isTrue();
        t.setStatus(OpsTicketStatus.IN_PROGRESS.getCode());
        assertThat(SlaCalculator.isAssigned(t)).isTrue();
        t.setStatus(OpsTicketStatus.RESOLVED.getCode());
        assertThat(SlaCalculator.isAssigned(t)).isTrue();
        t.setStatus(OpsTicketStatus.CLOSED.getCode());
        assertThat(SlaCalculator.isAssigned(t)).isTrue();
    }

    @Test
    @DisplayName("canEvaluate 仅在 RESOLVED/CLOSED")
    void canEvaluate() {
        OpsTicketDO t = new OpsTicketDO();
        t.setStatus(OpsTicketStatus.OPEN.getCode());
        assertThat(SlaCalculator.canEvaluate(t)).isFalse();
        t.setStatus(OpsTicketStatus.RESOLVED.getCode());
        assertThat(SlaCalculator.canEvaluate(t)).isTrue();
        t.setStatus(OpsTicketStatus.CLOSED.getCode());
        assertThat(SlaCalculator.canEvaluate(t)).isTrue();
    }

    @Test
    @DisplayName("null 工单不抛异常")
    void nullTicket() {
        assertThat(SlaCalculator.isResponseBreached(null, LocalDateTime.now())).isFalse();
        assertThat(SlaCalculator.isResolveBreached(null, LocalDateTime.now())).isFalse();
        assertThat(SlaCalculator.responseRemainMinutes(null, LocalDateTime.now())).isZero();
    }

    @Test
    @DisplayName("SlaDeadline record 属性")
    void recordAccess() {
        LocalDateTime a = LocalDateTime.of(2026, 7, 1, 9, 0);
        LocalDateTime b = LocalDateTime.of(2026, 7, 1, 10, 0);
        SlaCalculator.SlaDeadline d = new SlaCalculator.SlaDeadline(a, b);
        assertThat(d.responseDueAt()).isEqualTo(a);
        assertThat(d.resolveDueAt()).isEqualTo(b);
        assertThat(ChronoUnit.HOURS.between(d.responseDueAt(), d.resolveDueAt())).isEqualTo(1);
    }
}
