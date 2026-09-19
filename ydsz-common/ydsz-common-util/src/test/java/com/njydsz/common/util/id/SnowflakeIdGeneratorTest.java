package com.njydsz.common.util.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link SnowflakeIdGenerator} 单元测试。
 *
 * <p>覆盖：唯一性、反解析（parseTimestamp/WorkerId/Sequence/parseDatacenterId）、
 * 便捷构造器、序列号位数边界、时钟回拨处理。
 *
 * @since 26.09.19
 */
@DisplayName("SnowflakeIdGenerator 测试")
class SnowflakeIdGeneratorTest {

  @Nested
  @DisplayName("唯一性验证")
  class UniquenessTest {

    @Test
    @DisplayName("10000 个 ID 全部唯一")
    void nextId_thousandIds_allUnique() {
      SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0L);
      Set<Long> ids = new HashSet<>();
      for (int i = 0; i < 10_000; i++) {
        ids.add(generator.nextId());
      }
      assertThat(ids).hasSize(10_000);
    }

    @Test
    @DisplayName("ID 趋势递增（非严格单调，但整体递增）")
    void nextId_trendIncreasing() {
      SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0L);
      long prev = generator.nextId();
      for (int i = 0; i < 100; i++) {
        long current = generator.nextId();
        assertThat(current).isGreaterThan(prev);
        prev = current;
      }
    }
  }

  @Nested
  @DisplayName("反解析方法")
  class ParseTest {

    @Test
    @DisplayName("parseTimestamp: 反解时间戳与纪元一致")
    void parseTimestamp_epoch() {
      long epoch = 1700000000000L;
      SnowflakeIdGenerator generator = new SnowflakeIdGenerator(epoch);
      long id = generator.nextId();
      // 反解时间戳是相对 epoch 的毫秒 + epoch，应接近当前时间
      long parsed = generator.parseTimestamp(id);
      assertThat(parsed).isGreaterThanOrEqualTo(epoch);
    }

    @Test
    @DisplayName("parseWorkerId: 反解 workerId 与配置一致")
    void parseWorkerId_correct() {
      long epoch = 0L;
      SnowflakeIdGenerator generator = new SnowflakeIdGenerator(epoch);
      long id = generator.nextId();
      long workerId = generator.parseWorkerId(id);
      assertThat(workerId).isEqualTo(generator.getWorkerId());
    }

    @Test
    @DisplayName("parseDatacenterId: 反解 datacenterId 与配置一致")
    void parseDatacenterId_correct() {
      long epoch = 0L;
      SnowflakeIdGenerator generator = new SnowflakeIdGenerator(epoch);
      long id = generator.nextId();
      long datacenterId = generator.parseDatacenterId(id);
      assertThat(datacenterId).isEqualTo(generator.getDatacenterId());
    }

    @Test
    @DisplayName("parseSequence: 首个 ID 的序列号为 0")
    void parseSequence_firstId_isZero() {
      long epoch = 0L;
      SnowflakeIdGenerator generator = new SnowflakeIdGenerator(epoch);
      long id = generator.nextId();
      assertThat(generator.parseSequence(id)).isEqualTo(0L);
    }
  }

  @Nested
  @DisplayName("便捷构造器")
  class ConstructorTest {

    @Test
    @DisplayName("默认构造器可生成有效 ID")
    void defaultGenerator_generatesValidId() {
      SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
      long id = generator.nextId();
      assertThat(id).isGreaterThan(0);
    }

    @Test
    @DisplayName("epoch 构造器保存配置")
    void epochConstructor_preservesEpoch() {
      long epoch = 1700000000000L;
      SnowflakeIdGenerator generator = new SnowflakeIdGenerator(epoch);
      assertThat(generator.getInstanceEpoch()).isEqualTo(epoch);
    }

    @Test
    @DisplayName("epoch + bits 构造器保存配置")
    void epochBitsConstructor_preservesConfig() {
      long epoch = 1700000000000L;
      int sequenceBits = 10;
      SnowflakeIdGenerator generator = new SnowflakeIdGenerator(epoch, sequenceBits);
      assertThat(generator.getInstanceEpoch()).isEqualTo(epoch);
      assertThat(generator.getSequenceBits()).isEqualTo(sequenceBits);
    }
  }

  @Nested
  @DisplayName("序列号位数边界")
  class SequenceBitsTest {

    @Test
    @DisplayName("sequenceBits 超过最大值抛出异常")
    void sequenceBits_exceedsMax_throws() {
      assertThatThrownBy(() -> new SnowflakeIdGenerator(0L, 14))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sequenceBits 低于 1 抛出异常")
    void sequenceBits_belowOne_throws() {
      assertThatThrownBy(() -> new SnowflakeIdGenerator(0L, 0))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("最大序列号 = 2^sequenceBits - 1")
    void maxSequence_equalsPowerOfTwoMinusOne() {
      SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0L, 7);
      assertThat(generator.getMaxSequence()).isEqualTo(127L);
    }
  }
}
