package com.njydsz.agent.server.channel;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.channel.ChannelAdapter;
import com.njydsz.agent.domain.channel.ChannelStatus;
import com.njydsz.agent.domain.channel.ChannelType;

/**
 * 渠道适配器注册表。
 *
 * <p>管理所有渠道适配器，负责注册、查找和状态监控。
 * 实现渠道间的错误隔离，单个渠道异常不影响其他渠道。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
@Slf4j
public class ChannelAdapterRegistry {

    private final Map<ChannelType, ChannelAdapter> adapters = new ConcurrentHashMap<>();

    /**
     * 注册渠道适配器。
     *
     * @param adapter 渠道适配器
     */
    public void register(ChannelAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter 不能为 null");
        ChannelType type = adapter.getChannelType();
        adapters.put(type, adapter);
        log.info("[ChannelRegistry] 注册渠道适配器: type={}", type);
    }

    /**
     * 获取渠道适配器。
     *
     * @param type 渠道类型
     * @return 渠道适配器
     */
    public Optional<ChannelAdapter> getAdapter(ChannelType type) {
        return Optional.ofNullable(adapters.get(type));
    }

    /**
     * 检查渠道是否可用。
     *
     * @param type 渠道类型
     * @return 是否可用
     */
    public boolean isAvailable(ChannelType type) {
        return getAdapter(type)
                .map(ChannelAdapter::isHealthy)
                .orElse(false);
    }

    /**
     * 获取所有已注册的渠道类型。
     *
     * @return 渠道类型列表
     */
    public List<ChannelType> getRegisteredTypes() {
        return List.copyOf(adapters.keySet());
    }

    /**
     * 获取所有渠道状态。
     *
     * @return 渠道状态列表
     */
    public List<ChannelStatus> getAllStatuses() {
        return adapters.values().stream()
                .map(ChannelAdapter::getStatus)
                .toList();
    }

    /**
     * 获取健康的渠道列表。
     *
     * @return 健康渠道状态列表
     */
    public List<ChannelStatus> getHealthyChannels() {
        return adapters.values().stream()
                .filter(ChannelAdapter::isHealthy)
                .map(ChannelAdapter::getStatus)
                .toList();
    }
}
