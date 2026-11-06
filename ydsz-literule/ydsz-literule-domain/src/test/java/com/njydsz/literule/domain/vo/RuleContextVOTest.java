package com.njydsz.literule.domain.vo;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.literule.domain.enums.RuleEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RuleContextVO} 单元测试。
 *
 * <p>覆盖：静态工厂（默认租户/环境、不可变快照）、事实取值、表达式缓存懒初始化与清理。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("规则评估上下文")
class RuleContextVOTest {

  private Map<String, Object> facts() {
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("amount", 1000);
    facts.put("level", "vip");
    return facts;
  }

  @Nested
  @DisplayName("of 静态工厂")
  class OfFactory {

    @Test
    @DisplayName("最简工厂使用默认场景/来源/租户/环境")
    void simplestFactory() {
      RuleContextVO ctx = RuleContextVO.of(facts());
      assertThat(ctx.getScenario()).isEqualTo("DEFAULT");
      assertThat(ctx.getSource()).isEqualTo("UNKNOWN");
      assertThat(ctx.getTenantId()).isEqualTo("0");
      assertThat(ctx.getEnvironment()).isEqualTo(RuleEnvironment.DEFAULT);
      assertThat(ctx.getTraceId()).isNotBlank();
    }

    @Test
    @DisplayName("显式指定租户与环境")
    void explicitTenantAndEnvironment() {
      RuleContextVO ctx =
          RuleContextVO.of(facts(), "COCKPIT", "api", "trace-1", "tenant-9", "prod");
      assertThat(ctx.getScenario()).isEqualTo("COCKPIT");
      assertThat(ctx.getSource()).isEqualTo("api");
      assertThat(ctx.getTraceId()).isEqualTo("trace-1");
      assertThat(ctx.getTenantId()).isEqualTo("tenant-9");
      assertThat(ctx.getEnvironment()).isEqualTo("prod");
    }

    @Test
    @DisplayName("environment 为 null 回退 default")
    void nullEnvironmentFallsBack() {
      RuleContextVO ctx =
          RuleContextVO.of(facts(), "s", "src", "t", "tenant-1", null);
      assertThat(ctx.getEnvironment()).isEqualTo(RuleEnvironment.DEFAULT);
    }

    @Test
    @DisplayName("facts 为 null 抛 NullPointerException")
    void nullFactsThrows() {
      assertThatThrownBy(() -> RuleContextVO.of(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("事实快照不可变")
  class ImmutableFacts {

    @Test
    @DisplayName("getFacts 返回不可修改 Map")
    void factsAreUnmodifiable() {
      RuleContextVO ctx = RuleContextVO.of(facts());
      assertThatThrownBy(() -> ctx.getFacts().put("x", 1))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("外部修改源 Map 不影响上下文（防御性拷贝）")
    void defensiveCopy() {
      Map<String, Object> source = facts();
      RuleContextVO ctx = RuleContextVO.of(source, "s", "src");
      source.put("extra", "value");
      assertThat(ctx.get("extra")).isNull();
      assertThat(ctx.getFacts()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("get 取值")
  class Get {

    @Test
    @DisplayName("存在的 key 返回值，不存在的返回 null")
    void getByKey() {
      RuleContextVO ctx = RuleContextVO.of(facts());
      assertThat(ctx.get("amount")).isEqualTo(1000);
      assertThat(ctx.get("missing")).isNull();
    }
  }

  @Nested
  @DisplayName("表达式缓存")
  class ExpressionCache {

    @Test
    @DisplayName("懒初始化返回同一实例")
    void lazyInitReturnsSameInstance() {
      RuleContextVO ctx = RuleContextVO.of(facts());
      assertThat(ctx.getExpressionCache()).isSameAs(ctx.getExpressionCache());
    }

    @Test
    @DisplayName("缓存可写入与读取")
    void cacheStoresValues() {
      RuleContextVO ctx = RuleContextVO.of(facts());
      ctx.getExpressionCache().put("expr", "result");
      assertThat(ctx.getExpressionCache()).containsEntry("expr", "result");
    }

    @Test
    @DisplayName("clearExpressionCache 清空缓存")
    void clearEmptiesCache() {
      RuleContextVO ctx = RuleContextVO.of(facts());
      ctx.getExpressionCache().put("expr", "result");
      ctx.clearExpressionCache();
      assertThat(ctx.getExpressionCache()).isEmpty();
    }

    @Test
    @DisplayName("未初始化时清理不抛异常")
    void clearWithoutInitIsSafe() {
      RuleContextVO ctx = RuleContextVO.of(facts());
      ctx.clearExpressionCache();
      assertThat(ctx.getExpressionCache()).isEmpty();
    }
  }

  @Nested
  @DisplayName("toString")
  class ToString {

    @Test
    @DisplayName("包含场景与租户信息")
    void containsScenarioAndTenant() {
      RuleContextVO ctx = RuleContextVO.of(facts(), "COCKPIT", "api");
      assertThat(ctx.toString())
          .contains("COCKPIT")
          .contains("api")
          .contains("tenantId=0");
    }
  }
}
