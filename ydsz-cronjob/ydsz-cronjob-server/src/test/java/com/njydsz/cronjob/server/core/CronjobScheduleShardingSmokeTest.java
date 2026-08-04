package com.njydsz.cronjob.server.core;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.cronjob.domain.enums.JobTaskStatusEnum;
import com.njydsz.cronjob.server.core.scheduler.ScheduleType;
import com.njydsz.cronjob.server.core.sharding.AverageShardingStrategy;
import com.njydsz.cronjob.server.core.sharding.ShardAssignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 最小冒烟级单测，补充 P0 测试覆盖缺口。
 *
 * <p>测试 cronjob 核心计算逻辑：
 * <ul>
 *   <li>{@link ScheduleType#parse(String)} 调度类型解析 + 容错回退</li>
 *   <li>{@link LockKeyUtil#buildJobLockKey(String)} 锁 key 构造</li>
 *   <li>{@link AverageShardingStrategy#assign(int, List)} 分片轮询分配</li>
 *   <li>{@link JobTaskStatusEnum#parse} / isTerminal / canTransitTo</li>
 * </ul>
 *
 * <p>纯计算、无外部依赖（DB/Redis），直接构造实例调用。
 */
class CronjobScheduleShardingSmokeTest {

    @Nested
    @DisplayName("ScheduleType.parse - 调度类型解析")
    class ScheduleTypeTests {

        @Test
        @DisplayName("合法枚举值精准匹配")
        void validValues_parsedCorrectly() {
            assertThat(ScheduleType.parse("CRON")).isEqualTo(ScheduleType.CRON);
            assertThat(ScheduleType.parse("FIXED_RATE")).isEqualTo(ScheduleType.FIXED_RATE);
            assertThat(ScheduleType.parse("fixed_delay")).isEqualTo(ScheduleType.FIXED_DELAY);
            assertThat(ScheduleType.parse("api")).isEqualTo(ScheduleType.API);
        }

        @Test
        @DisplayName("null / 空 / 非法值回退到 CRON（向后兼容）")
        void invalidOrBlank_fallbackToCron() {
            assertThat(ScheduleType.parse(null)).isEqualTo(ScheduleType.CRON);
            assertThat(ScheduleType.parse("")).isEqualTo(ScheduleType.CRON);
            assertThat(ScheduleType.parse("   ")).isEqualTo(ScheduleType.CRON);
            assertThat(ScheduleType.parse("UNKNOWN")).isEqualTo(ScheduleType.CRON);
        }
    }

    @Nested
    @DisplayName("LockKeyUtil.buildJobLockKey - 锁 key 构造")
    class LockKeyUtilTests {

        @Test
        @DisplayName("非分片任务锁 key 格式正确")
        void nonShardLockKey_correctFormat() {
            String key = LockKeyUtil.buildJobLockKey("order-sync");
            assertThat(key).isEqualTo("ydsz:job:lock:order-sync");
        }

        @Test
        @DisplayName("分片任务锁 key 含分片后缀")
        void shardLockKey_containsShardSuffix() {
            String key = LockKeyUtil.buildJobLockKey("order-sync", 3);
            assertThat(key).isEqualTo("ydsz:job:lock:order-sync:shard:3");
        }

        @Test
        @DisplayName("分片索引为 null 或负数退化为普通锁 key")
        void nullOrNegativeShardIndex_degradesToSimpleKey() {
            assertThat(LockKeyUtil.buildJobLockKey("task-a", (Integer) null))
                    .isEqualTo("ydsz:job:lock:task-a");
            assertThat(LockKeyUtil.buildJobLockKey("task-a", -1))
                    .isEqualTo("ydsz:job:lock:task-a");
        }

        @Test
        @DisplayName("RELEASE_LOCK_SCRIPT 包含 CAS 逻辑")
        void releaseScript_containsCasLogic() {
            assertThat(LockKeyUtil.RELEASE_LOCK_SCRIPT)
                    .contains("redis.call('get'", "redis.call('del'");
        }
    }

    @Nested
    @DisplayName("AverageShardingStrategy.assign - 轮询分片")
    class AverageShardingTests {

        private final AverageShardingStrategy strategy = new AverageShardingStrategy();

        @Test
        @DisplayName("shardTotal=nodeCount，每节点恰好一个分片")
        void equalShardsAndNodes_eachNodeOneShard() {
            List<ShardAssignment> result = strategy.assign(2, List.of("node-a", "node-b"));
            assertThat(result).hasSize(2);
            assertThat(result.get(0).nodeId()).isEqualTo("node-a");
            assertThat(result.get(0).shardIndex()).isEqualTo(0);
            assertThat(result.get(1).nodeId()).isEqualTo("node-b");
            assertThat(result.get(1).shardIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("shardTotal > nodeCount，取模循环分配")
        void moreShardsThanNodes_roundRobin() {
            List<ShardAssignment> result = strategy.assign(4, List.of("node-a", "node-b"));
            assertThat(result).hasSize(4);
            assertThat(result.get(0).nodeId()).isEqualTo("node-a");
            assertThat(result.get(1).nodeId()).isEqualTo("node-b");
            assertThat(result.get(2).nodeId()).isEqualTo("node-a");
            assertThat(result.get(3).nodeId()).isEqualTo("node-b");
        }

        @Test
        @DisplayName("shardTotal < nodeCount，多余节点空闲")
        void fewerShardsThanNodes_someNodesIdle() {
            List<ShardAssignment> result = strategy.assign(2, List.of("node-a", "node-b", "node-c"));
            assertThat(result).hasSize(2);
            assertThat(result).extracting(ShardAssignment::nodeId)
                    .containsExactly("node-a", "node-b");
        }

        @Test
        @DisplayName("分片索引连续递增（0-based）")
        void shardIndex_sequential() {
            List<ShardAssignment> result = strategy.assign(5, List.of("node-a"));
            assertThat(result).extracting(ShardAssignment::shardIndex)
                    .containsExactly(0, 1, 2, 3, 4);
        }

        @Test
        @DisplayName("shardTotal < 1 抛出异常")
        void invalidShardTotal_throwsException() {
            assertThatThrownBy(() -> strategy.assign(0, List.of("node-a")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> strategy.assign(-1, List.of("node-a")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("空节点列表抛出异常")
        void emptyNodes_throwsException() {
            assertThatThrownBy(() -> strategy.assign(3, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> strategy.assign(3, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("返回的分配列表不可修改")
        void result_isUnmodifiable() {
            List<ShardAssignment> result = strategy.assign(2, List.of("node-a", "node-b"));
            assertThatThrownBy(() -> result.add(new ShardAssignment("node-x", 99)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("JobTaskStatusEnum - 子任务状态机")
    class JobTaskStatusEnumTests {

        @Test
        @DisplayName("parse 大小写不敏感，非法值返回 null")
        void parse_caseInsensitiveAndNullFallback() {
            assertThat(JobTaskStatusEnum.parse("running")).isEqualTo(JobTaskStatusEnum.RUNNING);
            assertThat(JobTaskStatusEnum.parse("PENDING")).isEqualTo(JobTaskStatusEnum.PENDING);
            assertThat(JobTaskStatusEnum.parse("unknown")).isNull();
            assertThat(JobTaskStatusEnum.parse(null)).isNull();
            assertThat(JobTaskStatusEnum.parse("")).isNull();
        }

        @Test
        @DisplayName("SUCCESS / FAILED 是终态")
        void isTerminal_correctlyIdentifiesTerminalStates() {
            assertThat(JobTaskStatusEnum.SUCCESS.isTerminal()).isTrue();
            assertThat(JobTaskStatusEnum.FAILED.isTerminal()).isTrue();
            assertThat(JobTaskStatusEnum.PENDING.isTerminal()).isFalse();
            assertThat(JobTaskStatusEnum.RUNNING.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("合法流转: PENDING → RUNNING → SUCCESS")
        void validTransition_pendingToRunningToSucceeds() {
            assertThat(JobTaskStatusEnum.PENDING.canTransitTo(JobTaskStatusEnum.RUNNING)).isTrue();
            assertThat(JobTaskStatusEnum.RUNNING.canTransitTo(JobTaskStatusEnum.SUCCESS)).isTrue();
        }

        @Test
        @DisplayName("非法流转: 终态不可再流转")
        void terminalState_cannotTransitFurther() {
            assertThat(JobTaskStatusEnum.SUCCESS.canTransitTo(JobTaskStatusEnum.RUNNING)).isFalse();
            assertThat(JobTaskStatusEnum.FAILED.canTransitTo(JobTaskStatusEnum.PENDING)).isFalse();
        }

        @Test
        @DisplayName("自流转为合法（保持原状态）")
        void selfTransition_isAllowed() {
            assertThatCode(() -> {
                for (JobTaskStatusEnum s : JobTaskStatusEnum.values()) {
                    assertThat(s.canTransitTo(s)).isTrue();
                }
            }).doesNotThrowAnyException();
        }
    }
}
