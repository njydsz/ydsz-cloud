package com.njydsz.pmis.audit.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DiffCalculatorTest {

    @Test
    void shouldReturnEmptyWhenBothNull() {
        assertThat(DiffCalculator.calculateDiff(null, null)).isEmpty();
    }

    @Test
    void shouldDetectAddedFields() {
        List<DiffCalculator.FieldDiff> diffs = DiffCalculator.calculateDiff(
                null, "{\"name\":\"test\",\"status\":\"active\"}");
        assertThat(diffs).hasSize(2);
        assertThat(diffs).allMatch(d -> "ADD".equals(d.getChangeType()));
    }

    @Test
    void shouldDetectDeletedFields() {
        List<DiffCalculator.FieldDiff> diffs = DiffCalculator.calculateDiff(
                "{\"name\":\"test\",\"status\":\"active\"}", null);
        assertThat(diffs).hasSize(2);
        assertThat(diffs).allMatch(d -> "DELETE".equals(d.getChangeType()));
    }

    @Test
    void shouldDetectModifiedFields() {
        List<DiffCalculator.FieldDiff> diffs = DiffCalculator.calculateDiff(
                "{\"name\":\"old\",\"status\":\"draft\"}",
                "{\"name\":\"new\",\"status\":\"published\"}");
        assertThat(diffs).hasSize(2);
        assertThat(diffs).allMatch(d -> "MODIFY".equals(d.getChangeType()));
        DiffCalculator.FieldDiff nameDiff = diffs.stream().filter(d -> "name".equals(d.getField())).findFirst().orElse(null);
        assertThat(nameDiff).isNotNull();
        assertThat(nameDiff.getOldValue()).isEqualTo("old");
        assertThat(nameDiff.getNewValue()).isEqualTo("new");
    }

    @Test
    void shouldDetectMixedChanges() {
        List<DiffCalculator.FieldDiff> diffs = DiffCalculator.calculateDiff(
                "{\"name\":\"old\",\"removed\":\"val\"}",
                "{\"name\":\"new\",\"added\":\"val\"}");
        assertThat(diffs).hasSize(3);
        assertThat(diffs.stream().filter(d -> "MODIFY".equals(d.getChangeType()))).hasSize(1);
        assertThat(diffs.stream().filter(d -> "DELETE".equals(d.getChangeType()))).hasSize(1);
        assertThat(diffs.stream().filter(d -> "ADD".equals(d.getChangeType()))).hasSize(1);
    }

    @Test
    void shouldReturnEmptyWhenNoChanges() {
        List<DiffCalculator.FieldDiff> diffs = DiffCalculator.calculateDiff(
                "{\"name\":\"test\"}", "{\"name\":\"test\"}");
        assertThat(diffs).isEmpty();
    }

    @Test
    void shouldHandleInvalidJson() {
        List<DiffCalculator.FieldDiff> diffs = DiffCalculator.calculateDiff(
                "{invalid}", "{\"name\":\"test\"}");
        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).getChangeType()).isEqualTo("ADD");
    }
}
