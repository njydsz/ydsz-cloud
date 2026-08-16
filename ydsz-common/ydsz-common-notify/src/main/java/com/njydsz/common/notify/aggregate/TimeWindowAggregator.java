package com.njydsz.common.notify.aggregate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.enums.NotifyPriority;
/**
 * 时间窗口消息聚合器实现（P2-3）
 *
 * <p>在配置的时间窗口内（默认 30 秒），按聚合粒度将通知聚合为一条摘要。
 * P0 级别消息跳过聚合，立即发送。
 *
 * <p>聚合粒度由 {@link AggregationLevel} 控制：
 * <ul>
 *   <li>{@link AggregationLevel#BY_RECEIVER_CHANNEL_TEMPLATE} — 接收者+渠道+模板（最细）</li>
 *   <li>{@link AggregationLevel#BY_RECEIVER_CHANNEL} — 接收者+渠道（中等）</li>
 *   <li>{@link AggregationLevel#BY_RECEIVER} — 仅接收者（最粗）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TimeWindowAggregator implements NotificationAggregator {

    private static final Logger log = LoggerFactory.getLogger(TimeWindowAggregator.class);

    /** 聚合粒度枚举 */
    public enum AggregationLevel {
        /** 按接收者+渠道+模板编码聚合（最细粒度，默认） */
        BY_RECEIVER_CHANNEL_TEMPLATE,
        /** 按接收者+渠道聚合（中等粒度，忽略模板差异） */
        BY_RECEIVER_CHANNEL,
        /** 仅按接收者聚合（最粗粒度，跨渠道汇总） */
        BY_RECEIVER
    }

    private static final int DEFAULT_WINDOW_SECONDS = 30;
    private static final int DEFAULT_MAX_COUNT = 100;
    private static final int MAX_CONTENT_PREVIEW = 10;
    private static final int CONTENT_TRUNCATE_LENGTH = 50;
    private static final String KEY_SEPARATOR = "|";

    private final int aggregateWindowSeconds;
    private final int maxAggregateCount;
    private final AggregationLevel aggregationLevel;

    /** 聚合缓冲区：key=receiver|channel|templateCode, value=待聚合消息列表 */
    private final ConcurrentMap<String, CopyOnWriteArrayList<PendingMessage>> buffer = new ConcurrentHashMap<>();

    /**
     * 创建时间窗口聚合器（默认粒度：接收者+渠道+模板）
     *
     * @param aggregateWindowSeconds 聚合窗口（秒）
     * @param maxAggregateCount      单次聚合最大消息数
     */
    public TimeWindowAggregator(int aggregateWindowSeconds, int maxAggregateCount) {
        this(aggregateWindowSeconds, maxAggregateCount, AggregationLevel.BY_RECEIVER_CHANNEL_TEMPLATE);
    }

    /**
     * 创建时间窗口聚合器（指定聚合粒度）
     *
     * @param aggregateWindowSeconds 聚合窗口（秒）
     * @param maxAggregateCount      单次聚合最大消息数
     * @param aggregationLevel       聚合粒度
     */
    public TimeWindowAggregator(int aggregateWindowSeconds, int maxAggregateCount,
                                AggregationLevel aggregationLevel) {
        this.aggregateWindowSeconds = aggregateWindowSeconds > 0 ? aggregateWindowSeconds : DEFAULT_WINDOW_SECONDS;
        this.maxAggregateCount = maxAggregateCount > 0 ? maxAggregateCount : DEFAULT_MAX_COUNT;
        this.aggregationLevel = aggregationLevel != null ? aggregationLevel
                : AggregationLevel.BY_RECEIVER_CHANNEL_TEMPLATE;
        log.info("[TimeWindowAggregator] 初始化完成, window={}s, maxCount={}, level={}",
                this.aggregateWindowSeconds, this.maxAggregateCount, this.aggregationLevel);
    }

    /**
     * 添加消息到聚合缓冲区
     *
     * @param receiver     接收者
     * @param channel      通知渠道
     * @param templateCode 模板编码
     * @param title        标题
     * @param content      内容
     * @param priority     优先级
     * @return true 表示消息已加入缓冲区（等待聚合），false 表示应立即发送
     */
    public boolean offer(String receiver, NotifyChannel channel, String templateCode,
                         String title, String content, NotifyPriority priority) {
        // P0 紧急消息跳过聚合
        if (priority == NotifyPriority.P0_CRITICAL) {
            return false;
        }
        if (!shouldAggregate(receiver, channel, templateCode)) {
            return false;
        }

        String key = buildKey(receiver, channel, templateCode);
        PendingMessage message = new PendingMessage(receiver, title, content, System.currentTimeMillis());

        CopyOnWriteArrayList<PendingMessage> list = buffer.computeIfAbsent(key,
                k -> new CopyOnWriteArrayList<>());

        // 超过最大聚合数时触发即时 flush
        if (list.size() >= maxAggregateCount) {
            log.debug("[TimeWindowAggregator] 缓冲区已满，触发即时聚合: key={}, count={}", key, list.size());
            return false;
        }

        list.add(message);
        log.debug("[TimeWindowAggregator] 消息加入聚合缓冲区: key={}, count={}", key, list.size());
        return true;
    }

    /**
     * 刷新指定 key 的聚合消息
     *
     * @param receiver     接收者
     * @param channel      通知渠道
     * @param templateCode 模板编码
     * @return 聚合后的消息，无待聚合消息时返回 null
     */
    public AggregatedMessage flush(String receiver, NotifyChannel channel, String templateCode) {
        String key = buildKey(receiver, channel, templateCode);
        CopyOnWriteArrayList<PendingMessage> list = buffer.remove(key);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return aggregate(new ArrayList<>(list));
    }

    /**
     * 刷新所有待聚合消息
     *
     * @return key 到聚合消息的映射
     */
    public Map<String, AggregatedMessage> flushAll() {
        Map<String, AggregatedMessage> result = new HashMap<>();
        for (String key : new ArrayList<>(buffer.keySet())) {
            CopyOnWriteArrayList<PendingMessage> list = buffer.remove(key);
            if (list != null && !list.isEmpty()) {
                result.put(key, aggregate(new ArrayList<>(list)));
            }
        }
        return result;
    }

    /**
     * 获取缓冲区大小
     *
     * @return 待聚合消息总数
     */
    public int getBufferSize() {
        return buffer.values().stream().mapToInt(List::size).sum();
    }

    @Override
    public boolean shouldAggregate(String receiver, NotifyChannel channel, String templateCode) {
        return receiver != null && !receiver.isEmpty() && channel != null;
    }

    @Override
    public AggregatedMessage aggregate(List<PendingMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        int count = messages.size();
        String firstTitle = messages.get(0).getTitle();

        StringBuilder sb = new StringBuilder();
        sb.append("您有 ").append(count).append(" 条通知汇总：\n\n");

        int maxShow = Math.min(count, MAX_CONTENT_PREVIEW);
        for (int i = 0; i < maxShow; i++) {
            PendingMessage msg = messages.get(i);
            sb.append(i + 1).append(". ").append(msg.getTitle());
            if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                String preview = msg.getContent().length() > CONTENT_TRUNCATE_LENGTH
                        ? msg.getContent().substring(0, CONTENT_TRUNCATE_LENGTH) + "..."
                        : msg.getContent();
                sb.append(": ").append(preview);
            }
            sb.append("\n");
        }

        if (count > maxShow) {
            sb.append("\n...还有 ").append(count - maxShow).append(" 条通知未显示");
        }

        return new AggregatedMessage(firstTitle + "（" + count + "条汇总）", sb.toString(), count);
    }

    @Override
    public int getAggregateWindowSeconds() {
        return aggregateWindowSeconds;
    }

    /**
     * 根据聚合粒度构建缓冲区 key
     *
     * @param receiver     接收者
     * @param channel      通知渠道
     * @param templateCode 模板编码
     * @return 聚合 key
     */
    private String buildKey(String receiver, NotifyChannel channel, String templateCode) {
        String effectiveTemplate = templateCode != null ? templateCode : "default";
        return switch (aggregationLevel) {
            case BY_RECEIVER_CHANNEL_TEMPLATE ->
                    receiver + KEY_SEPARATOR + channel.name() + KEY_SEPARATOR + effectiveTemplate;
            case BY_RECEIVER_CHANNEL ->
                    receiver + KEY_SEPARATOR + channel.name();
            case BY_RECEIVER ->
                    receiver;
        };
    }
}
