package com.njydsz.cronjob.domain.event;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JobEvent} 单元测试。
 *
 * <p>覆盖：record 组件访问器、aggregateId/aggregateType 契约、Type.parse 解析（含大小写/空值/非法）。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("任务领域事件")
class JobEventTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);

  @Nested
  @DisplayName("record 组件访问器")
  class Accessors {

    @Test
    @DisplayName("构造后各组件可读取")
    void components() {
      JobEvent e =
          new JobEvent("evt-1", "job-1", "CREATED", "{\"k\":1}", "admin", NOW);
      assertThat(e.eventId()).isEqualTo("evt-1");
      assertThat(e.jobId()).isEqualTo("job-1");
      assertThat(e.eventType()).isEqualTo("CREATED");
      assertThat(e.payload()).isEqualTo("{\"k\":1}");
      assertThat(e.operator()).isEqualTo("admin");
      assertThat(e.occurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("aggregateId 返回 jobId")
    void aggregateIdIsJobId() {
      JobEvent e = new JobEvent("evt-1", "job-1", "UPDATED", null, "admin", NOW);
      assertThat(e.aggregateId()).isEqualTo("job-1");
    }

    @Test
    @DisplayName("aggregateType 返回常量 job")
    void aggregateTypeIsJob() {
      JobEvent e = new JobEvent("evt-1", "job-1", "DELETED", null, "admin", NOW);
      assertThat(e.aggregateType()).isEqualTo(JobEvent.AGGREGATE_TYPE);
      assertThat(e.aggregateType()).isEqualTo("job");
    }

    @Test
    @DisplayName("record equals/hashCode 按组件比较")
    void equalsAndHashCode() {
      JobEvent a = new JobEvent("e", "j", "CREATED", "p", "op", NOW);
      JobEvent b = new JobEvent("e", "j", "CREATED", "p", "op", NOW);
      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
  }

  @Nested
  @DisplayName("Type.parse 解析")
  class TypeParse {

    @ParameterizedTest
    @CsvSource({
      "CREATED, CREATED",
      "created, CREATED",
      "Updated, UPDATED",
      "STATUS_CHANGED, STATUS_CHANGED",
      "TRIGGERED, TRIGGERED",
      "DELETED, DELETED",
      "MIGRATED, MIGRATED"
    })
    @DisplayName("大小写不敏感解析")
    void caseInsensitive(String input, JobEvent.Type expected) {
      assertThat(JobEvent.Type.parse(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null/空串返回 null")
    void nullAndEmpty(String input) {
      assertThat(JobEvent.Type.parse(input)).isNull();
    }

    @Test
    @DisplayName("空白字符串返回 null")
    void blankReturnsNull() {
      assertThat(JobEvent.Type.parse("   ")).isNull();
    }

    @Test
    @DisplayName("非法值返回 null（不抛异常）")
    void illegalReturnsNull() {
      assertThat(JobEvent.Type.parse("NOT_A_TYPE")).isNull();
    }

    @Test
    @DisplayName("前后空白被 trim 后解析")
    void trimBeforeParse() {
      assertThat(JobEvent.Type.parse("  CREATED  ")).isEqualTo(JobEvent.Type.CREATED);
    }
  }
}
