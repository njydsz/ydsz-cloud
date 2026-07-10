package com.njydsz.pmis.workflow.service.impl.strategy;

import com.njydsz.pmis.workflow.enums.definition.FlowPerformType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 会签推进策略工厂
 *
 * <p>Spring 启动时扫描所有 {@link CountersignStrategy} Bean，按
 * {@link CountersignStrategy#supportedType()} 注册到 {@link EnumMap}。
 * 运行时按 {@link FlowPerformType} 选取策略，未注册时回退到 OR 策略。
 *
 * <p>新增会签类型时：
 * <ol>
 *   <li>在 {@code FlowPerformType} 枚举中添加新值</li>
 *   <li>实现 {@link CountersignStrategy} 接口并标注 {@code @Component}</li>
 *   <li>无需修改本类（自动注册）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CountersignStrategyFactory {

    /** Spring 容器注入的所有会签策略 Bean 列表，启动时遍历注册到 {@link #registry} */
    private final List<CountersignStrategy> strategies;

    /** 策略注册表：performType -> strategy */
    private final Map<FlowPerformType, CountersignStrategy> registry = new EnumMap<>(FlowPerformType.class);

    @PostConstruct
    public void init() {
        for (CountersignStrategy strategy : strategies) {
            FlowPerformType type = strategy.supportedType();
            CountersignStrategy prev = registry.put(type, strategy);
            if (prev != null) {
                log.warn("[Flow] 会签策略重复注册: type={} new={} old={}",
                        type, strategy.getClass().getSimpleName(), prev.getClass().getSimpleName());
            } else {
                log.info("[Flow] 会签策略已注册: type={} impl={}",
                        type, strategy.getClass().getSimpleName());
            }
        }
        // 检查必备策略是否齐全
        for (FlowPerformType type : FlowPerformType.values()) {
            if (!registry.containsKey(type)) {
                log.warn("[Flow] 会签策略缺失: type={}（将回退到 OR）", type);
            }
        }
    }

    /**
     * 按 performType 获取策略；未注册时回退到 OR。
     */
    public CountersignStrategy getStrategy(FlowPerformType performType) {
        if (performType == null) {
            return registry.get(FlowPerformType.OR);
        }
        CountersignStrategy strategy = registry.get(performType);
        if (strategy == null) {
            log.warn("[Flow] 会签策略未注册: type={}，回退到 OR", performType);
            return registry.get(FlowPerformType.OR);
        }
        return strategy;
    }
}
