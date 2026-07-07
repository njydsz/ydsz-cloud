package com.njydsz.pmis.common.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ShardingContext} 单元测试（P3-1）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>构造器参数校验（shardTotal &lt; 1 / shardIndex 越界）</li>
 *   <li>shardItems 为 null 时降级为空列表</li>
 *   <li>isSharding() 判定逻辑</li>
 *   <li>toString() 输出包含关键信息</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ShardingContext 分片上下文测试")
class ShardingContextTest {

    @Test
    @DisplayName("正常构造: 字段正确赋值")
    void construct_normal_allFieldsAssigned() {
        List<String> items = Arrays.asList("table1", "table2");

        ShardingContext ctx = new ShardingContext(4, 2, items, "job-key", "log-1");

        assertEquals(4, ctx.getShardTotal());
        assertEquals(2, ctx.getShardIndex());
        assertEquals(items, ctx.getShardItems());
        assertEquals("job-key", ctx.getJobKey());
        assertEquals("log-1", ctx.getLogId());
    }

    @Test
    @DisplayName("shardTotal < 1 抛异常")
    void construct_shardTotalLessThan1_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new ShardingContext(0, 0, Collections.emptyList(), "k", "l"));
    }

    @Test
    @DisplayName("shardIndex < 0 抛异常")
    void construct_negativeShardIndex_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new ShardingContext(2, -1, Collections.emptyList(), "k", "l"));
    }

    @Test
    @DisplayName("shardIndex >= shardTotal 抛异常")
    void construct_shardIndexOutOfBounds_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new ShardingContext(2, 2, Collections.emptyList(), "k", "l"));
    }

    @Test
    @DisplayName("shardItems 为 null 时降级为空列表")
    void construct_nullShardItems_fallbackToEmptyList() {
        ShardingContext ctx = new ShardingContext(2, 0, null, "k", "l");

        assertNotNull(ctx.getShardItems());
        assertTrue(ctx.getShardItems().isEmpty());
    }

    @Test
    @DisplayName("shardTotal=1 时 isSharding 返回 false")
    void isSharding_singleShard_returnsFalse() {
        ShardingContext ctx = new ShardingContext(1, 0, Collections.emptyList(), "k", "l");

        assertFalse(ctx.isSharding());
    }

    @Test
    @DisplayName("shardTotal>1 时 isSharding 返回 true")
    void isSharding_multipleShards_returnsTrue() {
        ShardingContext ctx = new ShardingContext(4, 1, Collections.emptyList(), "k", "l");

        assertTrue(ctx.isSharding());
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toString_containsKeyInfo() {
        ShardingContext ctx = new ShardingContext(4, 2,
                Arrays.asList("a", "b"), "myJob", "log-123");

        String str = ctx.toString();

        assertTrue(str.contains("shardTotal=4"), "应包含 shardTotal");
        assertTrue(str.contains("shardIndex=2"), "应包含 shardIndex");
        assertTrue(str.contains("jobKey='myJob'"), "应包含 jobKey");
        assertTrue(str.contains("logId='log-123'"), "应包含 logId");
        assertTrue(str.contains("2 items"), "应包含 shardItems 数量");
    }

    @Test
    @DisplayName("允许 jobKey 和 logId 为 null")
    void construct_nullJobKeyAndLogId_allowed() {
        ShardingContext ctx = new ShardingContext(2, 0, Collections.emptyList(), null, null);

        assertEquals(null, ctx.getJobKey());
        assertEquals(null, ctx.getLogId());
    }
}
