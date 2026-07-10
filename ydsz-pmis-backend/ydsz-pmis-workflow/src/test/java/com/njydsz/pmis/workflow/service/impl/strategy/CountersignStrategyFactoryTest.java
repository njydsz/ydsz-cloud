package com.njydsz.pmis.workflow.service.impl.strategy;

import com.njydsz.pmis.workflow.enums.definition.FlowPerformType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * CountersignStrategyFactory 单元测试
 *
 * <p>验证策略注册、按类型选取、未注册时回退到 OR 策略的行为。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("会签策略工厂测试")
class CountersignStrategyFactoryTest {

    @Mock
    private OrCountersignStrategy orStrategy;
    @Mock
    private ParallelCountersignStrategy parallelStrategy;
    @Mock
    private SequentialCountersignStrategy sequentialStrategy;
    @Mock
    private VoteCountersignStrategy voteStrategy;
    @Mock
    private WeightedVoteCountersignStrategy weightedVoteStrategy;
    @Mock
    private ForeachCountersignStrategy foreachStrategy;

    private CountersignStrategyFactory factory;

    @BeforeEach
    void setUp() {
        // Stub supportedType 行为
        org.mockito.Mockito.when(orStrategy.supportedType()).thenReturn(FlowPerformType.OR);
        org.mockito.Mockito.when(parallelStrategy.supportedType()).thenReturn(FlowPerformType.PARALLEL);
        org.mockito.Mockito.when(sequentialStrategy.supportedType()).thenReturn(FlowPerformType.SEQUENTIAL);
        org.mockito.Mockito.when(voteStrategy.supportedType()).thenReturn(FlowPerformType.VOTE);
        org.mockito.Mockito.when(weightedVoteStrategy.supportedType()).thenReturn(FlowPerformType.WEIGHTED_VOTE);
        org.mockito.Mockito.when(foreachStrategy.supportedType()).thenReturn(FlowPerformType.FOREACH_PARALLEL);

        factory = new CountersignStrategyFactory(List.of(
                orStrategy, parallelStrategy, sequentialStrategy,
                voteStrategy, weightedVoteStrategy, foreachStrategy
        ));
        factory.init();
    }

    @Test
    @DisplayName("按 performType 获取已注册策略-OR")
    void getStrategy_OR() {
        CountersignStrategy strategy = factory.getStrategy(FlowPerformType.OR);
        assertNotNull(strategy);
        assertSame(orStrategy, strategy);
    }

    @Test
    @DisplayName("按 performType 获取已注册策略-PARALLEL")
    void getStrategy_PARALLEL() {
        CountersignStrategy strategy = factory.getStrategy(FlowPerformType.PARALLEL);
        assertNotNull(strategy);
        assertSame(parallelStrategy, strategy);
    }

    @Test
    @DisplayName("按 performType 获取已注册策略-SEQUENTIAL")
    void getStrategy_SEQUENTIAL() {
        CountersignStrategy strategy = factory.getStrategy(FlowPerformType.SEQUENTIAL);
        assertNotNull(strategy);
        assertSame(sequentialStrategy, strategy);
    }

    @Test
    @DisplayName("按 performType 获取已注册策略-VOTE")
    void getStrategy_VOTE() {
        CountersignStrategy strategy = factory.getStrategy(FlowPerformType.VOTE);
        assertNotNull(strategy);
        assertSame(voteStrategy, strategy);
    }

    @Test
    @DisplayName("按 performType 获取已注册策略-WEIGHTED_VOTE")
    void getStrategy_WEIGHTED_VOTE() {
        CountersignStrategy strategy = factory.getStrategy(FlowPerformType.WEIGHTED_VOTE);
        assertNotNull(strategy);
        assertSame(weightedVoteStrategy, strategy);
    }

    @Test
    @DisplayName("按 performType 获取已注册策略-FOREACH_PARALLEL")
    void getStrategy_FOREACH_PARALLEL() {
        CountersignStrategy strategy = factory.getStrategy(FlowPerformType.FOREACH_PARALLEL);
        assertNotNull(strategy);
        assertSame(foreachStrategy, strategy);
    }

    @Test
    @DisplayName("performType 为 null 时回退到 OR 策略")
    void getStrategy_NullFallbackToOR() {
        CountersignStrategy strategy = factory.getStrategy(null);
        assertNotNull(strategy);
        assertSame(orStrategy, strategy);
    }

    @Test
    @DisplayName("策略总数等于 6 个枚举值")
    void registrySize() {
        // 应注册 6 个策略
        for (FlowPerformType type : FlowPerformType.values()) {
            CountersignStrategy strategy = factory.getStrategy(type);
            assertNotNull(strategy, "策略不应为 null: type=" + type);
        }
    }

    @Test
    @DisplayName("重复注册时新策略覆盖旧策略并发出告警")
    void registry_DuplicateOverwrite() {
        org.mockito.Mockito.when(orStrategy.supportedType()).thenReturn(FlowPerformType.OR);
        CountersignStrategy dupOrStrategy = org.mockito.Mockito.mock(OrCountersignStrategy.class);
        org.mockito.Mockito.when(dupOrStrategy.supportedType()).thenReturn(FlowPerformType.OR);

        CountersignStrategyFactory dupFactory = new CountersignStrategyFactory(List.of(orStrategy, dupOrStrategy));
        dupFactory.init();

        // 后注册的应覆盖先注册的
        assertSame(dupOrStrategy, dupFactory.getStrategy(FlowPerformType.OR));
        // 与原 factory 行为不同（应被覆盖）
        assertNotEquals(factory.getStrategy(FlowPerformType.OR), dupFactory.getStrategy(FlowPerformType.OR));
    }

    @Test
    @DisplayName("枚举值数量与策略数量一致")
    void enumValueConsistency() {
        assertEquals(FlowPerformType.values().length, 6);
    }
}
