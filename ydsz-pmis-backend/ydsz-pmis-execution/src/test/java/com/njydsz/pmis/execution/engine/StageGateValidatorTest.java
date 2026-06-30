package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.entity.DeliveryItemDO;
import com.njydsz.pmis.execution.enums.DeliveryItemStatus;
import com.njydsz.pmis.execution.enums.DeliveryStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StageGateValidator 阶段门控测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("StageGateValidator 阶段门控")
class StageGateValidatorTest {

    @Test
    @DisplayName("目标为空")
    void nullTarget() {
        StageGateValidator.GateCheckResult r = StageGateValidator.check(1L, null, List.of(), "L3");
        assertThat(r.passed()).isFalse();
    }

    @Test
    @DisplayName("CD1 启动 免校验")
    void cd1() {
        StageGateValidator.GateCheckResult r = StageGateValidator.check(1L, DeliveryStage.CD1_KICKOFF, null, "L3");
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("前置无交付物")
    void noPrevItems() {
        StageGateValidator.GateCheckResult r = StageGateValidator.check(1L, DeliveryStage.CD2_DESIGN, List.of(), "L3");
        assertThat(r.passed()).isFalse();
    }

    @Test
    @DisplayName("必交付未通过")
    void requiredNotPassed() {
        DeliveryItemDO i = new DeliveryItemDO();
        i.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        i.setRequired(1);
        i.setStatus(DeliveryItemStatus.SUBMITTED.getCode());
        i.setDeliveryName("需求文档");
        StageGateValidator.GateCheckResult r = StageGateValidator.check(1L,
                DeliveryStage.CD2_DESIGN, List.of(i), "L3");
        assertThat(r.passed()).isFalse();
        assertThat(r.message()).contains("需求文档");
    }

    @Test
    @DisplayName("全部通过")
    void allPassed() {
        DeliveryItemDO i = new DeliveryItemDO();
        i.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        i.setRequired(1);
        i.setStatus(DeliveryItemStatus.ACCEPTED.getCode());
        i.setDeliveryName("n");
        StageGateValidator.GateCheckResult r = StageGateValidator.check(1L,
                DeliveryStage.CD2_DESIGN, List.of(i), "L3");
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("豁免 通过")
    void waived() {
        DeliveryItemDO i = new DeliveryItemDO();
        i.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        i.setRequired(1);
        i.setStatus(DeliveryItemStatus.WAIVED.getCode());
        i.setDeliveryName("n");
        StageGateValidator.GateCheckResult r = StageGateValidator.check(1L,
                DeliveryStage.CD2_DESIGN, List.of(i), "L3");
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("非必交付不校验")
    void notRequired() {
        DeliveryItemDO i = new DeliveryItemDO();
        i.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        i.setRequired(0);
        i.setStatus(DeliveryItemStatus.SUBMITTED.getCode());
        StageGateValidator.GateCheckResult r = StageGateValidator.check(1L,
                DeliveryStage.CD2_DESIGN, List.of(i), "L3");
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("L13+ 项目 TR 校验")
    void highLevelTr() {
        DeliveryItemDO i = new DeliveryItemDO();
        i.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        i.setRequired(1);
        i.setTrRequired(1);
        i.setTrCompleted(0);
        i.setStatus(DeliveryItemStatus.ACCEPTED.getCode());
        i.setDeliveryName("n");
        StageGateValidator.GateCheckResult r = StageGateValidator.check(1L,
                DeliveryStage.CD2_DESIGN, List.of(i), "L15");
        assertThat(r.passed()).isFalse();
    }

    @Test
    @DisplayName("L13+ 项目 TR 已完成")
    void highLevelTrDone() {
        DeliveryItemDO i = new DeliveryItemDO();
        i.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        i.setRequired(1);
        i.setTrRequired(1);
        i.setTrCompleted(1);
        i.setStatus(DeliveryItemStatus.ACCEPTED.getCode());
        i.setDeliveryName("n");
        StageGateValidator.GateCheckResult r = StageGateValidator.check(1L,
                DeliveryStage.CD2_DESIGN, List.of(i), "L15");
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("L12 项目 不做 TR 校验")
    void lowLevelNoTr() {
        DeliveryItemDO i = new DeliveryItemDO();
        i.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        i.setRequired(1);
        i.setTrRequired(1);
        i.setTrCompleted(0);
        i.setStatus(DeliveryItemStatus.ACCEPTED.getCode());
        i.setDeliveryName("n");
        StageGateValidator.GateCheckResult r = StageGateValidator.check(1L,
                DeliveryStage.CD2_DESIGN, List.of(i), "L12");
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("previousStage")
    void prev() {
        assertThat(StageGateValidator.previousStage(DeliveryStage.CD2_DESIGN)).isEqualTo(DeliveryStage.CD1_KICKOFF);
        assertThat(StageGateValidator.previousStage(DeliveryStage.CD5_GO_LIVE)).isEqualTo(DeliveryStage.CD4_UAT);
        assertThat(StageGateValidator.previousStage(DeliveryStage.CD1_KICKOFF)).isNull();
        assertThat(StageGateValidator.previousStage(null)).isNull();
    }

    @Test
    @DisplayName("isHighLevel")
    void highLevel() {
        assertThat(StageGateValidator.isHighLevel("L13")).isTrue();
        assertThat(StageGateValidator.isHighLevel("L18")).isTrue();
        assertThat(StageGateValidator.isHighLevel("L12")).isFalse();
        assertThat(StageGateValidator.isHighLevel(null)).isFalse();
        assertThat(StageGateValidator.isHighLevel("")).isFalse();
        assertThat(StageGateValidator.isHighLevel("LXX")).isFalse();
    }
}
