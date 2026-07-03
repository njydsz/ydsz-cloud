package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.OpsTicketDO;
import com.njydsz.pmis.project.enums.OpsTicketPriority;
import com.njydsz.pmis.project.enums.OpsTicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SLA 计算器测试")
class SlaCalculatorTest {

    @Test
    @DisplayName("P1 优先级 - 响应 15 分钟，解决 4 小时")
    void shouldCalculateP1Deadline() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0);
        SlaCalculator.SlaDeadline deadline = SlaCalculator.calc(OpsTicketPriority.P1, createdAt);

        assertEquals(createdAt.plusMinutes(15), deadline.responseDueAt());
        assertEquals(createdAt.plusMinutes(4 * 60), deadline.resolveDueAt());
    }

    @Test
    @DisplayName("P2 优先级 - 响应 1 小时，解决 24 小时")
    void shouldCalculateP2Deadline() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0);
        SlaCalculator.SlaDeadline deadline = SlaCalculator.calc(OpsTicketPriority.P2, createdAt);

        assertEquals(createdAt.plusMinutes(60), deadline.responseDueAt());
        assertEquals(createdAt.plusMinutes(24 * 60), deadline.resolveDueAt());
    }

    @Test
    @DisplayName("P3 优先级 - 响应 4 小时，解决 72 小时")
    void shouldCalculateP3Deadline() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0);
        SlaCalculator.SlaDeadline deadline = SlaCalculator.calc(OpsTicketPriority.P3, createdAt);

        assertEquals(createdAt.plusMinutes(4 * 60), deadline.responseDueAt());
        assertEquals(createdAt.plusMinutes(72 * 60), deadline.resolveDueAt());
    }

    @Test
    @DisplayName("P4 优先级 - 响应 8 小时，解决 7 天")
    void shouldCalculateP4Deadline() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0);
        SlaCalculator.SlaDeadline deadline = SlaCalculator.calc(OpsTicketPriority.P4, createdAt);

        assertEquals(createdAt.plusMinutes(8 * 60), deadline.responseDueAt());
        assertEquals(createdAt.plusMinutes(7 * 24 * 60), deadline.resolveDueAt());
    }

    @Test
    @DisplayName("priority 或 createdAt 为 null 返回 null 截止时间")
    void shouldReturnNullDeadlinesWhenParamsAreNull() {
        SlaCalculator.SlaDeadline deadline = SlaCalculator.calc(null, LocalDateTime.now());
        assertNull(deadline.responseDueAt());
        assertNull(deadline.resolveDueAt());
    }

    @Test
    @DisplayName("响应 SLA 超时判断")
    void shouldDetectResponseBreach() {
        OpsTicketDO ticket = new OpsTicketDO();
        ticket.setResponseDueAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 10, 0);

        assertTrue(SlaCalculator.isResponseBreached(ticket, now));
    }

    @Test
    @DisplayName("响应 SLA 未超时判断")
    void shouldDetectResponseNotBreached() {
        OpsTicketDO ticket = new OpsTicketDO();
        ticket.setResponseDueAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 9, 0);

        assertFalse(SlaCalculator.isResponseBreached(ticket, now));
    }

    @Test
    @DisplayName("解决 SLA 超时判断")
    void shouldDetectResolveBreach() {
        OpsTicketDO ticket = new OpsTicketDO();
        ticket.setResolveDueAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 9, 0);

        assertTrue(SlaCalculator.isResolveBreached(ticket, now));
    }

    @Test
    @DisplayName("距离响应 SLA 剩余分钟数")
    void shouldCalculateResponseRemainMinutes() {
        OpsTicketDO ticket = new OpsTicketDO();
        ticket.setResponseDueAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 9, 30);

        assertEquals(30, SlaCalculator.responseRemainMinutes(ticket, now));
    }

    @Test
    @DisplayName("距离解决 SLA 剩余分钟数 - 已超时返回负值")
    void shouldCalculateNegativeRemainMinutesWhenBreached() {
        OpsTicketDO ticket = new OpsTicketDO();
        ticket.setResolveDueAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 10, 0);

        assertEquals(-60, SlaCalculator.resolveRemainMinutes(ticket, now));
    }

    @Test
    @DisplayName("已派单判断 - ASSIGNED/IN_PROGRESS/RESOLVED/CLOSED")
    void shouldDetectAssignedStatus() {
        OpsTicketDO ticket = new OpsTicketDO();
        ticket.setStatus(OpsTicketStatus.ASSIGNED.getCode());
        assertTrue(SlaCalculator.isAssigned(ticket));

        ticket.setStatus(OpsTicketStatus.IN_PROGRESS.getCode());
        assertTrue(SlaCalculator.isAssigned(ticket));

        ticket.setStatus(OpsTicketStatus.RESOLVED.getCode());
        assertTrue(SlaCalculator.isAssigned(ticket));

        ticket.setStatus(OpsTicketStatus.CLOSED.getCode());
        assertTrue(SlaCalculator.isAssigned(ticket));
    }

    @Test
    @DisplayName("未派单判断 - OPEN 状态")
    void shouldDetectNotAssignedStatus() {
        OpsTicketDO ticket = new OpsTicketDO();
        ticket.setStatus(OpsTicketStatus.OPEN.getCode());
        assertFalse(SlaCalculator.isAssigned(ticket));
    }

    @Test
    @DisplayName("可发起满意度评价 - RESOLVED/CLOSED 状态")
    void shouldAllowEvaluationForResolvedOrClosed() {
        OpsTicketDO ticket = new OpsTicketDO();
        ticket.setStatus(OpsTicketStatus.RESOLVED.getCode());
        assertTrue(SlaCalculator.canEvaluate(ticket));

        ticket.setStatus(OpsTicketStatus.CLOSED.getCode());
        assertTrue(SlaCalculator.canEvaluate(ticket));
    }

    @Test
    @DisplayName("不可发起满意度评价 - OPEN 状态")
    void shouldNotAllowEvaluationForOpen() {
        OpsTicketDO ticket = new OpsTicketDO();
        ticket.setStatus(OpsTicketStatus.OPEN.getCode());
        assertFalse(SlaCalculator.canEvaluate(ticket));
    }
}