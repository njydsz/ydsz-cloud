package com.njydsz.pmis.common.redis.service.ops;

import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.common.redis.config.RedisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis Stream 鎿嶄綔缁勪欢
 *
 * <p>鎻愪緵 Stream 鏁版嵁缁撴瀯鐨勫畬鏁存搷浣滄帴鍙ｏ紝鍖呮嫭锛?
 * <ul>
 *   <li>娑堟伅娣诲姞锛坅dd锛?/li>
 *   <li>娑堣垂鑰呯粍绠＄悊锛坈reateGroup銆乨eleteGroup锛?/li>
 *   <li>娑堣垂鑰呯粍璇诲彇锛坮eadGroup锛?/li>
 *   <li>娑堟伅纭锛坅ck锛?/li>
 *   <li>寰呭鐞嗘秷鎭煡璇紙pendingInfo锛?/li>
 *   <li>姝讳俊闃熷垪锛圶CLAIM 瓒呮椂鏈‘璁ょ殑娑堟伅锛?/li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
@Component
@SuppressWarnings("unchecked")
public class RedisStreamOps {

    private static final String DEAD_LETTER_SUFFIX = ":deadletter";

    private final RedisTemplate<String, Object> redisTemplate;
    private final String keyPrefix;

    public RedisStreamOps(RedisTemplate<String, Object> redisTemplate, RedisProperties redisProperties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "RedisTemplate 涓嶈兘涓?null");
        this.keyPrefix = redisProperties != null ? (redisProperties.getKeyPrefix() != null ? redisProperties.getKeyPrefix() : "") : "";
    }

    /**
     * 鏍煎紡鍖?Key锛屾坊鍔犵粺涓€鍓嶇紑
     */
    private String formatKey(String key) {
        if (key == null || keyPrefix.isEmpty()) {
            return key;
        }
        return keyPrefix + ":" + key;
    }

    // ============================ 娑堟伅娣诲姞 =============================

    /**
     * 鍚?Stream 娣诲姞娑堟伅
     *
     * @param streamKey Stream 閿悕
     * @param message   娑堟伅鍐呭锛堥敭鍊煎锛?
     * @return 娑堟伅 ID
     */
    public String add(String streamKey, Map<String, Object> message) {
        if (streamKey == null || streamKey.isEmpty()) {
            log.warn("銆怰edis銆慩ADD 鎿嶄綔澶辫触锛歋tream 閿悕涓嶈兘涓虹┖");
            return null;
        }
        if (message == null || message.isEmpty()) {
            log.warn("銆怰edis銆慩ADD 鎿嶄綔澶辫触锛氭秷鎭唴瀹逛笉鑳戒负绌?);
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
            log.error("銆怰edis銆慩ADD 鎿嶄綔澶辫触 | streamKey={} | error={}", streamKey, e);
            return null;
        }
    }

    /**
     * 鍚?Stream 娣诲姞娑堟伅锛堝甫鏈€澶ч暱搴﹂檺鍒讹級
     *
     * @param streamKey    Stream 閿悕
     * @param message      娑堟伅鍐呭
     * @param maxMaxLength 鏈€澶ч暱搴︼紙杩戜技瑁佸壀锛?
     * @return 娑堟伅 ID
     */
    public String add(String streamKey, Map<String, Object> message, long maxMaxLength) {
        if (streamKey == null || streamKey.isEmpty()) {
            log.warn("銆怰edis銆慩ADD 鎿嶄綔澶辫触锛歋tream 閿悕涓嶈兘涓虹┖");
            return null;
        }
        if (message == null || message.isEmpty()) {
            log.warn("銆怰edis銆慩ADD 鎿嶄綔澶辫触锛氭秷鎭唴瀹逛笉鑳戒负绌?);
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
            log.error("銆怰edis銆慩ADD 鎿嶄綔澶辫触 | streamKey={} | maxlen={} | error={}", streamKey, maxMaxLength, e);
            return null;
        }
    }

    // ============================ 娑堣垂鑰呯粍绠＄悊 =============================

    /**
     * 鍒涘缓娑堣垂鑰呯粍
     *
     * @param streamKey   Stream 閿悕
     * @param groupName   娑堣垂鑰呯粍鍚?
     * @param readOffset  璧峰鍋忕Щ閲忥紝濡?"0"锛堜粠澶村紑濮嬶級鎴?"$"锛堜粎鏂版秷鎭級
     * @return true-鍒涘缓鎴愬姛
     */
    public boolean createGroup(String streamKey, String groupName, String readOffset) {
        if (streamKey == null || streamKey.isEmpty() || groupName == null || groupName.isEmpty()) {
            log.warn("銆怰edis銆慩GROUP CREATE 鎿嶄綔澶辫触锛氬弬鏁颁笉鑳戒负绌?);
            return false;
        }
        String formattedKey = formatKey(streamKey);
        try {
            ReadOffset offset = ReadOffset.from(readOffset != null ? readOffset : "$");
            ops().createGroup(formattedKey, offset, groupName);
            log.info("銆怰edis銆戝垱寤烘秷璐硅€呯粍鎴愬姛 | streamKey={} | groupName={} | offset={}", streamKey, groupName, readOffset);
            return true;
        } catch (Exception e) {
            log.error("銆怰edis銆慩GROUP CREATE 鎿嶄綔澶辫触 | streamKey={} | groupName={} | error={}", streamKey, groupName, e);
            return false;
        }
    }

    /**
     * 鍒涘缓娑堣垂鑰呯粍锛堜粠鏈€鏂版秷鎭紑濮嬫秷璐癸級
     *
     * @param streamKey Stream 閿悕
     * @param groupName 娑堣垂鑰呯粍鍚?
     * @return true-鍒涘缓鎴愬姛
     */
    public boolean createGroup(String streamKey, String groupName) {
        return createGroup(streamKey, groupName, "$");
    }

    /**
     * 鍒犻櫎娑堣垂鑰呯粍
     *
     * @param streamKey Stream 閿悕
     * @param groupName 娑堣垂鑰呯粍鍚?
     * @return true-鍒犻櫎鎴愬姛
     */
    public boolean deleteGroup(String streamKey, String groupName) {
        if (streamKey == null || streamKey.isEmpty() || groupName == null || groupName.isEmpty()) {
            log.warn("銆怰edis銆慩GROUP DESTROY 鎿嶄綔澶辫触锛氬弬鏁颁笉鑳戒负绌?);
            return false;
        }
        String formattedKey = formatKey(streamKey);
        try {
            ops().destroyGroup(formattedKey, groupName);
            log.info("銆怰edis銆戝垹闄ゆ秷璐硅€呯粍鎴愬姛 | streamKey={} | groupName={}", streamKey, groupName);
            return true;
        } catch (Exception e) {
            log.error("銆怰edis銆慩GROUP DESTROY 鎿嶄綔澶辫触 | streamKey={} | groupName={} | error={}", streamKey, groupName, e);
            return false;
        }
    }

    // ============================ 娑堣垂鑰呯粍璇诲彇 =============================

    /**
     * 浠庢秷璐硅€呯粍璇诲彇鏈‘璁ょ殑娑堟伅
     *
     * @param streamKey    Stream 閿悕
     * @param groupName    娑堣垂鑰呯粍鍚?
     * @param consumerName 娑堣垂鑰呭悕
     * @param count        璇诲彇鏁伴噺
     * @return 娑堟伅鍒楄〃
     */
    public List<StreamMessage> readGroup(String streamKey, String groupName, String consumerName, int count) {
        if (streamKey == null || groupName == null || consumerName == null) {
            log.warn("銆怰edis銆慩READGROUP 鎿嶄綔澶辫触锛氬弬鏁颁笉鑳戒负绌?);
            return Collections.emptyList();
        }
        String formattedKey = formatKey(streamKey);
        try {
            Consumer consumer = Consumer.from(groupName, consumerName);
            StreamReadOptions readOptions = StreamReadOptions.empty().count(count);
            StreamOffset<String> offset = StreamOffset.create(formattedKey, ReadOffset.lastConsumed());
            List<MapRecord<String, Object, Object>> records =
                    ops().read(consumer, readOptions, offset);
            return convertRecords(records, streamKey);
        } catch (Exception e) {
            log.error("銆怰edis銆慩READGROUP 鎿嶄綔澶辫触 | streamKey={} | groupName={} | consumerName={} | error={}",
                    streamKey, groupName, consumerName, e);
            return Collections.emptyList();
        }
    }

    /**
     * 浠庢秷璐硅€呯粍璇诲彇鏈‘璁ょ殑娑堟伅锛堥粯璁よ鍙?1 鏉★級
     *
     * @param streamKey    Stream 閿悕
     * @param groupName    娑堣垂鑰呯粍鍚?
     * @param consumerName 娑堣垂鑰呭悕
     * @return 娑堟伅鍒楄〃
     */
    public List<StreamMessage> readGroup(String streamKey, String groupName, String consumerName) {
        return readGroup(streamKey, groupName, consumerName, 1);
    }

    // ============================ 娑堟伅纭 =============================

    /**
     * 纭娑堟伅宸插鐞?
     *
     * @param streamKey  Stream 閿悕
     * @param groupName  娑堣垂鑰呯粍鍚?
     * @param recordIds  娑堟伅 ID 鏁扮粍
     * @return 纭鐨勬秷鎭暟閲?
     */
    public long ack(String streamKey, String groupName, String... recordIds) {
        if (streamKey == null || groupName == null || recordIds == null || recordIds.length == 0) {
            log.warn("銆怰edis銆慩ACK 鎿嶄綔澶辫触锛氬弬鏁颁笉鑳戒负绌?);
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
            log.error("銆怰edis銆慩ACK 鎿嶄綔澶辫触 | streamKey={} | groupName={} | recordIds={} | error={}",
                    streamKey, groupName, Arrays.toString(recordIds), e);
            return 0;
        }
    }

    // ============================ 寰呭鐞嗘秷鎭煡璇?=============================

    /**
     * 鑾峰彇寰呭鐞嗘秷鎭俊鎭?
     *
     * @param streamKey Stream 閿悕
     * @param groupName 娑堣垂鑰呯粍鍚?
     * @return 寰呭鐞嗘秷鎭憳瑕佷俊鎭?
     */
    public PendingMessagesSummary pendingInfo(String streamKey, String groupName) {
        if (streamKey == null || groupName == null) {
            log.warn("銆怰edis銆慩PENDING 鎿嶄綔澶辫触锛氬弬鏁颁笉鑳戒负绌?);
            return null;
        }
        String formattedKey = formatKey(streamKey);
        try {
            return ops().pending(formattedKey, groupName);
        } catch (Exception e) {
            log.error("銆怰edis銆慩PENDING 鎿嶄綔澶辫触 | streamKey={} | groupName={} | error={}", streamKey, groupName, e);
            return null;
        }
    }

    /**
     * 鑾峰彇寰呭鐞嗘秷鎭鎯呭垪琛?
     *
     * @param streamKey    Stream 閿悕
     * @param groupName    娑堣垂鑰呯粍鍚?
     * @param consumerName 娑堣垂鑰呭悕锛堝彲閫夛紝null 琛ㄧず鏌ヨ鎵€鏈夋秷璐硅€咃級
     * @param count        鏈€澶ц繑鍥炴暟閲?
     * @return 寰呭鐞嗘秷鎭垪琛?
     */
    public PendingMessages pendingMessages(String streamKey, String groupName, String consumerName, int count) {
        if (streamKey == null || groupName == null) {
            log.warn("銆怰edis銆慩PENDING 鎿嶄綔澶辫触锛氬弬鏁颁笉鑳戒负绌?);
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
            log.error("銆怰edis銆慩PENDING 鎿嶄綔澶辫触 | streamKey={} | groupName={} | consumerName={} | error={}",
                    streamKey, groupName, consumerName, e);
            return null;
        }
    }

    // ============================ 姝讳俊闃熷垪锛圶CLAIM锛?=============================

    /**
     * 鍘熷瓙鎿嶄綔 Lua 鑴氭湰锛氬皢娑堟伅鍐欏叆姝讳俊闃熷垪骞剁‘璁ゅ師娑堟伅
     *
     * <p>閫氳繃 Lua 鑴氭湰淇濊瘉 XADD锛堝啓鍏ユ淇￠槦鍒楋級鍜?XACK锛堢‘璁ゅ師娑堟伅锛夌殑鍘熷瓙鎬э紝
     * 閬垮厤涓棿澶辫触瀵艰嚧娑堟伅涓㈠け鎴栭噸澶嶃€?
     *
     * <p>鍙傛暟璇存槑锛?
     * KEYS[1] = 姝讳俊闃熷垪閿悕
     * KEYS[2] = 鍘熷 Stream 閿悕
     * ARGV[1] = 娑堣垂鑰呯粍鍚?
     * ARGV[2] = 娑堟伅 ID
     * ARGV[3..] = 浜ゆ浛鐨?field-value 瀵癸紙鐢ㄤ簬 XADD锛?
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
     * 璁ら瓒呮椂鏈‘璁ょ殑娑堟伅锛圶CLAIM锛夛紝灏嗗叾杞Щ鍒版淇￠槦鍒?
     *
     * <p>褰撴秷鎭湪鎸囧畾鏃堕棿鍐呮湭琚‘璁わ紙ACK锛夛紝灏嗗叾浠庡師娑堣垂鑰呰浆绉诲埌鎸囧畾娑堣垂鑰咃紝
     * 骞朵娇鐢?Lua 鑴氭湰鍘熷瓙鎬у湴鍐欏叆姝讳俊闃熷垪骞剁‘璁ゅ師娑堟伅锛圶ACK + XADD 鍚堝苟涓哄師瀛愭搷浣滐級銆?
     *
     * @param streamKey          Stream 閿悕
     * @param groupName          娑堣垂鑰呯粍鍚?
     * @param deadLetterConsumer 姝讳俊娑堣垂鑰呭悕
     * @param minIdleTimeMs      鏈€灏忕┖闂叉椂闂达紙姣锛夛紝瓒呰繃姝ゆ椂闂寸殑鏈‘璁ゆ秷鎭皢琚棰?
     * @param count              鏈€澶ц棰嗘暟閲?
     * @return 璁ら鐨勬秷鎭暟閲?
     */
    public long claimDeadLetters(String streamKey, String groupName, String deadLetterConsumer,
                                  long minIdleTimeMs, int count) {
        if (streamKey == null || groupName == null || deadLetterConsumer == null) {
            log.warn("銆怰edis銆慩CLAIM 鎿嶄綔澶辫触锛氬弬鏁颁笉鑳戒负绌?);
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

            // 浣跨敤 Lua 鑴氭湰鍘熷瓙鎬у湴鍐欏叆姝讳俊闃熷垪骞剁‘璁ゅ師娑堟伅
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
                    args.add(entry.getValue() != null ? JsonUtils.toJson(entry.getValue()) : "");
                }

                redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> {
                    byte[][] keysArray = keys.stream()
                            .map(k -> k.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                            .toArray(byte[][]::new);
                    byte[][] argsArray = args.stream()
                            .map(a -> a.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                            .toArray(byte[][]::new);
                    // Merge keys and args for vararg passing
                    byte[][] allArgs = new byte[keysArray.length + argsArray.length][];
                    System.arraycopy(keysArray, 0, allArgs, 0, keysArray.length);
                    System.arraycopy(argsArray, 0, allArgs, keysArray.length, argsArray.length);
                    Object result = connection.scriptingCommands().eval(
                            DEAD_LETTER_ATOMIC_SCRIPT.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            org.springframework.data.redis.connection.ReturnType.INTEGER,
                            keysArray.length,
                            allArgs);
                    return result != null ? ((Number) result).longValue() : 0L;
                });
                acknowledgedCount++;
            }

            log.info("銆怰edis銆戞淇¤棰嗗畬鎴?| streamKey={} | groupName={} | claimedCount={}",
                    streamKey, groupName, acknowledgedCount);
            return acknowledgedCount;
        } catch (Exception e) {
            log.error("銆怰edis銆慩CLAIM 鎿嶄綔澶辫触 | streamKey={} | groupName={} | error={}",
                    streamKey, groupName, e);
            return 0;
        }
    }

    /**
     * 璁ら鎸囧畾娑堟伅 ID 鐨勮秴鏃舵湭纭娑堟伅锛圶CLAIM锛?
     *
     * @param streamKey          Stream 閿悕
     * @param groupName          娑堣垂鑰呯粍鍚?
     * @param deadLetterConsumer 姝讳俊娑堣垂鑰呭悕
     * @param minIdleTimeMs      鏈€灏忕┖闂叉椂闂达紙姣锛?
     * @param recordIds          瑕佽棰嗙殑娑堟伅 ID 鍒楄〃
     * @return 璁ら鐨勬秷鎭垪琛?
     */
    public List<StreamMessage> claim(String streamKey, String groupName, String deadLetterConsumer,
                                      long minIdleTimeMs, List<String> recordIds) {
        if (streamKey == null || groupName == null || deadLetterConsumer == null
                || recordIds == null || recordIds.isEmpty()) {
            log.warn("銆怰edis銆慩CLAIM 鎿嶄綔澶辫触锛氬弬鏁颁笉鑳戒负绌?);
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
            log.error("銆怰edis銆慩CLAIM 鎿嶄綔澶辫触 | streamKey={} | groupName={} | recordIds={} | error={}",
                    streamKey, groupName, recordIds, e);
            return Collections.emptyList();
        }
    }

    // ============================ 杈呭姪鏂规硶 =============================

    /**
     * 鑾峰彇 Stream 鐨勯暱搴?
     *
     * @param streamKey Stream 閿悕
     * @return Stream 涓殑娑堟伅鏁伴噺
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
            log.error("銆怰edis銆慩LEN 鎿嶄綔澶辫触 | streamKey={} | error={}", streamKey, e);
            return 0;
        }
    }

    /**
     * 璇诲彇 Stream 涓殑娑堟伅锛堜笉閫氳繃娑堣垂鑰呯粍锛?
     *
     * @param streamKey Stream 閿悕
     * @param startId   璧峰娑堟伅 ID锛屽 "0-0"
     * @param count     璇诲彇鏁伴噺
     * @return 娑堟伅鍒楄〃
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
            log.error("銆怰edis銆慩READ 鎿嶄綔澶辫触 | streamKey={} | error={}", streamKey, e);
            return Collections.emptyList();
        }
    }

    /**
     * 鍒犻櫎 Stream 涓殑娑堟伅
     *
     * @param streamKey Stream 閿悕
     * @param recordIds 娑堟伅 ID 鏁扮粍
     * @return 鍒犻櫎鐨勬秷鎭暟閲?
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
            log.error("銆怰edis銆慩DEL 鎿嶄綔澶辫触 | streamKey={} | recordIds={} | error={}",
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
            result.put(entry.getKey(), entry.getValue() != null ? JsonUtils.toJson(entry.getValue()) : null);
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
     * Stream 娑堟伅灏佽
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

        public Map<Object, Object> getBody() {
            return body != null ? body : Collections.emptyMap();
        }

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
