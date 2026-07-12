package com.njydsz.pmis.common.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 瀹㈡埛绔厤缃睘鎬х被
 *
 * <p>鎻愪緵 Redis 瀹㈡埛绔€夋嫨鍙婄浉鍏抽厤缃紝鏀寔閫氳繃 application.yml 涓殑
 * {@code remi.redis.client} 鍓嶇紑娉ㄥ叆閰嶇疆銆?
 *
 * <p><b>閰嶇疆绀轰緥锛坅pplication.yml锛夛細</b>
 * <pre>{@code
 * remi:
 *   redis:
 *     client:
 *       type: jedis
 *       read-from: REPLICA_PREFERRED
 *       pool:
 *         max-active: 16
 *         max-idle: 8
 *         min-idle: 2
 *         max-wait: 3000
 *       ssl:
 *         enabled: true
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "remi.redis.client")
public class RedisClientProperties {

    /**
     * 瀹㈡埛绔被鍨嬶紙榛樿 JEDIS锛?
     */
    private RedisClientType type = RedisClientType.JEDIS;

    /**
     * 杩炴帴姹犻厤缃?
     */
    private Pool pool = new Pool();

    /**
     * SSL 閰嶇疆
     */
    private Ssl ssl = new Ssl();

    /**
     * 璇荤瓥鐣ワ紙浠?Lettuce 瀹㈡埛绔敓鏁堬紝鐢ㄤ簬璇诲啓鍒嗙鍦烘櫙锛?
     * <p>鍙€夊€硷細MASTER銆丮ASTER_PREFERRED銆丷EPLICA_PREFERRED銆丷EPLICA銆丯EAREST
     * <p>榛樿鍊硷細MASTER锛堜粎浠庝富鑺傜偣璇诲彇锛?
     */
    private ReadFrom readFrom = ReadFrom.MASTER;

    /**
     * Redis 璇荤瓥鐣ユ灇涓撅紙瀵瑰簲 Lettuce 鐨?io.lettuce.core.ReadFrom锛?
     * <p>MASTER / MASTER_PREFERRED 宸插純鐢紝寤鸿浣跨敤 UPSTREAM / UPSTREAM_PREFERRED
     */
    public enum ReadFrom {
        /** 浠呬粠涓昏妭鐐硅鍙栵紙宸插純鐢紝璇蜂娇鐢?UPSTREAM锛?*/
        MASTER,
        /** 浼樺厛浠庝富鑺傜偣璇诲彇锛屼富鑺傜偣涓嶅彲鐢ㄦ椂浠庡壇鏈鍙栵紙宸插純鐢紝璇蜂娇鐢?UPSTREAM_PREFERRED锛?*/
        MASTER_PREFERRED,
        /** 浠呬粠涓婃父锛堜富锛夎妭鐐硅鍙?*/
        UPSTREAM,
        /** 浼樺厛浠庝笂娓革紙涓伙級鑺傜偣璇诲彇锛屼富鑺傜偣涓嶅彲鐢ㄦ椂浠庡壇鏈鍙?*/
        UPSTREAM_PREFERRED,
        /** 浼樺厛浠庡壇鏈鍙栵紝鍓湰涓嶅彲鐢ㄦ椂浠庝富鑺傜偣璇诲彇 */
        REPLICA_PREFERRED,
        /** 浠呬粠鍓湰璇诲彇 */
        REPLICA,
        /** 浠庣綉缁滄嫇鎵戞渶杩戠殑鑺傜偣璇诲彇 */
        NEAREST
    }

    /**
     * 杩炴帴姹犻厤缃被
     */
    @Data
    public static class Pool {

        /**
         * 鏈€澶ц繛鎺ユ暟锛堥粯璁?16锛?
         */
        private int maxActive = 16;

        /**
         * 鏈€澶х┖闂茶繛鎺ユ暟锛堥粯璁?8锛?
         */
        private int maxIdle = 8;

        /**
         * 鏈€灏忕┖闂茶繛鎺ユ暟锛堥粯璁?2锛?
         */
        private int minIdle = 2;

        /**
         * 鑾峰彇杩炴帴鏈€澶х瓑寰呮椂闂达紙姣锛夛紝-1 琛ㄧず鏃犻檺鍒?
         */
        private long maxWait = -1;

        /**
         * 鏄惁鍚敤杩炴帴姹狅紙榛樿鍚敤锛?
         */
        private boolean enabled = true;
    }

    /**
     * SSL 閰嶇疆绫?
     */
    @Data
    public static class Ssl {

        /**
         * 鏄惁鍚敤 SSL锛堥粯璁?false锛?
         */
        private boolean enabled = false;
    }
}
