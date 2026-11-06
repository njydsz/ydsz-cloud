package com.njydsz.cronjob.domain.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link ExecutionContextScope} 单元测试。
 *
 * <p>覆盖：of/ofLogger 工厂设置 ThreadLocal、close 清理 ThreadLocal、null 参数容忍、幂等 close。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("任务执行上下文作用域")
class ExecutionContextScopeTest {

  @Nested
  @DisplayName("of 工厂")
  class Of {

    @Test
    @DisplayName("设置 logger 与 shardingContext 后可读取")
    void setsBoth() {
      JobLogger logger = mock(JobLogger.class);
      ShardingContext ctx = new ShardingContext();
      ctx.setShardIndex(2);
      try (ExecutionContextScope scope = ExecutionContextScope.of(logger, ctx)) {
        assertThat(JobExecutionContext.getLogger()).isSameAs(logger);
        assertThat(JobExecutionContext.getShardingContext()).isSameAs(ctx);
        assertThat(scope).isNotNull();
      }
    }

    @Test
    @DisplayName("close 后 ThreadLocal 被清理")
    void clearsOnClose() {
      JobLogger logger = mock(JobLogger.class);
      ShardingContext ctx = new ShardingContext();
      ExecutionContextScope scope = ExecutionContextScope.of(logger, ctx);
      assertThat(JobExecutionContext.getLogger()).isSameAs(logger);
      assertThat(JobExecutionContext.getShardingContext()).isSameAs(ctx);
      scope.close();
      assertThat(JobExecutionContext.getLogger()).isNull();
      assertThat(JobExecutionContext.getShardingContext()).isNull();
    }

    @Test
    @DisplayName("null 参数不抛异常且不设置对应 ThreadLocal")
    void nullTolerated() {
      JobLogger logger = mock(JobLogger.class);
      ExecutionContextScope scope = ExecutionContextScope.of(logger, null);
      assertThat(JobExecutionContext.getLogger()).isSameAs(logger);
      assertThat(JobExecutionContext.getShardingContext()).isNull();
      scope.close();
    }

    @Test
    @DisplayName("两个 null 参数容忍")
    void bothNull() {
      ExecutionContextScope scope = ExecutionContextScope.of(null, null);
      assertThat(JobExecutionContext.getLogger()).isNull();
      assertThat(JobExecutionContext.getShardingContext()).isNull();
      scope.close();
    }
  }

  @Nested
  @DisplayName("ofLogger 工厂")
  class OfLogger {

    @Test
    @DisplayName("仅设置 logger，shardingContext 保持 null")
    void setsLoggerOnly() {
      JobLogger logger = mock(JobLogger.class);
      ExecutionContextScope scope = ExecutionContextScope.ofLogger(logger);
      assertThat(JobExecutionContext.getLogger()).isSameAs(logger);
      assertThat(JobExecutionContext.getShardingContext()).isNull();
      scope.close();
      assertThat(JobExecutionContext.getLogger()).isNull();
    }

    @Test
    @DisplayName("null logger 不抛异常")
    void nullLoggerTolerated() {
      ExecutionContextScope scope = ExecutionContextScope.ofLogger(null);
      assertThat(JobExecutionContext.getLogger()).isNull();
      scope.close();
    }
  }

  @Nested
  @DisplayName("close 幂等")
  class IdempotentClose {

    @Test
    @DisplayName("多次 close 不抛异常")
    void multipleClose() {
      JobLogger logger = mock(JobLogger.class);
      ExecutionContextScope scope = ExecutionContextScope.ofLogger(logger);
      scope.close();
      scope.close();
      scope.close();
      assertThat(JobExecutionContext.getLogger()).isNull();
      assertThat(JobExecutionContext.getShardingContext()).isNull();
    }
  }
}
