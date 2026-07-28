package com.njydsz.common.core.featureflag;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.env.Environment;

/**
 * 默认特性开关服务实现 — 基于内存 + Spring 配置文件
 *
 * <p>核心设计：
 * <ul>
 *   <li>线程安全：每个开关对应一个 {@link AtomicReference&lt;FlagState&gt;}，状态变更采用 CAS 替换</li>
 *   <li>强制开关：{@link FeatureFlag#isMandatory()} 为 true 的开关（如 SAFETY 类）始终返回 true，
 *       {@link #setEnabled} 对强制开关的禁用操作会被忽略并返回当前生效值</li>
 *   <li>灰度发布：当 {@code rolloutPercentage} 非 null 且 {@code userId} 非空时，使用
 *       {@code hash(flagName + userId) % 100 < rolloutPercentage} 判断该用户是否在灰度范围内；
 *       若 {@code userId} 为空则按全局 enabled 值返回</li>
 *   <li>初始配置：从 {@link FeatureFlagProperties} 加载初始状态，运行时可通过
 *       {@link #setEnabled} / {@link #setRolloutPercentage} 动态调整</li>
 *   <li>{@link #refresh()} 重新从 {@link FeatureFlagProperties} 加载，覆盖运行时修改</li>
 * </ul>
 *
 * <p>该实现是 {@link FeatureFlagAutoConfiguration} 默认注册的 Bean，当
 * {@link NacosFeatureFlagService} 可用时会被其替代（{@code @ConditionalOnMissingBean}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DefaultFeatureFlagService implements FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(DefaultFeatureFlagService.class);

    /** 模块总开关，关闭后所有非强制开关返回 false */
    protected volatile boolean moduleEnabled = true;

    /** 各开关的当前状态（线程安全，使用 ConcurrentHashMap 包装 EnumMap） */
    private final Map<FeatureFlag, AtomicReference<FlagState>> states = new ConcurrentHashMap<>(
            Collections.synchronizedMap(new EnumMap<>(FeatureFlag.class)));

    /** 配置属性（用于 refresh 时重新加载） */
    protected final FeatureFlagProperties properties;

    /** Spring Environment，用于回退读取 Nacos server-addr 等配置 */
    protected final Environment environment;

    public DefaultFeatureFlagService(FeatureFlagProperties properties, Environment environment) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.environment = environment;
        this.moduleEnabled = properties.isEnabled();
        loadFromProperties();
    }

    /**
     * 从 {@link FeatureFlagProperties} 加载初始状态到内存
     */
    protected void loadFromProperties() {
        for (FeatureFlag flag : FeatureFlag.values()) {
            FeatureFlagProperties.FlagConfig cfg = properties.getFlags() == null
                    ? null : properties.getFlags().get(flag.name());
            FlagState state = buildState(flag, cfg);
            states.put(flag, new AtomicReference<>(state));
        }
        if (log.isDebugEnabled()) {
            log.debug("[FeatureFlag] 已加载 {} 个特性开关配置", states.size());
        }
    }

    private FlagState buildState(FeatureFlag flag, FeatureFlagProperties.FlagConfig cfg) {
        Boolean configuredValue = cfg != null ? cfg.getEnabled() : null;
        Integer rollout = cfg != null ? cfg.getRollout() : null;
        return new FlagState(configuredValue, rollout, LocalDateTime.now());
    }

    @Override
    public boolean isEnabled(FeatureFlag flag, String userId) {
        Objects.requireNonNull(flag, "flag must not be null");
        // 强制开关始终启用
        if (flag.isMandatory()) {
            return true;
        }
        // 模块总开关关闭时，所有非强制开关返回 false
        if (!moduleEnabled) {
            return false;
        }
        FlagState state = currentState(flag);
        // 灰度发布：userId 非空且 rollout 已配置
        if (userId != null && !userId.isEmpty() && state.rolloutPercentage != null) {
            return isInRollout(flag, userId, state.rolloutPercentage);
        }
        return state.resolvedEnabled();
    }

    /**
     * 判断指定用户是否在灰度范围内
     *
     * <p>使用 {@code hash(flagName + userId) % 100 < percentage} 算法，确保：
     * <ul>
     *   <li>同一用户 + 同一开关的结果稳定（不会因刷新而变化）</li>
     *   <li>不同开关的灰度集合相互独立</li>
     *   <li>percentage=100 时全部命中，percentage=0 时全部不命中</li>
     * </ul>
     */
    protected boolean isInRollout(FeatureFlag flag, String userId, int percentage) {
        if (percentage <= 0) {
            return false;
        }
        if (percentage >= 100) {
            return true;
        }
        int hash = stableHash(flag.name() + ':' + userId);
        return Math.abs(hash % 100) < percentage;
    }

    /**
     * 稳定哈希函数（FNV-1a 32-bit）
     *
     * <p>选择 FNV-1a 而非 String.hashCode 的原因：
     * <ul>
     *   <li>FNV-1a 跨语言/跨实现一致（Java/Go/Python 结果相同），便于灰度集合可重现</li>
     *   <li>{@link String#hashCode()} 受 JVM 启用 String.hashCode 调整影响，存在历史值漂移</li>
     * </ul>
     */
    private static int stableHash(String s) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }

    @Override
    public List<FeatureFlagSnapshot> snapshot() {
        List<FeatureFlagSnapshot> result = new ArrayList<>(FeatureFlag.values().length);
        for (FeatureFlag flag : FeatureFlag.values()) {
            result.add(toSnapshot(flag, currentState(flag)));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Map<String, List<FeatureFlagSnapshot>> snapshotByCategory() {
        Map<String, List<FeatureFlagSnapshot>> grouped = new LinkedHashMap<>();
        for (FeatureFlag flag : FeatureFlag.values()) {
            FeatureFlagSnapshot snap = toSnapshot(flag, currentState(flag));
            grouped.computeIfAbsent(snap.getCategory().name(), k -> new ArrayList<>()).add(snap);
        }
        // 包装为不可变视图
        Map<String, List<FeatureFlagSnapshot>> immutable = new LinkedHashMap<>(grouped.size());
        grouped.forEach((k, v) -> immutable.put(k, Collections.unmodifiableList(v)));
        return Collections.unmodifiableMap(immutable);
    }

    private FeatureFlagSnapshot toSnapshot(FeatureFlag flag, FlagState state) {
        return FeatureFlagSnapshot.builder()
                .key(flag.name())
                .category(flag.getCategory())
                .description(flag.getDescription())
                .configuredValue(state.configuredValue)
                .effectiveValue(computeEffectiveValue(flag, state))
                .mandatory(flag.isMandatory())
                .rolloutPercentage(state.rolloutPercentage)
                .updatedAt(state.updatedAt)
                .build();
    }

    private boolean computeEffectiveValue(FeatureFlag flag, FlagState state) {
        if (flag.isMandatory()) {
            return true;
        }
        if (!moduleEnabled) {
            return false;
        }
        return state.resolvedEnabled();
    }

    @Override
    public boolean setEnabled(FeatureFlag flag, boolean enabled) {
        Objects.requireNonNull(flag, "flag must not be null");
        // 强制开关忽略禁用操作
        if (flag.isMandatory() && !enabled) {
            log.warn("[FeatureFlag] 拒绝禁用强制开关: {}", flag.name());
            return true;
        }
        AtomicReference<FlagState> ref = states.get(flag);
        if (ref == null) {
            log.warn("[FeatureFlag] 未注册的开关: {}", flag.name());
            return false;
        }
        boolean changed;
        FlagState old;
        FlagState next;
        do {
            old = ref.get();
            if (old.configuredValue != null && old.configuredValue == enabled) {
                return computeEffectiveValue(flag, old);
            }
            next = new FlagState(enabled, old.rolloutPercentage, LocalDateTime.now());
            changed = ref.compareAndSet(old, next);
        } while (!changed);
        log.info("[FeatureFlag] {} enabled: {} -> {}", flag.name(), old.configuredValue, enabled);
        return computeEffectiveValue(flag, next);
    }

    @Override
    public int setRolloutPercentage(FeatureFlag flag, int percentage) {
        Objects.requireNonNull(flag, "flag must not be null");
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException(
                    "rolloutPercentage must be in [0, 100], got: " + percentage);
        }
        AtomicReference<FlagState> ref = states.get(flag);
        if (ref == null) {
            log.warn("[FeatureFlag] 未注册的开关: {}", flag.name());
            return 0;
        }
        boolean changed;
        FlagState old;
        FlagState next;
        do {
            old = ref.get();
            Integer oldRollout = old.rolloutPercentage;
            if (oldRollout != null && oldRollout == percentage) {
                return 0;
            }
            next = new FlagState(old.configuredValue, percentage, LocalDateTime.now());
            changed = ref.compareAndSet(old, next);
        } while (!changed);
        log.info("[FeatureFlag] {} rollout: {} -> {}", flag.name(), old.rolloutPercentage, percentage);
        return 1;
    }

    @Override
    public void refresh() {
        loadFromProperties();
        log.info("[FeatureFlag] 已从配置属性重新加载 {} 个特性开关", states.size());
    }

    /**
     * 获取指定开关的当前状态（用于测试与监控）
     */
    protected FlagState currentState(FeatureFlag flag) {
        AtomicReference<FlagState> ref = states.get(flag);
        if (ref == null) {
            return FlagState.DEFAULT;
        }
        return ref.get();
    }

    /**
     * 不可变的开关状态快照
     */
    protected static final class FlagState {
        static final FlagState DEFAULT = new FlagState(null, null, null);

        /** 显式配置值，null 表示未配置（resolvedEnabled() 回退到 false） */
        final Boolean configuredValue;
        final Integer rolloutPercentage;
        final LocalDateTime updatedAt;

        FlagState(Boolean configuredValue, Integer rolloutPercentage, LocalDateTime updatedAt) {
            this.configuredValue = configuredValue;
            this.rolloutPercentage = rolloutPercentage;
            this.updatedAt = updatedAt;
        }

        /** 解析为有效布尔值：未配置时返回 false */
        boolean resolvedEnabled() {
            return configuredValue != null && configuredValue;
        }
    }
}
