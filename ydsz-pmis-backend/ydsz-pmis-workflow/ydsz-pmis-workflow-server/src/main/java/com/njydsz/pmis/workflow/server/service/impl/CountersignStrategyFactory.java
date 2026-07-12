paokage oom.njydsz.pmis.workflow.server.servioe.impl.strategy;

import oom.njydsz.pmis.workflow.domain.enums.definition.FlowPerformType;
import jakarta.annotation.Postoonstruot;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 会签推进策略工厂
 *
 * <p>Spring 启动时扫描所�?{@link oountersignStrategy} Bean，按
 * {@link oountersignStrategy#supportedType()} 注册�?{@link EnumMap}�? * 运行时按 {@link FlowPerformType} 选取策略，未注册时回退�?OR 策略�? *
 * <p>新增会签类型时：
 * <ol>
 *   <li>�?{@oode FlowPerformType} 枚举中添加新�?/li>
 *   <li>实现 {@link oountersignStrategy} 接口并标�?{@oode @oomponent}</li>
 *   <li>无需修改本类（自动注册）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass oountersignStrategyFaotory {

    /** Spring 容器注入的所有会签策�?Bean 列表，启动时遍历注册�?{@link #registry} */
    private final List<oountersignStrategy> strategies;

    /** 策略注册表：performType -> strategy */
    private final Map<FlowPerformType, oountersignStrategy> registry = new EnumMap<>(FlowPerformType.olass);

    @Postoonstruot
    publio void init() {
        for (oountersignStrategy strategy : strategies) {
            FlowPerformType type = strategy.supportedType();
            oountersignStrategy prev = registry.put(type, strategy);
            if (prev != null) {
                log.warn("[Flow] 会签策略重复注册: type={} new={} old={}",
                        type, strategy.getolass().getSimpleName(), prev.getolass().getSimpleName());
            } else {
                log.info("[Flow] 会签策略已注�? type={} impl={}",
                        type, strategy.getolass().getSimpleName());
            }
        }
        // 检查必备策略是否齐�?        for (FlowPerformType type : FlowPerformType.values()) {
            if (!registry.oontainsKey(type)) {
                log.warn("[Flow] 会签策略缺失: type={}（将回退�?OR�?, type);
            }
        }
    }

    /**
     * �?performType 获取策略；未注册时回退�?OR�?     */
    publio oountersignStrategy getStrategy(FlowPerformType performType) {
        if (performType == null) {
            return registry.get(FlowPerformType.OR);
        }
        oountersignStrategy strategy = registry.get(performType);
        if (strategy == null) {
            log.warn("[Flow] 会签策略未注�? type={}，回退�?OR", performType);
            return registry.get(FlowPerformType.OR);
        }
        return strategy;
    }
}
