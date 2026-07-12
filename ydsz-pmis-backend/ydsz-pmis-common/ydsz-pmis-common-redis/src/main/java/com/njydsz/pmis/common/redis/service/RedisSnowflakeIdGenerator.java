package com.njydsz.pmis.common.redis.service;

import com.njydsz.pmis.common.redis.config.RedisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 鍒嗗竷寮忛洩鑺?ID 鐢熸垚鍣? *
 * <p>鍩轰簬 Twitter Snowflake 绠楁硶锛? * <ul>
 *   <li>1 浣嶇鍙蜂綅锛堝浐瀹?0锛?/li>
 *   <li>41 浣嶆椂闂存埑锛堟绉掔骇锛岀害 69 骞达級</li>
 *   <li>10 浣嶅伐浣滄満鍣?ID锛? 浣嶆暟鎹腑蹇?+ 5 浣嶅伐浣滆妭鐐癸級</li>
 *   <li>12 浣嶅簭鍒楀彿锛堟瘡姣 4096 涓?ID锛?/li>
 * </ul>
 *
 * <p><b>workerId 鍒嗛厤锛?/b>閫氳繃 Redis INCR 鍏ㄥ眬閫掑鍒嗛厤锛岀‘淇濋泦缇や腑姣忎釜瀹炰緥鐨?workerId 鍞竴銆? * 鏀寔鑷畾涔?datacenterId锛?~31锛夊拰 workerId 涓婇檺妫€娴嬨€? *
 * <p><b>鐗规€э細</b>
 * <ul>
 *   <li>鍏ㄥ眬鍞竴锛歸orkerId + sequence + timestamp 缁勫悎淇濊瘉鍞竴</li>
 *   <li>瓒嬪娍閫掑锛氬悓涓€姣鍐呭簭鍒楅€掑锛屾暣浣撴寜鏃堕棿鍗曡皟閫掑</li>
 *   <li>楂樺悶鍚愶細鍗曞疄渚嬪嘲鍊煎彲杈?4096 * 1000 = 409.6 涓?QPS</li>
 *   <li>楂樺彲鐢細Redis 涓嶅彲鐢ㄦ椂闄嶇骇涓烘湰鍦板簭鍒楃敓鎴?/li>
 * </ul>
 *
 * <p><b>浣跨敤绀轰緥锛?/b>
 * <pre>{@code
 * @Autowired
 * private RedisSnowflakeIdGenerator idGenerator;
 *
 * public Long createOrder() {
 *     return idGenerator.nextId();
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class RedisSnowflakeIdGenerator {

    /** 璧峰鏃堕棿鎴筹紙2024-01-01锛?*/
    private static final long EPOCH = 1704038400000L;

    /** 宸ヤ綔鏈哄櫒 ID 鍗犵敤浣嶆暟 */
    private static final long WORKER_ID_BITS = 5L;

    /** 鏁版嵁涓績 ID 鍗犵敤浣嶆暟 */
    private static final long DATACENTER_ID_BITS = 5L;

    /** 搴忓垪鍙峰崰鐢ㄤ綅鏁?*/
    private static final long SEQUENCE_BITS = 12L;

    /** 鏈€澶у伐浣滄満鍣?ID锛?1锛?*/
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /** 鏈€澶ф暟鎹腑蹇?ID锛?1锛?*/
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    /** 搴忓垪鍙锋帺鐮侊紙4095锛?*/
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /** 宸ヤ綔鏈哄櫒 ID 宸︾Щ浣嶆暟 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /** 鏁版嵁涓績 ID 宸︾Щ浣嶆暟 */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 鏃堕棿鎴冲乏绉讳綅鏁?*/
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /** Redis 鍒嗛厤 workerId 鐨?Lua 鑴氭湰锛堝師瀛愰€掑 + 涓婇檺淇濇姢锛?*/
    private static final String ALLOCATE_WORKER_LUA =
            "local current = redis.call('INCR', KEYS[1]) " +
            "if current > tonumber(ARGV[1]) then " +
            "  redis.call('SET', KEYS[1], 0) " +
            "  return 0 " +
            "end " +
            "return current - 1";

    /**
     * Redis 蹇冭烦缁害 Lua 鑴氭湰锛?     * 浠呭綋 key 瀛樺湪鏃舵墠缁害锛圗XPIRE锛夛紝閬垮厤宸查噴鏀剧殑 workerId 琚敊璇画绾?     */
    private static final String HEARTBEAT_LUA =
            "if redis.call('EXISTS', KEYS[1]) == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) " +
            "  return 1 " +
            "end " +
            "return 0";

    /** workerId 蹇冭烦闂撮殧锛堢锛夛細榛樿 30 绉?*/
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30L;

    /** workerId 蹇冭烦 TTL锛堢锛夛細榛樿 90 绉掞紝瓒呰繃 3 涓績璺冲懆鏈熸湭缁害鍒欒嚜鍔ㄩ噴鏀?*/
    private static final long HEARTBEAT_TTL_SECONDS = 90L;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisProperties redisProperties;
    private final long datacenterId;
    private final long workerId;
    private final AtomicLong sequence = new AtomicLong(0L);
    private final AtomicLong lastTimestamp = new AtomicLong(-1L);

    private final ConcurrentHashMap<String, DefaultRedisScript<Long>> scriptCache = new ConcurrentHashMap<>();

    /** 蹇冭烦瀹氭椂浠诲姟鎵ц鍣?*/
    private ScheduledExecutorService heartbeatExecutor;

    /** 鏍囪褰撳墠瀹炰緥鏄惁宸插叧闂紝鐢ㄤ簬蹇冭烦浠诲姟閫€鍑哄垽鏂?*/
    private volatile boolean shutdown = false;

    public RedisSnowflakeIdGenerator(RedisTemplate<String, Object> redisTemplate,
                                     RedisProperties redisProperties) {
        this(redisTemplate, redisProperties, -1L, true);
    }

    /**
     * 鏋勯€犳柟娉?     *
     * @param redisTemplate  Redis 瀹㈡埛绔?     * @param redisProperties Redis 閰嶇疆
     * @param datacenterId   鏁版嵁涓績 ID锛?~31锛夛紝-1 琛ㄧず鑷姩鍒嗛厤
     * @param autoAllocateWorkerId 鏄惁鑷姩閫氳繃 Redis 鍒嗛厤 workerId
     */
    public RedisSnowflakeIdGenerator(RedisTemplate<String, Object> redisTemplate,
                                     RedisProperties redisProperties,
                                     long datacenterId,
                                     boolean autoAllocateWorkerId) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;

        long dcId = datacenterId < 0
                ? (Math.abs((long) (System.getenv().getOrDefault("HOSTNAME", "0").hashCode())) % (MAX_DATACENTER_ID + 1))
                : Math.min(datacenterId, MAX_DATACENTER_ID);
        this.datacenterId = dcId;

        long wId = 0L;
        if (autoAllocateWorkerId) {
            try {
                wId = allocateWorkerIdFromRedis();
                // 鍒嗛厤鎴愬姛鍚庣珛鍗宠缃績璺?TTL锛屽苟鍚姩缁害浠诲姟
                setWorkerIdHeartbeat(wId);
                startHeartbeatScheduler(wId);
            } catch (Exception e) {
                log.warn("銆怱nowflake銆戜粠 Redis 鍒嗛厤 workerId 澶辫触锛岄檷绾т负鏈湴鐢熸垚 | error={}", e);
                wId = Math.abs(Thread.currentThread().threadId()) % (MAX_WORKER_ID + 1);
            }
        }
        this.workerId = wId;
        log.info("銆怱nowflake銆慖D 鐢熸垚鍣ㄥ垵濮嬪寲瀹屾垚 | datacenterId={} | workerId={}", this.datacenterId, this.workerId);
    }

    private long allocateWorkerIdFromRedis() {
        String key = formatKey("snowflake:worker_id");
        DefaultRedisScript<Long> script = scriptCache.computeIfAbsent("alloc_worker", k -> {
            DefaultRedisScript<Long> s = new DefaultRedisScript<>();
            s.setScriptText(ALLOCATE_WORKER_LUA);
            s.setResultType(Long.class);
            return s;
        });
        Long result = redisTemplate.execute(script, Collections.singletonList(key),
                String.valueOf(MAX_WORKER_ID));
        return result != null ? result : 0L;
    }

    /**
     * 涓?workerId 璁剧疆鍒濆蹇冭烦 TTL
     *
     * @param workerId 宸ヤ綔鑺傜偣 ID
     */
    private void setWorkerIdHeartbeat(long workerId) {
        String key = formatKey("snowflake:worker:" + workerId);
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()),
                Duration.ofSeconds(HEARTBEAT_TTL_SECONDS));
        log.debug("銆怱nowflake銆戣缃?workerId 蹇冭烦 TTL | workerId={} | ttl={}s", workerId, HEARTBEAT_TTL_SECONDS);
    }

    /**
     * 鍚姩 workerId 蹇冭烦缁害瀹氭椂浠诲姟
     *
     * @param workerId 宸ヤ綔鑺傜偣 ID
     */
    private void startHeartbeatScheduler(long workerId) {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "snowflake-heartbeat-" + workerId);
            t.setDaemon(true);
            return t;
        });

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (shutdown) {
                return;
            }
            try {
                renewWorkerIdHeartbeat(workerId);
            } catch (Exception e) {
                log.warn("銆怱nowflake銆憌orkerId 蹇冭烦缁害澶辫触 | workerId={} | error={}", workerId, e.getMessage());
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        log.info("銆怱nowflake銆戝惎鍔?workerId 蹇冭烦缁害浠诲姟 | workerId={} | interval={}s",
                workerId, HEARTBEAT_INTERVAL_SECONDS);
    }

    /**
     * 缁害 workerId 蹇冭烦锛堜娇鐢?Lua 鑴氭湰纭繚鍘熷瓙鎬э級
     *
     * @param workerId 宸ヤ綔鑺傜偣 ID
     */
    private void renewWorkerIdHeartbeat(long workerId) {
        String key = formatKey("snowflake:worker:" + workerId);
        DefaultRedisScript<Long> script = scriptCache.computeIfAbsent("heartbeat", k -> {
            DefaultRedisScript<Long> s = new DefaultRedisScript<>();
            s.setScriptText(HEARTBEAT_LUA);
            s.setResultType(Long.class);
            return s;
        });

        Long result = redisTemplate.execute(script, Collections.singletonList(key),
                String.valueOf(HEARTBEAT_TTL_SECONDS));

        if (result != null && result == 1L) {
            log.debug("銆怱nowflake銆憌orkerId 蹇冭烦缁害鎴愬姛 | workerId={}", workerId);
        } else {
            log.warn("銆怱nowflake銆憌orkerId 宸茶繃鏈熸垨琚噴鏀撅紝灏濊瘯閲嶆柊鍒嗛厤 | workerId={}", workerId);
            // workerId 宸插け鏁堬紝灏濊瘯閲嶆柊鍒嗛厤
            try {
                long newWorkerId = allocateWorkerIdFromRedis();
                setWorkerIdHeartbeat(newWorkerId);
                log.info("銆怱nowflake銆戦噸鏂板垎閰?workerId 鎴愬姛 | oldWorkerId={} | newWorkerId={}",
                        workerId, newWorkerId);
            } catch (Exception e) {
                log.error("銆怱nowflake銆戦噸鏂板垎閰?workerId 澶辫触 | error={}", e.getMessage());
            }
        }
    }

    /**
     * 搴旂敤鍏抽棴鏃舵竻鐞嗗績璺充换鍔?     */
    @PreDestroy
    public void shutdown() {
        shutdown = true;
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("銆怱nowflake銆戝仠姝?workerId 蹇冭烦缁害浠诲姟 | workerId={}", workerId);
        }
    }

    /**
     * 鐢熸垚涓嬩竴涓敮涓€ ID
     *
     * @return 64 浣?Long 绫诲瀷 ID
     */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();

        if (timestamp < lastTimestamp.get()) {
            // 鏃堕挓鍥炴嫧澶勭悊锛氱瓑寰呭埌鏈€鍚庢椂闂存埑
            long offset = lastTimestamp.get() - timestamp;
            if (offset <= 5) {
                try {
                    Thread.sleep(offset << 1);
                    timestamp = currentTimeMillis();
                    if (timestamp < lastTimestamp.get()) {
                        throw new RuntimeException("鏃堕挓鍥炴嫧瓒呰繃 5ms锛屾嫆缁濈敓鎴?ID");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("ID 鐢熸垚琚腑鏂?, e);
                }
            } else {
                throw new RuntimeException(
                        String.format("鏃堕挓鍥炴嫧 %d ms锛屾嫆缁濈敓鎴?ID", offset));
            }
        }

        if (timestamp == lastTimestamp.get()) {
            long seq = (sequence.incrementAndGet()) & SEQUENCE_MASK;
            if (seq == 0) {
                // 褰撳墠姣搴忓垪鍙风敤灏斤紝绛夊緟涓嬩竴姣
                timestamp = waitNextMillis(lastTimestamp.get());
            }
            sequence.set(seq);
        } else {
            sequence.set(0L);
        }

        lastTimestamp.set(timestamp);

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence.get();
    }

    /**
     * 瑙ｆ瀽 ID 涓殑鏃堕棿鎴?     */
    public static long parseTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + EPOCH;
    }

    /**
     * 瑙ｆ瀽 ID 涓殑鏁版嵁涓績 ID
     */
    public static long parseDatacenterId(long id) {
        return (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
    }

    /**
     * 瑙ｆ瀽 ID 涓殑宸ヤ綔鑺傜偣 ID
     */
    public static long parseWorkerId(long id) {
        return (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    /**
     * 瑙ｆ瀽 ID 涓殑搴忓垪鍙?     */
    public static long parseSequence(long id) {
        return id & SEQUENCE_MASK;
    }

    public long getDatacenterId() {
        return datacenterId;
    }

    public long getWorkerId() {
        return workerId;
    }

    private long waitNextMillis(long lastTs) {
        long ts = currentTimeMillis();
        while (ts <= lastTs) {
            ts = currentTimeMillis();
        }
        return ts;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private String formatKey(String key) {
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        if (prefix == null || prefix.isEmpty()) {
            return key;
        }
        return prefix + ":" + key;
    }
}
