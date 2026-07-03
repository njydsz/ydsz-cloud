package com.njydsz.pmis.workflow.engine;

import com.github.benmanes.caffeine.cache.Ticker;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowSkipMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowDefinitionCacheService 单元测试
 *
 * <p>P1：验证流程定义元数据缓存的命中、主动失效（evict）、TTL 过期重载，
 * 以及节点/skip 便捷查询方法从缓存列表派生的正确性。
 *
 * <p>使用 Mockito mock 底层 Mapper，通过自定义 {@link Ticker} 模拟 TTL 过期，
 * 无需真实数据库与时间等待。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@ExtendWith(MockitoExtension.class)
class FlowDefinitionCacheServiceTest {

    @Mock
    private FlowNodeMapper flowNodeMapper;
    @Mock
    private FlowSkipMapper flowSkipMapper;

    private static final Long DEFINITION_ID = 200L;

    /** 可手动推进时间的 Ticker，用于测试 TTL 过期 */
    private static final class FakeTicker implements Ticker {
        private long nanos = 0L;

        @Override
        public long read() {
            return nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }

    // ==================== 缓存命中 ====================

    @Test
    @DisplayName("getNodeByCode - 第二次调用命中缓存，不重复查库")
    void getNodeByCodeShouldCacheAndNotQueryDbOnSecondCall() {
        FlowDefinitionCacheService cacheService = new FlowDefinitionCacheService(
                flowNodeMapper, flowSkipMapper);
        FlowNodeDO node = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);

        when(flowNodeMapper.selectByDefinitionId(DEFINITION_ID)).thenReturn(List.of(node));

        FlowNodeDO first = cacheService.getNodeByCode(DEFINITION_ID, "node1");
        FlowNodeDO second = cacheService.getNodeByCode(DEFINITION_ID, "node1");

        assertThat(first).isNotNull();
        assertThat(first.getNodeCode()).isEqualTo("node1");
        assertThat(second).isSameAs(first);
        // 仅首次加载查库一次
        verify(flowNodeMapper, times(1)).selectByDefinitionId(DEFINITION_ID);
    }

    @Test
    @DisplayName("getAllSkips - 第二次调用命中缓存，不重复查库")
    void getAllSkipsShouldCacheAndNotQueryDbOnSecondCall() {
        FlowDefinitionCacheService cacheService = new FlowDefinitionCacheService(
                flowNodeMapper, flowSkipMapper);
        FlowSkipDO skip = buildSkipWithSource("node1", "node2", "PASS");

        when(flowSkipMapper.selectByDefinitionId(DEFINITION_ID)).thenReturn(List.of(skip));

        List<FlowSkipDO> first = cacheService.getAllSkips(DEFINITION_ID);
        List<FlowSkipDO> second = cacheService.getAllSkips(DEFINITION_ID);

        assertThat(first).hasSize(1);
        assertThat(second).isSameAs(first);
        verify(flowSkipMapper, times(1)).selectByDefinitionId(DEFINITION_ID);
    }

    // ==================== evict 主动失效 ====================

    @Test
    @DisplayName("evict - 清除缓存后下一次访问重新查库")
    void evictShouldForceReloadOnNextAccess() {
        FlowDefinitionCacheService cacheService = new FlowDefinitionCacheService(
                flowNodeMapper, flowSkipMapper);
        FlowNodeDO node = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);

        when(flowNodeMapper.selectByDefinitionId(DEFINITION_ID)).thenReturn(List.of(node));

        cacheService.getAllNodes(DEFINITION_ID);
        cacheService.evict(DEFINITION_ID);
        cacheService.getAllNodes(DEFINITION_ID);

        // evict 后重新查库，共 2 次
        verify(flowNodeMapper, times(2)).selectByDefinitionId(DEFINITION_ID);
    }

    @Test
    @DisplayName("evict - 同时清除节点和 skip 两份缓存")
    void evictShouldInvalidateBothNodeAndSkipCache() {
        FlowDefinitionCacheService cacheService = new FlowDefinitionCacheService(
                flowNodeMapper, flowSkipMapper);
        FlowNodeDO node = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);
        FlowSkipDO skip = buildSkipWithSource("node1", "node2", "PASS");

        when(flowNodeMapper.selectByDefinitionId(DEFINITION_ID)).thenReturn(List.of(node));
        when(flowSkipMapper.selectByDefinitionId(DEFINITION_ID)).thenReturn(List.of(skip));

        // 首次加载：各查库 1 次
        cacheService.getAllNodes(DEFINITION_ID);
        cacheService.getAllSkips(DEFINITION_ID);
        // evict 后重新加载：各再查库 1 次
        cacheService.evict(DEFINITION_ID);
        cacheService.getAllNodes(DEFINITION_ID);
        cacheService.getAllSkips(DEFINITION_ID);

        verify(flowNodeMapper, times(2)).selectByDefinitionId(DEFINITION_ID);
        verify(flowSkipMapper, times(2)).selectByDefinitionId(DEFINITION_ID);
    }

    // ==================== TTL 过期 ====================

    @Test
    @DisplayName("TTL 过期 - 超过 30 分钟后访问重新查库")
    void ttlExpiryShouldForceReload() {
        FakeTicker ticker = new FakeTicker();
        FlowDefinitionCacheService cacheService = new FlowDefinitionCacheService(
                flowNodeMapper, flowSkipMapper, ticker);
        FlowNodeDO node = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);

        when(flowNodeMapper.selectByDefinitionId(DEFINITION_ID)).thenReturn(List.of(node));

        // 首次访问：查库 1 次
        cacheService.getAllNodes(DEFINITION_ID);
        verify(flowNodeMapper, times(1)).selectByDefinitionId(DEFINITION_ID);

        // 推进时间超过 TTL（30 分钟）
        ticker.advance(Duration.ofMinutes(31));
        // 再次访问：缓存已过期，重新查库
        cacheService.getAllNodes(DEFINITION_ID);

        verify(flowNodeMapper, times(2)).selectByDefinitionId(DEFINITION_ID);
    }

    @Test
    @DisplayName("TTL 未过期 - 30 分钟内访问命中缓存")
    void ttlNotExpiredShouldHitCache() {
        FakeTicker ticker = new FakeTicker();
        FlowDefinitionCacheService cacheService = new FlowDefinitionCacheService(
                flowNodeMapper, flowSkipMapper, ticker);
        FlowNodeDO node = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);

        when(flowNodeMapper.selectByDefinitionId(DEFINITION_ID)).thenReturn(List.of(node));

        cacheService.getAllNodes(DEFINITION_ID);
        // 推进 29 分钟（未过期）
        ticker.advance(Duration.ofMinutes(29));
        cacheService.getAllNodes(DEFINITION_ID);

        verify(flowNodeMapper, times(1)).selectByDefinitionId(DEFINITION_ID);
    }

    // ==================== 便捷查询方法 ====================

    @Test
    @DisplayName("getStartNode - 从缓存节点列表中筛选 nodeType=START")
    void getStartNodeShouldReturnStartNodeFromCache() {
        FlowDefinitionCacheService cacheService = new FlowDefinitionCacheService(
                flowNodeMapper, flowSkipMapper);
        FlowNodeDO start = buildNode("start", "发起", FlowNodeType.START);
        FlowNodeDO approval = buildNode("node1", "审批", FlowNodeType.APPROVAL);

        when(flowNodeMapper.selectByDefinitionId(DEFINITION_ID))
                .thenReturn(List.of(start, approval));

        FlowNodeDO result = cacheService.getStartNode(DEFINITION_ID);

        assertThat(result).isNotNull();
        assertThat(result.getNodeCode()).isEqualTo("start");
        assertThat(result.getNodeType()).isEqualTo(FlowNodeType.START.getCode());
        verify(flowNodeMapper, times(1)).selectByDefinitionId(DEFINITION_ID);
    }

    @Test
    @DisplayName("getSkipsByNextNode - 按 nextNodeCode 过滤缓存 skip 列表")
    void getSkipsByNextNodeShouldFilterByNextNodeCode() {
        FlowDefinitionCacheService cacheService = new FlowDefinitionCacheService(
                flowNodeMapper, flowSkipMapper);
        FlowSkipDO s1 = buildSkipWithSource("node1", "join1", "PASS");
        FlowSkipDO s2 = buildSkipWithSource("node2", "join1", "PASS");
        FlowSkipDO s3 = buildSkipWithSource("node3", "end", "PASS");

        when(flowSkipMapper.selectByDefinitionId(DEFINITION_ID))
                .thenReturn(List.of(s1, s2, s3));

        List<FlowSkipDO> result = cacheService.getSkipsByNextNode(DEFINITION_ID, "join1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FlowSkipDO::getNextNodeCode)
                .containsExactly("join1", "join1");
        verify(flowSkipMapper, times(1)).selectByDefinitionId(DEFINITION_ID);
    }

    @Test
    @DisplayName("getSkipsByNodeCode - 按 ext.sourceRef 过滤缓存 skip 列表")
    void getSkipsByNodeCodeShouldFilterBySourceRef() {
        FlowDefinitionCacheService cacheService = new FlowDefinitionCacheService(
                flowNodeMapper, flowSkipMapper);
        // node1 出发的两条出边
        FlowSkipDO out1 = buildSkipWithSource("node1", "node2", "PASS");
        FlowSkipDO out2 = buildSkipWithSource("node1", "node3", "PASS");
        // node2 出发的出边（不应被包含）
        FlowSkipDO out3 = buildSkipWithSource("node2", "node4", "PASS");

        when(flowSkipMapper.selectByDefinitionId(DEFINITION_ID))
                .thenReturn(List.of(out1, out2, out3));

        List<FlowSkipDO> result = cacheService.getSkipsByNodeCode(DEFINITION_ID, "node1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FlowSkipDO::getNextNodeCode)
                .containsExactlyInAnyOrder("node2", "node3");
        verify(flowSkipMapper, times(1)).selectByDefinitionId(DEFINITION_ID);
    }

    @Test
    @DisplayName("getNodeByCode - 节点不存在时返回 null")
    void getNodeByCodeShouldReturnNullWhenNotFound() {
        FlowDefinitionCacheService cacheService = new FlowDefinitionCacheService(
                flowNodeMapper, flowSkipMapper);
        FlowNodeDO node = buildNode("node1", "审批", FlowNodeType.APPROVAL);

        when(flowNodeMapper.selectByDefinitionId(DEFINITION_ID)).thenReturn(List.of(node));

        FlowNodeDO result = cacheService.getNodeByCode(DEFINITION_ID, "not_exist");

        assertThat(result).isNull();
    }

    // ==================== 辅助方法 ====================

    private FlowNodeDO buildNode(String code, String name, FlowNodeType type) {
        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode(code);
        node.setNodeName(name);
        node.setNodeType(type.getCode());
        node.setDefinitionId(DEFINITION_ID);
        return node;
    }

    private FlowSkipDO buildSkipWithSource(String sourceRef, String nextNodeCode, String skipType) {
        FlowSkipDO skip = new FlowSkipDO();
        skip.setNextNodeCode(nextNodeCode);
        skip.setSkipType(skipType);
        skip.setDefinitionId(DEFINITION_ID);
        skip.setExt("{\"sourceRef\":\"" + sourceRef + "\"}");
        return skip;
    }
}
