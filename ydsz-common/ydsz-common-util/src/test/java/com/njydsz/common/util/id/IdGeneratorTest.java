package com.njydsz.common.util.id;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IdGenerator 静态门面测试。
 *
 * <p>覆盖降级语义与可观测性：
 *
 * <ul>
 *   <li>Supplier 未注册（非 Spring 环境）→ 随机数降级 + DEGRADED_COUNT 递增
 *   <li>Supplier 注册后 → 委托 SnowflakeIdGenerator，无降级计数
 *   <li>fallbackToUuid 开关的行为差异
 * </ul>
 *
 * <p>静态状态通过包级 {@code resetForTesting()} 在每个用例前复位，保证用例隔离。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class IdGeneratorTest {

  @BeforeEach
  void resetState() {
    IdGenerator.resetForTesting();
    IdGenerator.setGeneratorSupplier(null);
    IdGenerator.setFallbackToUuid(false);
  }

  @AfterEach
  void cleanUp() {
    IdGenerator.resetForTesting();
    IdGenerator.setGeneratorSupplier(null);
    IdGenerator.setFallbackToUuid(false);
  }

  @Test
  @DisplayName("未注册 Supplier：降级为伪随机数并累计降级计数")
  void fallsBackToRandomWhenSupplierAbsent() {
    long before = IdGenerator.getDegradedCount().get();

    String id = IdGenerator.nextIdStr();

    assertThat(id).as("降级 ID 应为伪随机 long 的字符串形式").matches("-?\\d+");
    assertThat(IdGenerator.getDegradedCount().get()).as("降级计数应递增").isGreaterThan(before);
  }

  @Test
  @DisplayName("注册 Supplier 后：委托 Snowflake 生成且无降级")
  void delegatesToRegisteredGenerator() {
    SnowflakeIdGenerator generator =
        new SnowflakeIdGenerator(
            new SnowflakeProperties(), WorkerIdAllocatorChain.defaults());
    IdGenerator.setGeneratorSupplier(() -> generator);
    long before = IdGenerator.getDegradedCount().get();

    long id = IdGenerator.nextId();
    long next = IdGenerator.nextId();

    assertThat(id).isPositive();
    assertThat(next)
        .as("连续两次生成的 Snowflake ID 应单调递增")
        .isGreaterThan(id);
    assertThat(IdGenerator.getDegradedCount().get())
        .as("正常路径不应产生降级计数")
        .isEqualTo(before);
  }

  @Test
  @DisplayName("fallbackToUuid=true：降级为 32 位无连字符 UUID 字符串")
  void uuidFallbackProducesHexString() {
    IdGenerator.setFallbackToUuid(true);

    String id = IdGenerator.nextIdStr();

    assertThat(id).hasSize(32).matches("[0-9a-f]{32}");
  }

  @Test
  @DisplayName("fallbackToUuid=true：降级 long 恒为非负（清除符号位）")
  void uuidFallbackLongIsNonNegative() {
    IdGenerator.setFallbackToUuid(true);

    long id = IdGenerator.nextId();

    assertThat(id).isNotNegative();
  }
}
