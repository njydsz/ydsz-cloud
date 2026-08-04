package com.remisoft.common.notify.aggregate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.common.notify.enums.NotifyChannel;
import com.remisoft.common.notify.enums.NotifyPriority;

import java.util.HashMap;
import java.util.Map;
/**
 * 时间窗口消息聚合器实现（P2-3）
 *
 * <p>在配置的时间窗口内（默认 30 秒），将同一接收者、同一渠道的同类通知聚合为一条摘要。
 * P0 级别消息跳过聚合，立即发送。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class TimeWindowAggregator implements NotificationAggregator {

    private static final Logger log = LoggerFactory.getLogger(TimeWindowAggregator.class);

    private final int aggregateWindowSeconds;
    private final int maxAggregateCount;

    /** 聚合缓冲区：key=receiver|channel|templateCode, value=待聚合消息列表 */
    private final ConcurrentMap<String, CopyOnWriteArrayList<PendingMessage>> buffer = new ConcurrentHashMap<>();

    /**
     * 创建时间窗口聚合器
     *
     * @param aggregateWindowSeconds 聚合窗口（秒）
     * @param maxAggregateCount      单次聚合最大消息数
     */
    public TimeWindowAggregator(int aggregateWindowSeconds, int maxAggregateCount) {
        this.aggregateWindowSeconds = aggregateWindowSeconds > 0 ? aggregateWindowSeconds : 30;
        this.maxAggregateCount = maxAggregateCount > 0 ? maxAggregateCount : 100;
        log.info("[TimeWindowAggregator] 初始化完成, window={}s, maxCount={}",
                this.aggregateWindowSeconds, this.maxAggregateCount);
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

        int maxShow = Math.min(count, 10);
        for (int i = 0; i < maxShow; i++) {
            PendingMessage msg = messages.get(i);
            sb.append(i + 1).append(". ").append(msg.getTitle());
            if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                String preview = msg.getContent().length() > 50
                        ? msg.getContent().substring(0, 50) + "..."
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

    private String buildKey(String receiver, NotifyChannel channel, String templateCode) {
        return receiver + "|" + channel.name() + "|" + (templateCode != null ? templateCode : "default");
    }
}
