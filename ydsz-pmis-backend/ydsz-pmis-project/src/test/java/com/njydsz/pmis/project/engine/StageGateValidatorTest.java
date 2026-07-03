package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.DeliveryItemDO;
import com.njydsz.pmis.project.enums.DeliveryItemStatus;
import com.njydsz.pmis.project.enums.DeliveryStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("阶段门控校验器测试")
class StageGateValidatorTest {

    @Test
    @DisplayName("CD1 启动阶段无需前置校验，直接通过")
    void shouldPassWhenTargetIsCd1Kickoff() {
        StageGateValidator.GateCheckResult result = StageGateValidator.check(
                1L, DeliveryStage.CD1_KICKOFF, List.of(), "L13");
        assertTrue(result.passed());
        assertEquals("CD1 启动阶段无前置校验", result.message());
    }

    @Test
    @DisplayName("目标阶段为 null 返回失败")
    void shouldFailWhenTargetStageIsNull() {
        StageGateValidator.GateCheckResult result = StageGateValidator.check(
                1L, null, List.of(), "L13");
        assertFalse(result.passed());
        assertEquals("目标阶段不能为空", result.message());
    }

    @Test
    @DisplayName("前置阶段无交付物时返回失败")
    void shouldFailWhenNoPreviousStageItems() {
        StageGateValidator.GateCheckResult result = StageGateValidator.check(
                1L, DeliveryStage.CD2_DESIGN, List.of(), "L13");
        assertFalse(result.passed());
        assertTrue(result.message().contains("无任何交付物"));
    }

    @Test
    @DisplayName("前置阶段必交付物已验收时通过")
    void shouldPassWhenRequiredItemsAccepted() {
        DeliveryItemDO item = new DeliveryItemDO();
        item.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        item.setRequired(1);
        item.setStatus(DeliveryItemStatus.ACCEPTED.getCode());
        item.setDeliveryName("交付物A");

        StageGateValidator.GateCheckResult result = StageGateValidator.check(
                1L, DeliveryStage.CD2_DESIGN, List.of(item), "L13");
        assertTrue(result.passed());
    }

    @Test
    @DisplayName("前置阶段必交付物已豁免时通过")
    void shouldPassWhenRequiredItemsWaived() {
        DeliveryItemDO item = new DeliveryItemDO();
        item.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        item.setRequired(1);
        item.setStatus(DeliveryItemStatus.WAIVED.getCode());
        item.setDeliveryName("交付物A");

        StageGateValidator.GateCheckResult result = StageGateValidator.check(
                1L, DeliveryStage.CD2_DESIGN, List.of(item), "L13");
        assertTrue(result.passed());
    }

    @Test
    @DisplayName("前置阶段必交付物未通过时返回失败")
    void shouldFailWhenRequiredItemsNotPassed() {
        DeliveryItemDO item = new DeliveryItemDO();
        item.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        item.setRequired(1);
        item.setStatus(DeliveryItemStatus.PENDING.getCode());
        item.setDeliveryName("交付物A");

        StageGateValidator.GateCheckResult result = StageGateValidator.check(
                1L, DeliveryStage.CD2_DESIGN, List.of(item), "L13");
        assertFalse(result.passed());
        assertTrue(result.message().contains("无法进入"));
    }

    @Test
    @DisplayName("高级项目 TR 未完成时返回失败")
    void shouldFailWhenHighLevelProjectTrNotCompleted() {
        DeliveryItemDO item = new DeliveryItemDO();
        item.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        item.setRequired(1);
        item.setStatus(DeliveryItemStatus.ACCEPTED.getCode());
        item.setDeliveryName("交付物A");
        item.setTrRequired(1);
        item.setTrCompleted(0);

        StageGateValidator.GateCheckResult result = StageGateValidator.check(
                1L, DeliveryStage.CD2_DESIGN, List.of(item), "L13");
        assertFalse(result.passed());
        assertTrue(result.message().contains("TR 未完成"));
    }

    @Test
    @DisplayName("非高级项目不校验 TR")
    void shouldSkipTrCheckForNonHighLevelProject() {
        DeliveryItemDO item = new DeliveryItemDO();
        item.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        item.setRequired(1);
        item.setStatus(DeliveryItemStatus.ACCEPTED.getCode());
        item.setDeliveryName("交付物A");
        item.setTrRequired(1);
        item.setTrCompleted(0);

        StageGateValidator.GateCheckResult result = StageGateValidator.check(
                1L, DeliveryStage.CD2_DESIGN, List.of(item), "L12");
        assertTrue(result.passed());
    }

    @Test
    @DisplayName("isHighLevel - L13 及以上为高级项目")
    void shouldIdentifyHighLevelProjects() {
        assertTrue(StageGateValidator.isHighLevel("L13"));
        assertTrue(StageGateValidator.isHighLevel("L14"));
        assertTrue(StageGateValidator.isHighLevel("L20"));
    }

    @Test
    @DisplayName("isHighLevel - L12 及以下为非高级项目")
    void shouldIdentifyNonHighLevelProjects() {
        assertFalse(StageGateValidator.isHighLevel("L12"));
        assertFalse(StageGateValidator.isHighLevel("L1"));
        assertFalse(StageGateValidator.isHighLevel(null));
        assertFalse(StageGateValidator.isHighLevel(""));
    }

    @Test
    @DisplayName("previousStage - 获取各阶段的前置阶段")
    void shouldReturnCorrectPreviousStage() {
        assertEquals(DeliveryStage.CD1_KICKOFF, StageGateValidator.previousStage(DeliveryStage.CD2_DESIGN));
        assertEquals(DeliveryStage.CD2_DESIGN, StageGateValidator.previousStage(DeliveryStage.CD3_BUILD));
        assertEquals(DeliveryStage.CD3_BUILD, StageGateValidator.previousStage(DeliveryStage.CD4_UAT));
        assertEquals(DeliveryStage.CD4_UAT, StageGateValidator.previousStage(DeliveryStage.CD5_GO_LIVE));
        assertNull(StageGateValidator.previousStage(DeliveryStage.CD1_KICKOFF));
        assertNull(StageGateValidator.previousStage(null));
    }
}