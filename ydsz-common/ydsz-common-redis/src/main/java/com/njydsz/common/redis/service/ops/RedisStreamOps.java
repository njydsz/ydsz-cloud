package com.njydsz.common.redis.service.ops;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.json.YdszJson;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis Stream 操作组件
 *
 * <p>提供 Stream 数据结构的完整操作接口，包括：
 * <ul>
 *   <li>消息添加（add）</li>
 *   <li>消费者组管理（createGroup、deleteGroup）</li>
 *   <li>消费者组读取（readGroup）</li>
 *   <li>消息确认（ack）</li>
 *   <li>待处理消息查询（pendingInfo）</li>
 *   <li>死信队列（XCLAIM 超时未确认的消息）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RedisStreamOps {

    private static final String DEAD_LETTER_SUFFIX = ":deadletter";

    private final RedisTemplate<String, Object> redisTemplate;
    private final String keyPrefix;

    public RedisStreamOps(RedisTemplate<String, Object> redisTemplate, RedisProperties redisProperties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "RedisTemplate 不能为 null");
        this.keyPrefix = redisProperties != null ? (redisProperties.getKeyPrefix() != null ? redisProperties.getKeyPrefix() : "") : "";
    }

    /**
     * 格式化 Key，添加统一前缀
     */
    private String formatKey(String key) {
        if (key == null || keyPrefix.isEmpty()) {
            return key;
        }
        return keyPrefix + ":" + key;
    }

    // ============================ 消息添加 =============================

    /**
     * 向 Stream 添加消息
     *
     * @param streamKey Stream 键名
     * @param message   消息内容（键值对）
     * @return 消息 ID
     */
    public String add(String streamKey, Map<String, Object> message) {
        if (streamKey == null || streamKey.isEmpty()) {
            log.warn("【Redis】XADD 操作失败：Stream 键名不能为空");
            return null;
        }
        if (message == null || message.isEmpty()) {
            log.warn("【Redis】XADD 操作失败：消息内容不能为空");
            return null;
        }
        String formattedKey = formatKey(streamKey);
        try {
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .in(formattedKey)
                    .ofMap(convertToStringMap(message));
            RecordId recordId = ops().add(record);
            return recordId != null ? recordId.getValue() : null;
        } catch (Exception e) {
            log.error("【Redis】XADD 操作失败 | streamKey={} | error={}", streamKey, e);
            return null;
        }
    }

    /**
     * 向 Stream 添加消息（带最大长度限制）
     *
     * @param streamKey    Stream 键名
     * @param message      消息内容
     * @param maxMaxLength 最大长度（近似裁剪）
     * @return 消息 ID
     */
    public String add(String streamKey, Map<String, Object> message, long maxMaxLength) {
        if (streamKey == null || streamKey.isEmpty()) {
            log.warn("【Redis】XADD 操作失败：Stream 键名不能为空");
            return null;
        }
        if (message == null || message.isEmpty()) {
            log.warn("【Redis】XADD 操作失败：消息内容不能为空");
            return null;
        }
        String formattedKey = formatKey(streamKey);
        try {
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .in(formattedKey)
                    .ofMap(convertToStringMap(message));
            RecordId recordId = ops().add(record, RedisStreamCommands.XAddOptions.trim(TrimOptions.maxLen(maxMaxLength).approximate()));
            return recordId != null ? recordId.getValue() : null;
        } catch (Exception e) {
            log.error("【Redis】XADD 操作失败 | streamKey={} | maxlen={} | error={}", streamKey, maxMaxLength, e);
            return null;
        }
    }

    // ============================ 消费者组管理 =============================

    /**
     * 创建消费者组
     *
     * @param streamKey   Stream 键名
     * @param groupName   消费者组名
     * @param readOffset  起始偏移量，如 "0"（从头开始）或 "$"（仅新消息）
     * @return true-创建成功
     */
    public boolean createGroup(String streamKey, String groupName, String readOffset) {
        if (streamKey == null || streamKey.isEmpty() || groupName == null || groupName.isEmpty()) {
            log.warn("【Redis】XGROUP CREATE 操作失败：参数不能为空");
            return false;
        }
        String formattedKey = formatKey(streamKey);
        try {
            ReadOffset offset = ReadOffset.from(readOffset != null ? readOffset : "$");
            ops().createGroup(formattedKey, offset, groupName);
            log.info("【Redis】创建消费者组成功 | streamKey={} | groupName={} | offset={}", streamKey, groupName, readOffset);
            return true;
        } catch (Exception e) {
            log.error("【Redis】XGROUP CREATE 操作失败 | streamKey={} | groupName={} | error={}", streamKey, groupName, e);
            return false;
        }
    }

    /**
     * 创建消费者组（从最新消息开始消费）
     *
     * @param streamKey Stream 键名
     * @param groupName 消费者组名
     * @return true-创建成功
     */
    public boolean createGroup(String streamKey, String groupName) {
        return createGroup(streamKey, groupName, "$");
    }

    /**
     * 删除消费者组
     *
     * @param streamKey Stream 键名
     * @param groupName 消费者组名
     * @return true-删除成功
     */
    public boolean deleteGroup(String streamKey, String groupName) {
        if (streamKey == null || streamKey.isEmpty() || groupName == null || groupName.isEmpty()) {
            log.warn("【Redis】XGROUP DESTROY 操作失败：参数不能为空");
            return false;
        }
        String formattedKey = formatKey(streamKey);
        try {
            ops().destroyGroup(formattedKey, groupName);
            log.info("【Redis】删除消费者组成功 | streamKey={} | groupName={}", streamKey, groupName);
            return true;
        } catch (Exception e) {
            log.error("【Redis】XGROUP DESTROY 操作失败 | streamKey={} | groupName={} | error={}", streamKey, groupName, e);
            return false;
        }
    }

    // ============================ 消费者组读取 =============================

    /**
     * 从消费者组读取未确认的消息
     *
     * @param streamKey    Stream 键名
     * @param groupName    消费者组名
     * @param consumerName 消费者名
     * @param count        读取数量
     * @return 消息列表
     */
    public List<StreamMessage> readGroup(String streamKey, String groupName, String consumerName, int count) {
        return readGroup(streamKey, groupName, consumerName, count, false);
    }

    /**
     * 从消费者组读取消息（支持指定读取模式）
     *
     * @param streamKey    Stream 键名
     * @param groupName    消费者组名
     * @param consumerName 消费者名
     * @param count        读取数量（0 表示仅读 pending 不拉新消息）
     * @param readPending  true 表示读取 pending 列表中该消费者的未确认消息（offset=0），
     *                     false 表示读取新消息（offset=>）
     * @return 消息列表
     */
    public List<StreamMessage> readGroup(String streamKey, String groupName,
                                         String consumerName, int count, boolean readPending) {
        if (streamKey == null || groupName == null || consumerName == null) {
            log.warn("【Redis】XREADGROUP 操作失败：参数不能为空");
            return Collections.emptyList();
        }
        String formattedKey = formatKey(streamKey);
        try {
            Consumer consumer = Consumer.from(groupName, consumerName);
            StreamReadOptions readOptions = StreamReadOptions.empty().count(Math.max(count, 1));
            // readPending=true 时使用 "0" 读取已投递但未确认的消息
            // readPending=false 时使用 ">" 读取新消息
            ReadOffset offset = readPending ? ReadOffset.from("0") : ReadOffset.lastConsumed();
            StreamOffset<String> streamOffset = StreamOffset.create(formattedKey, offset);
            List<MapRecord<String, Object, Object>> records =
                    ops().read(consumer, readOptions, streamOffset);
            return convertRecords(records, streamKey);
        } catch (Exception e) {
            log.error("【Redis】XREADGROUP 操作失败 | streamKey={} | groupName={} | consumerName={} | readPending={} | error={}",
                    streamKey, groupName, consumerName, readPending, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从消费者组读取消息（背压感知模式）
     *
     * <p>根据当前消费者组的 pending 消息积压量动态调整实际拉取数量：
     * <ul>
     *   <li>pending 超过 highWatermark → 拉取量减半（限流保护）</li>
     *   <li>pending 低于 lowWatermark → 拉取量逐步恢复至 maxBatch</li>
     *   <li>pending 介于两者之间 → 保持上次拉取量</li>
     * </ul>
     *
     * @param streamKey    Stream 键名
     * @param groupName    消费者组名
     * @param consumerName 消费者名
     * @param maxBatch     最大拉取量
     * @param lowWatermark 低水位线（pending 低于此值时增大批次）
     * @param highWatermark 高水位线（pending 超过此值时减大批次）
     * @param lastBatchRef  上次的批次大小（用于渐进调整），传入 0 则使用 min(maxBatch, 10)
     * @return 背压感知的读取结果
     */
    public BackpressureResult readGroupWithBackpressure(String streamKey, String groupName,
                                                        String consumerName, int maxBatch,
                                                        int lowWatermark, int highWatermark,
                                                        int lastBatchRef) {
        // 查询当前 pending 数量
        long pendingCount = 0;
        try {
            PendingMessagesSummary summary = pendingInfo(streamKey, groupName);
            if (summary != null) {
                pendingCount = summary.getTotalPendingMessages();
            }
        } catch (Exception e) {
            log.debug("【Redis】背压检测 pending 查询失败 | streamKey={}", streamKey, e);
        }

        // 计算实际拉取量
        int actualBatch;
        if (lastBatchRef <= 0) {
            lastBatchRef = Math.min(maxBatch, 10);
        }

        if (pendingCount > highWatermark) {
            // 积压严重，缩小拉取量（至少 1）
            actualBatch = Math.max(lastBatchRef / 2, 1);
        } else if (pendingCount < lowWatermark) {
            // 积压少，渐进增大（步长为 maxBatch/10，不超过 maxBatch）
            int step = Math.max(maxBatch / 10, 1);
            actualBatch = Math.min(lastBatchRef + step, maxBatch);
        } else {
            // 中等积压，保持
            actualBatch = lastBatchRef;
        }

        // 先读 pending 未确认，再读新消息
        List<StreamMessage> pending = readGroup(streamKey, groupName, consumerName, 0, true);
        if (pending.size() >= actualBatch) {
            return new BackpressureResult(pending.subList(0, actualBatch), actualBatch, pendingCount);
        }

        int remainingForNew = actualBatch - pending.size();
        List<StreamMessage> newMessages = readGroup(streamKey, groupName, consumerName, remainingForNew, false);
        List<StreamMessage> combined = new ArrayList<>(pending.size() + newMessages.size());
        combined.addAll(pending);
        combined.addAll(newMessages);

        return new BackpressureResult(combined, actualBatch, pendingCount);
    }

    /**
     * 从消费者组读取未确认的消息（默认读取 1 条）
     *
     * @param streamKey    Stream 键名
     * @param groupName    消费者组名
     * @param consumerName 消费者名
     * @return 消息列表
     */
    public List<StreamMessage> readGroup(String streamKey, String groupName, String consumerName) {
        return readGroup(streamKey, groupName, consumerName, 1);
    }

    // ============================ 消息确认 =============================

    /**
     * 确认消息已处理
     *
     * @param streamKey  Stream 键名
     * @param groupName  消费者组名
     * @param recordIds  消息 ID 数组
     * @return 确认的消息数量
     */
    public long ack(String streamKey, String groupName, String... recordIds) {
        if (streamKey == null || groupName == null || recordIds == null || recordIds.length == 0) {
            log.warn("【Redis】XACK 操作失败：参数不能为空");
            return 0;
        }
        String formattedKey = formatKey(streamKey);
        try {
            RecordId[] ids = Arrays.stream(recordIds)
                    .map(RecordId::of)
                    .toArray(RecordId[]::new);
            Long acknowledged = ops().acknowledge(formattedKey, groupName, ids);
            return acknowledged != null ? acknowledged : 0;
        } catch (Exception e) {
            log.error("【Redis】XACK 操作失败 | streamKey={} | groupName={} | recordIds={} | error={}",
                    streamKey, groupName, Arrays.toString(recordIds), e);
            return 0;
        }
    }

    // ============================ 待处理消息查询 =============================

    /**
     * 获取待处理消息信息
     *
     * @param streamKey Stream 键名
     * @param groupName 消费者组名
     * @return 待处理消息摘要信息
     */
    public PendingMessagesSummary pendingInfo(String streamKey, String groupName) {
        if (streamKey == null || groupName == null) {
            log.warn("【Redis】XPENDING 操作失败：参数不能为空");
            return null;
        }
        String formattedKey = formatKey(streamKey);
        try {
            return ops().pending(formattedKey, groupName);
        } catch (Exception e) {
            log.error("【Redis】XPENDING 操作失败 | streamKey={} | groupName={} | error={}", streamKey, groupName, e);
            return null;
        }
    }

    /**
     * 获取待处理消息详情列表
     *
     * @param streamKey    Stream 键名
     * @param groupName    消费者组名
     * @param consumerName 消费者名（可选，null 表示查询所有消费者）
     * @param count        最大返回数量
     * @return 待处理消息列表
     */
    public PendingMessages pendingMessages(String streamKey, String groupName, String consumerName, int count) {
        if (streamKey == null || groupName == null) {
            log.warn("【Redis】XPENDING 操作失败：参数不能为空");
            return null;
        }
        String formattedKey = formatKey(streamKey);
        try {
            if (consumerName != null) {
                Consumer consumer = Consumer.from(groupName, consumerName);
                return ops().pending(formattedKey, consumer, Range.unbounded(), count);
            } else {
                return ops().pending(formattedKey, groupName, Range.unbounded(), count);
            }
        } catch (Exception e) {
            log.error("【Redis】XPENDING 操作失败 | streamKey={} | groupName={} | consumerName={} | error={}",
                    streamKey, groupName, consumerName, e);
            return null;
        }
    }

    // ============================ 死信队列（XCLAIM） =============================

    /**
     * 原子操作 Lua 脚本：将消息写入死信队列并确认原消息
     *
     * <p>通过 Lua 脚本保证 XADD（写入死信队列）和 XACK（确认原消息）的原子性，
     * 避免中间失败导致消息丢失或重复。
     *
     * <p>参数说明：
     * KEYS[1] = 死信队列键名
     * KEYS[2] = 原始 Stream 键名
     * ARGV[1] = 消费者组名
     * ARGV[2] = 消息 ID
     * ARGV[3..] = 交替的 field-value 对（用于 XADD）
     */
    private static final String DEAD_LETTER_ATOMIC_SCRIPT =
            "local deadLetterKey = KEYS[1]\n" +
            "local streamKey = KEYS[2]\n" +
            "local groupName = ARGV[1]\n" +
            "local recordId = ARGV[2]\n" +
            "local fields = {}\n" +
            "for i = 3, #ARGV do\n" +
            "    fields[i - 2] = ARGV[i]\n" +
            "end\n" +
            "redis.call('XADD', deadLetterKey, '*', fields)\n" +
            "redis.call('XACK', streamKey, groupName, recordId)\n" +
            "return 1";

    /**
     * 认领超时未确认的消息（XCLAIM），将其转移到死信队列
     *
     * <p>当消息在指定时间内未被确认（ACK），将其从原消费者转移到指定消费者，
     * 并使用 Lua 脚本原子性地写入死信队列并确认原消息（XACK + XADD 合并为原子操作）。
     *
     * @param streamKey          Stream 键名
     * @param groupName          消费者组名
     * @param deadLetterConsumer 死信消费者名
     * @param minIdleTimeMs      最小空闲时间（毫秒），超过此时间的未确认消息将被认领
     * @param count              最大认领数量
     * @return 认领的消息数量
     */
    public long claimDeadLetters(String streamKey, String groupName, String deadLetterConsumer,
                                  long minIdleTimeMs, int count) {
        if (streamKey == null || groupName == null || deadLetterConsumer == null) {
            log.warn("【Redis】XCLAIM 操作失败：参数不能为空");
            return 0;
        }
        String formattedKey = formatKey(streamKey);
        try {
            PendingMessages pending = pendingMessages(streamKey, groupName, null, count);
            if (pending == null || pending.isEmpty()) {
                return 0;
            }

            List<RecordId> idleRecordIds = pending.stream()
                    .filter(pm -> pm.getElapsedTimeSinceLastDelivery().toMillis() >= minIdleTimeMs)
                    .map(PendingMessage::getId)
                    .collect(Collectors.toList());

            if (idleRecordIds.isEmpty()) {
                return 0;
            }

            RecordId[] idsArray = idleRecordIds.toArray(new RecordId[0]);
            List<MapRecord<String, Object, Object>> claimed =
                    ops().claim(formattedKey, groupName, deadLetterConsumer,
                            Duration.ofMillis(minIdleTimeMs), idsArray);

            if (claimed == null || claimed.isEmpty()) {
                return 0;
            }

            // 使用 Lua 脚本原子性地写入死信队列并确认原消息
            String deadLetterKey = formattedKey + DEAD_LETTER_SUFFIX;
            long acknowledgedCount = 0;
            for (MapRecord<String, Object, Object> record : claimed) {
                List<String> keys = Arrays.asList(deadLetterKey, formattedKey);
                List<Object> args = new ArrayList<>();
                args.add(groupName);
                args.add(record.getId().getValue());

                Map<Object, Object> value = record.getValue();
                for (Map.Entry<Object, Object> entry : value.entrySet()) {
                    args.add(entry.getKey() != null ? entry.getKey().toString() : "");
                    args.add(entry.getValue() != null ? YdszJson.toJson(entry.getValue()) : "");
                }

                redisTemplate.execute((RedisCallback<Long>) connection -> {
                    byte[][] keysArray = keys.stream()
                            .map(k -> k.getBytes(StandardCharsets.UTF_8))
                            .toArray(byte[][]::new);
                    byte[][] argsArray = args.stream()
                            .map(a -> a.toString().getBytes(StandardCharsets.UTF_8))
                            .toArray(byte[][]::new);
                    // Merge keys and args for vararg passing
                    byte[][] allArgs = new byte[keysArray.length + argsArray.length][];
                    System.arraycopy(keysArray, 0, allArgs, 0, keysArray.length);
                    System.arraycopy(argsArray, 0, allArgs, keysArray.length, argsArray.length);
                    Object result = connection.scriptingCommands().eval(
                            DEAD_LETTER_ATOMIC_SCRIPT.getBytes(StandardCharsets.UTF_8),
                            ReturnType.INTEGER,
                            keysArray.length,
                            allArgs);
                    return result != null ? ((Number) result).longValue() : 0L;
                });
                acknowledgedCount++;
            }

            log.info("【Redis】死信认领完成 | streamKey={} | groupName={} | claimedCount={}",
                    streamKey, groupName, acknowledgedCount);
            return acknowledgedCount;
        } catch (Exception e) {
            log.error("【Redis】XCLAIM 操作失败 | streamKey={} | groupName={} | error={}",
                    streamKey, groupName, e);
            return 0;
        }
    }

    /**
     * 认领指定消息 ID 的超时未确认消息（XCLAIM）
     *
     * @param streamKey          Stream 键名
     * @param groupName          消费者组名
     * @param deadLetterConsumer 死信消费者名
     * @param minIdleTimeMs      最小空闲时间（毫秒）
     * @param recordIds          要认领的消息 ID 列表
     * @return 认领的消息列表
     */
    public List<StreamMessage> claim(String streamKey, String groupName, String deadLetterConsumer,
                                      long minIdleTimeMs, List<String> recordIds) {
        if (streamKey == null || groupName == null || deadLetterConsumer == null
                || recordIds == null || recordIds.isEmpty()) {
            log.warn("【Redis】XCLAIM 操作失败：参数不能为空");
            return Collections.emptyList();
        }
        String formattedKey = formatKey(streamKey);
        try {
            RecordId[] ids = recordIds.stream().map(RecordId::of).toArray(RecordId[]::new);
            List<MapRecord<String, Object, Object>> claimed =
                    ops().claim(formattedKey, groupName, deadLetterConsumer,
                            Duration.ofMillis(minIdleTimeMs), ids);
            return convertRecords(claimed, streamKey);
        } catch (Exception e) {
            log.error("【Redis】XCLAIM 操作失败 | streamKey={} | groupName={} | recordIds={} | error={}",
                    streamKey, groupName, recordIds, e);
            return Collections.emptyList();
        }
    }

    // ============================ 辅助方法 =============================

    /**
     * 获取 Stream 的长度
     *
     * @param streamKey Stream 键名
     * @return Stream 中的消息数量
     */
    public long size(String streamKey) {
        if (streamKey == null || streamKey.isEmpty()) {
            return 0;
        }
        String formattedKey = formatKey(streamKey);
        try {
            Long size = ops().size(formattedKey);
            return size != null ? size : 0;
        } catch (Exception e) {
            log.error("【Redis】XLEN 操作失败 | streamKey={} | error={}", streamKey, e);
            return 0;
        }
    }

    /**
     * 读取 Stream 中的消息（不通过消费者组）
     *
     * @param streamKey Stream 键名
     * @param startId   起始消息 ID，如 "0-0"
     * @param count     读取数量
     * @return 消息列表
     */
    public List<StreamMessage> read(String streamKey, String startId, int count) {
        if (streamKey == null || streamKey.isEmpty()) {
            return Collections.emptyList();
        }
        String formattedKey = formatKey(streamKey);
        try {
            StreamOffset<String> offset = StreamOffset.create(formattedKey, ReadOffset.from(startId));
            List<MapRecord<String, Object, Object>> records =
                    ops().read(StreamReadOptions.empty().count(count), offset);
            return convertRecords(records, streamKey);
        } catch (Exception e) {
            log.error("【Redis】XREAD 操作失败 | streamKey={} | error={}", streamKey, e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除 Stream 中的消息
     *
     * @param streamKey Stream 键名
     * @param recordIds 消息 ID 数组
     * @return 删除的消息数量
     */
    public long delete(String streamKey, String... recordIds) {
        if (streamKey == null || recordIds == null || recordIds.length == 0) {
            return 0;
        }
        String formattedKey = formatKey(streamKey);
        try {
            RecordId[] ids = Arrays.stream(recordIds)
                    .map(RecordId::of)
                    .toArray(RecordId[]::new);
            Long deleted = ops().delete(formattedKey, ids);
            return deleted != null ? deleted : 0;
        } catch (Exception e) {
            log.error("【Redis】XDEL 操作失败 | streamKey={} | recordIds={} | error={}",
                    streamKey, Arrays.toString(recordIds), e);
            return 0;
        }
    }

    private StreamOperations<String, Object, Object> ops() {
        return redisTemplate.opsForStream();
    }

    private Map<String, String> convertToStringMap(Map<String, Object> message) {
        Map<String, String> result = new LinkedHashMap<>(message.size());
        for (Map.Entry<String, Object> entry : message.entrySet()) {
            result.put(entry.getKey(), entry.getValue() != null ? YdszJson.toJson(entry.getValue()) : null);
        }
        return result;
    }

    private List<StreamMessage> convertRecords(List<MapRecord<String, Object, Object>> records, String originalStreamKey) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        return records.stream()
                .map(r -> new StreamMessage(r.getId().getValue(), originalStreamKey, r.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 背压感知读取结果
     *
     * @param messages      本次读取到的消息列表
     * @param actualBatch   实际使用的批次大小（已根据积压量调整）
     * @param pendingCount  当前消费者组 pending 积压总量
     */
    public record BackpressureResult(List<StreamMessage> messages, int actualBatch, long pendingCount) {

        /**
         * 判断是否处于高积压状态（需要限流）
         */
        public boolean isBackpressured() {
            return messages.size() < actualBatch;
        }

        @Override
        public String toString() {
            return String.format("BackpressureResult{msgSize=%d, actualBatch=%d, pending=%d}",
                    messages.size(), actualBatch, pendingCount);
        }
    }

    /**
     * Stream 消息封装
     */
    public static class StreamMessage {
        private final String id;
        private final String streamKey;
        private final Map<Object, Object> body;

        public StreamMessage(String id, String streamKey, Map<Object, Object> body) {
            this.id = id;
            this.streamKey = streamKey;
            this.body = body;
        }

        public String getId() {
            return id;
        }

        public String getStreamKey() {
            return streamKey;
        }

        /**
         * 获取消息体 Map。
         *
         * @return 消息体字段映射；body 为 null 时返回空 Map 而非 {@code null}
         */
        public Map<Object, Object> getBody() {
            return body != null ? body : Collections.emptyMap();
        }

        /**
         * 按类型安全获取消息体指定字段。
         *
         * <p>字段不存在或类型不匹配时返回 {@code null}，不做强制转换。</p>
         *
         * @param field 字段名
         * @param clazz 期望的字段值类型
         * @param <T>   目标类型
         * @return 转换后的字段值；缺失或类型不匹配时返回 {@code null}
         */
        public <T> T getBodyField(String field, Class<T> clazz) {
            if (body == null || field == null) {
                return null;
            }
            Object value = body.get(field);
            if (value == null) {
                return null;
            }
            if (clazz.isInstance(value)) {
                return clazz.cast(value);
            }
            return null;
        }
    }
}
