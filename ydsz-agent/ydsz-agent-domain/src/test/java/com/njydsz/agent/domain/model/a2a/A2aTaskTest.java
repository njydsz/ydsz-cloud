package com.njydsz.agent.domain.model.a2a;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.njydsz.agent.domain.model.a2a.A2aTask.A2aTaskStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link A2aTask} 单元测试。
 *
 * <p>覆盖：Lombok 构建器与 getter/setter、{@link A2aTaskStatus} 大小写不敏感解析、终态判定、未知/null 兜底 UNKNOWN。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("A2aTask A2A 任务模型")
class A2aTaskTest {

  @Nested
  @DisplayName("模型字段")
  class ModelTest {

    @Test
    @DisplayName("Builder 应正确构建全部字段")
    void shouldBuildWithAllFields() {
      LocalDateTime now = LocalDateTime.now();
      List<A2aMessage> history = new ArrayList<>();
      List<A2aArtifact> artifacts = new ArrayList<>();
      Map<String, Object> metadata = new HashMap<>();
      metadata.put("k", "v");

      A2aTask task = A2aTask.builder()
          .id("task-1")
          .contextId("ctx-1")
          .status(A2aTaskStatus.WORKING)
          .history(history)
          .artifacts(artifacts)
          .metadata(metadata)
          .createdAt(now)
          .updatedAt(now)
          .build();

      assertThat(task.getId()).isEqualTo("task-1");
      assertThat(task.getContextId()).isEqualTo("ctx-1");
      assertThat(task.getStatus()).isEqualTo(A2aTaskStatus.WORKING);
      assertThat(task.getHistory()).isEmpty();
      assertThat(task.getArtifacts()).isEmpty();
      assertThat(task.getMetadata()).containsEntry("k", "v");
      assertThat(task.getCreatedAt()).isEqualTo(now);
      assertThat(task.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("无参构造 + setter 应可用")
    void shouldSupportNoArgsConstructor() {
      A2aTask task = new A2aTask();
      task.setId("t2");
      task.setStatus(A2aTaskStatus.COMPLETED);
      assertThat(task.getId()).isEqualTo("t2");
      assertThat(task.getStatus()).isEqualTo(A2aTaskStatus.COMPLETED);
    }

    @Test
    @DisplayName("equals/hashCode 应基于字段值")
    void shouldImplementEqualsAndHashCode() {
      A2aTask a = A2aTask.builder().id("1").build();
      A2aTask b = A2aTask.builder().id("1").build();
      A2aTask c = A2aTask.builder().id("2").build();

      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
      assertThat(a).isNotEqualTo(c);
    }
  }

  @Nested
  @DisplayName("任务状态解析")
  class StatusTest {

    @ParameterizedTest(name = "fromProtocolValue(\"{0}\") 应返回 {1}")
    @CsvSource({
      "submitted, SUBMITTED",
      "working, WORKING",
      "input-required, INPUT_REQUIRED",
      "completed, COMPLETED",
      "canceled, CANCELED",
      "failed, FAILED",
      "rejected, REJECTED",
      "auth-required, AUTH_REQUIRED",
      "unknown, UNKNOWN",
      "WORKING, WORKING",
      "Completed, COMPLETED",
    })
    @DisplayName("大小写不敏感解析应成功")
    void shouldParseCaseInsensitive(String value, A2aTaskStatus expected) {
      assertThat(A2aTaskStatus.fromProtocolValue(value)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 应兜底为 UNKNOWN")
    void shouldFallbackForNull() {
      assertThat(A2aTaskStatus.fromProtocolValue(null)).isEqualTo(A2aTaskStatus.UNKNOWN);
    }

    @ParameterizedTest(name = "fromProtocolValue(\"{0}\") 应兜底为 UNKNOWN")
    @ValueSource(strings = {"done", "success", ""})
    @DisplayName("未知值应兜底为 UNKNOWN")
    void shouldFallbackForUnknown(String value) {
      assertThat(A2aTaskStatus.fromProtocolValue(value)).isEqualTo(A2aTaskStatus.UNKNOWN);
    }

    @Test
    @DisplayName("protocolValue 应小写（含连字符）")
    void shouldHaveLowercaseProtocolValue() {
      for (A2aTaskStatus status : A2aTaskStatus.values()) {
        assertThat(status.getProtocolValue()).isEqualTo(status.getProtocolValue().toLowerCase());
      }
    }
  }

  @Nested
  @DisplayName("任务终态判定")
  class TerminalTest {

    @ParameterizedTest(name = "isTerminal({0}) 应为 {1}")
    @CsvSource({
      "COMPLETED, true",
      "FAILED, true",
      "CANCELED, true",
      "REJECTED, true",
      "SUBMITTED, false",
      "WORKING, false",
      "INPUT_REQUIRED, false",
      "AUTH_REQUIRED, false",
      "UNKNOWN, false",
    })
    @DisplayName("终态判定应正确")
    void shouldJudgeTerminal(A2aTaskStatus status, boolean expected) {
      assertThat(status.isTerminal()).isEqualTo(expected);
    }
  }
}
