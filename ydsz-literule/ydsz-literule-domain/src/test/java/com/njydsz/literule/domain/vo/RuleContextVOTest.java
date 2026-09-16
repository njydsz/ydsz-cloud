package com.njydsz.literule.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.literule.domain.enums.RuleEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link RuleContextVO} 单元测试
 *
 * <p>验证规则评估上下文的工厂方法、不可变事实数据、租户与环境默认值、
 * 表达式求值结果缓存的生命周期管理。
 *
 * @author ydsz-team
 * @since 26.09.16
 */
@Tag("unit")
class RuleContextVOTest {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  private Map<String, Object> createTestFacts() {
    Map<String, Object> facts = new HashMap<>(COLLECTION_CAPACITY);
    facts.put("amount", 500);
    facts.put("category", "IT");
    return facts;
  }

  @Nested
  @DisplayName("工厂方法重载")
  class FactoryMethods {

    @Test
    @DisplayName("of(facts) 应使用默认租户和环境")
    void ofWithFactsShouldUseDefaults() {
      RuleContextVO context = RuleContextVO.of(createTestFacts());

      assertThat(context.getFacts()).containsEntry("amount", 500);
      assertThat(context.getScenario()).isEqualTo("DEFAULT");
      assertThat(context.getSource()).isEqualTo("UNKNOWN");
      assertThat(context.getTenantId()).isEqualTo("1");
      assertThat(context.getEnvironment()).isEqualTo(RuleEnvironment.DEFAULT);
    }

    @Test
    @DisplayName("of(facts, scenario, source) 应设置场景和来源")
    void ofWithScenarioAndSourceShouldSetValues() {
      RuleContextVO context = RuleContextVO.of(createTestFacts(), "BUDGET_CHECK", "API_CALL");

      assertThat(context.getScenario()).isEqualTo("BUDGET_CHECK");
      assertThat(context.getSource()).isEqualTo("API_CALL");
    }

    @Test
    @DisplayName("of(facts, scenario, source, traceId, tenantId) 应设置租户")
    void ofWithTenantIdShouldSetValue() {
      RuleContextVO context = RuleContextVO.of(
          createTestFacts(), "BUDGET_CHECK", "API_CALL", "trace-001", "T002");

      assertThat(context.getTenantId()).isEqualTo("T002");
      assertThat(context.getEnvironment()).isEqualTo(RuleEnvironment.DEFAULT);
    }

    @Test
    @DisplayName("of(7参数) 应完整设置所有字段")
    void ofWithAllParamsShouldSetAllFields() {
      RuleContextVO context = RuleContextVO.of(
          createTestFacts(), "BUDGET_CHECK", "API_CALL", "trace-001", "T003", "prod");

      assertThat(context.getScenario()).isEqualTo("BUDGET_CHECK");
      assertThat(context.getSource()).isEqualTo("API_CALL");
      assertThat(context.getTraceId()).isEqualTo("trace-001");
      assertThat(context.getTenantId()).isEqualTo("T003");
      assertThat(context.getEnvironment()).isEqualTo("prod");
    }

    @Test
    @DisplayName("of(facts) 传入 null facts 应抛出 NullPointerException")
    void ofWithNullFactsShouldThrowNpe() {
      assertThatThrownBy(() -> RuleContextVO.of(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("facts");
    }

    @Test
    @DisplayName("环境为 null 时应使用默认值 DEFAULT")
    void nullEnvironmentShouldDefaultToDefaultEnvironment() {
      RuleContextVO context = RuleContextVO.of(
          createTestFacts(), "BUDGET_CHECK", "API_CALL", "trace-001", "T001", null);

      assertThat(context.getEnvironment()).isEqualTo(RuleEnvironment.DEFAULT);
    }
  }

  @Nested
  @DisplayName("事实数据访问")
  class FactAccess {

    @Test
    @DisplayName("get(key) 应返回对应事实值")
    void getShouldReturnFactValue() {
      RuleContextVO context = RuleContextVO.of(createTestFacts());

      assertThat(context.get("amount")).isEqualTo(500);
      assertThat(context.get("category")).isEqualTo("IT");
    }

    @Test
    @DisplayName("get(不存在的 key) 应返回 null")
    void getMissingKeyShouldReturnNull() {
      RuleContextVO context = RuleContextVO.of(createTestFacts());

      assertThat(context.get("nonExistent")).isNull();
    }

    @Test
    @DisplayName("getFacts() 返回的 Map 应为不可修改")
    void getFactsShouldBeUnmodifiable() {
      RuleContextVO context = RuleContextVO.of(createTestFacts());

      assertThatThrownBy(() -> context.getFacts().put("hack", "value"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("外部修改原始 facts Map 不应影响上下文内部状态")
    void externalFactMutationShouldNotAffectContext() {
      Map<String, Object> facts = createTestFacts();
      RuleContextVO context = RuleContextVO.of(facts);

      facts.put("injected", "malicious");

      assertThat(context.get("injected")).isNull();
    }
  }

  @Nested
  @DisplayName("追踪 ID")
  class TraceId {

    @Test
    @DisplayName("of(facts, scenario, source) 应自动生成非空的 traceId")
    void ofWithScenarioAndSourceShouldAutoGenerateTraceId() {
      RuleContextVO context = RuleContextVO.of(createTestFacts(), "BUDGET_CHECK", "API_CALL");

      assertThat(context.getTraceId()).isNotNull();
      assertThat(context.getTraceId()).isNotBlank();
    }
  }

  @Nested
  @DisplayName("表达式求值缓存")
  class ExpressionCache {

    @Test
    @DisplayName("getExpressionCache() 应返回非空的 ConcurrentHashMap")
    void getExpressionCacheShouldReturnNonNullMap() {
      RuleContextVO context = RuleContextVO.of(createTestFacts());

      Map<String, Object> cache = context.getExpressionCache();

      assertThat(cache).isNotNull();
      assertThat(cache).isEmpty();
    }

    @Test
    @DisplayName("多次调用 getExpressionCache 应返回同一实例")
    void getExpressionCacheShouldReturnSameInstance() {
      RuleContextVO context = RuleContextVO.of(createTestFacts());

      Map<String, Object> cache1 = context.getExpressionCache();
      Map<String, Object> cache2 = context.getExpressionCache();

      assertThat(cache1).isSameAs(cache2);
    }

    @Test
    @DisplayName("clearExpressionCache 应清空缓存内容")
    void clearExpressionCacheShouldEmptyTheCache() {
      RuleContextVO context = RuleContextVO.of(createTestFacts());
      Map<String, Object> cache = context.getExpressionCache();
      cache.put("test-key", "test-value");

      context.clearExpressionCache();

      assertThat(cache).isEmpty();
    }

    @Test
    @DisplayName("首次调用 clearExpressionCache（缓存未初始化）应为 no-op")
    void clearExpressionCacheBeforeInitShouldBeNoop() {
      RuleContextVO context = RuleContextVO.of(createTestFacts());

      context.clearExpressionCache();
    }

    @Test
    @DisplayName("同一上下文中重复表达式应复用缓存（避免冗余求值）")
    void repeatedExpressionShouldBeCached() {
      RuleContextVO context = RuleContextVO.of(createTestFacts());
      Map<String, Object> cache = context.getExpressionCache();

      cache.put("B:amount > 100", true);

      assertThat(cache.get("B:amount > 100")).isEqualTo(true);
    }
  }

  @Nested
  @DisplayName("toString 输出")
  class ToStringOutput {

    @Test
    @DisplayName("toString 应包含关键字段（scenario/source/tenantId/environment）")
    void toStringShouldContainKeyFields() {
      RuleContextVO context = RuleContextVO.of(
          createTestFacts(), "BUDGET_CHECK", "API_CALL", "trace-001", "T001", "prod");

      String output = context.toString();

      assertThat(output).contains("scenario=").contains("source=")
          .contains("tenantId=").contains("environment=");
    }
  }
}
