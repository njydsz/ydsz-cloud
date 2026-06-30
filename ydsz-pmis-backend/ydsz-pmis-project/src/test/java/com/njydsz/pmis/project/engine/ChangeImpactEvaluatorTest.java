package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.dto.ProjectChangeCreateDTO;
import com.njydsz.pmis.project.enums.ChangeType;
import com.njydsz.pmis.project.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChangeImpactEvaluator 影响评估引擎测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ChangeImpactEvaluator 变更影响评估")
class ChangeImpactEvaluatorTest {

    @Test
    @DisplayName("空 DTO")
    void nullDto() {
        ChangeImpactEvaluator.ImpactResult r = ChangeImpactEvaluator.evaluate(null);
        assertThat(r.level()).isEqualTo(RiskLevel.LOW);
        assertThat(r.major()).isFalse();
    }

    @Test
    @DisplayName("空字段")
    void empty() {
        ProjectChangeCreateDTO dto = new ProjectChangeCreateDTO();
        ChangeImpactEvaluator.ImpactResult r = ChangeImpactEvaluator.evaluate(dto);
        assertThat(r.level()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("预算超 50 万 重大")
    void majorBudget() {
        ProjectChangeCreateDTO dto = new ProjectChangeCreateDTO();
        dto.setBudgetImpact(new BigDecimal("600000"));
        ChangeImpactEvaluator.ImpactResult r = ChangeImpactEvaluator.evaluate(dto);
        assertThat(r.major()).isTrue();
    }

    @Test
    @DisplayName("合同超 100 万 重大")
    void majorContract() {
        ProjectChangeCreateDTO dto = new ProjectChangeCreateDTO();
        dto.setContractImpact(new BigDecimal("1200000"));
        ChangeImpactEvaluator.ImpactResult r = ChangeImpactEvaluator.evaluate(dto);
        assertThat(r.major()).isTrue();
    }

    @Test
    @DisplayName("进度超 30 天 重大")
    void majorSchedule() {
        ProjectChangeCreateDTO dto = new ProjectChangeCreateDTO();
        dto.setScheduleImpactDays(45);
        ChangeImpactEvaluator.ImpactResult r = ChangeImpactEvaluator.evaluate(dto);
        assertThat(r.major()).isTrue();
    }

    @Test
    @DisplayName("CONTRACT 类型 重大")
    void majorContractType() {
        ProjectChangeCreateDTO dto = new ProjectChangeCreateDTO();
        dto.setChangeType(ChangeType.CONTRACT.getCode());
        ChangeImpactEvaluator.ImpactResult r = ChangeImpactEvaluator.evaluate(dto);
        assertThat(r.major()).isTrue();
    }

    @Test
    @DisplayName("综合评估 HIGH")
    void highLevel() {
        ProjectChangeCreateDTO dto = new ProjectChangeCreateDTO();
        dto.setBudgetImpact(new BigDecimal("600000"));
        dto.setContractImpact(new BigDecimal("1200000"));
        dto.setScheduleImpactDays(45);
        dto.setProfitImpact(new BigDecimal("200000"));
        dto.setAffectedWbsCount(10);
        dto.setAffectedStaffCount(5);
        dto.setChangeType(ChangeType.CONTRACT.getCode());
        ChangeImpactEvaluator.ImpactResult r = ChangeImpactEvaluator.evaluate(dto);
        assertThat(r.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(r.major()).isTrue();
    }

    @Test
    @DisplayName("利润影响百分比")
    void profitPct() {
        ProjectChangeCreateDTO dto = new ProjectChangeCreateDTO();
        dto.setProfitImpact(new BigDecimal("0.05"));
        ChangeImpactEvaluator.ImpactResult r = ChangeImpactEvaluator.evaluate(dto);
        assertThat(r.profitImpactPct()).isNotNull();
    }

    @Test
    @DisplayName("利润影响超出 10%")
    void profitPctMajor() {
        ProjectChangeCreateDTO dto = new ProjectChangeCreateDTO();
        dto.setProfitImpact(new BigDecimal("0.5"));
        ChangeImpactEvaluator.ImpactResult r = ChangeImpactEvaluator.evaluate(dto);
        assertThat(r.profitImpactPct()).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("负向预算影响 取绝对值")
    void negativeBudget() {
        ProjectChangeCreateDTO dto = new ProjectChangeCreateDTO();
        dto.setBudgetImpact(new BigDecimal("-600000"));
        ChangeImpactEvaluator.ImpactResult r = ChangeImpactEvaluator.evaluate(dto);
        assertThat(r.major()).isTrue();
    }
}
