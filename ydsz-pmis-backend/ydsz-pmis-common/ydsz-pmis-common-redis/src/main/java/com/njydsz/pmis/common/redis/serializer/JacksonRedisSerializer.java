package com.njydsz.pmis.common.redis.serializer;

import com.njydsz.pmis.common.util.json.JsonUtils;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Jackson 鐗堟湰鐨?Redis 搴忓垪鍖栧伐鍏风被
 *
 * <p>鎻愪緵鍩轰簬 {@link JsonUtils} 鐨勯珮鎬ц兘搴忓垪鍖栧疄鐜帮紝鐢ㄤ簬 Redis 鍊肩殑搴忓垪鍖?鍙嶅簭鍒楀寲銆?
 * 缁熶竴浣跨敤 ydsz-pmis-common-util 涓殑 JsonUtils 宸ュ叿绫伙紝纭繚鍏ㄩ」鐩?JSON 澶勭悊鐨勪竴鑷存€с€?
 *
 * <p><b>涓昏鍔熻兘锛?/b>
 * <ul>
 *   <li>瀵硅薄搴忓垪鍖栦负 JSON 瀛楄妭鏁扮粍锛堥€氳繃 JsonUtils.toJsonBytes锛?/li>
 *   <li>JSON 瀛楄妭鏁扮粍鍙嶅簭鍒楀寲涓哄璞★紙閫氳繃 JsonUtils.fromJsonBytes锛?/li>
 *   <li>鏀寔 Java 8 鏃堕棿绫诲瀷锛堢敱 JsonUtils 鍐呴儴 JavaTimeModule 澶勭悊锛?/li>
 *   <li>鏀寔澶嶆潅瀵硅薄宓屽</li>
 * </ul>
 *
 * <p><b>渚濊禆璇存槑锛?/b>
 * <ul>
 *   <li>Jackson 鐢?ydsz-pmis-common-util 浼犻€掍緷璧栧紩鍏ワ紝鏃犻渶鏄惧紡澹版槑</li>
 *   <li>搴忓垪鍖?鍙嶅簭鍒楀寲閫昏緫缁熶竴濮旀墭缁?JsonUtils锛屼繚鎸佸叏椤圭洰涓€鑷?/li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 * @since 1.0.0
 */
public class JacksonRedisSerializer implements RedisSerializer<Object> {

    /**
     * 榛樿瀛楃闆嗭紙淇濈暀鐢ㄤ簬鍏煎鎬э級
     */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /**
     * 瑕佸簭鍒楀寲鐨勫璞＄被鍨?
     */
    private final Class<?> clazz;

    /**
     * 鏃犲弬鏋勯€犲櫒锛堝吋瀹?Spring 鍙嶅皠鍒涘缓锛?
     * <p>璀﹀憡锛氫娇鐢ㄦ鏋勯€犲櫒鍒涘缓鐨勫簭鍒楀寲鍣ㄥ湪鍙嶅簭鍒楀寲鏃舵棤娉曠‘瀹氬叿浣撶被鍨嬶紝
     * 灏嗗弽搴忓垪鍖栦负 Object 绫诲瀷銆?
     */
    public JacksonRedisSerializer() {
        this.clazz = Object.class;
    }

    /**
     * 鏋勯€犲櫒
     *
     * @param clazz 瑕佸簭鍒楀寲鐨勫璞＄被鍨?
     */
    public JacksonRedisSerializer(Class<?> clazz) {
        this.clazz = clazz != null ? clazz : Object.class;
    }

    /**
     * 搴忓垪鍖栧璞?
     *
     * <p>浣跨敤 {@link JsonUtils#toJsonBytes(Object)} 灏嗗璞¤浆鎹负 JSON 瀛楄妭鏁扮粍銆?
     *
     * @param t 瑕佸簭鍒楀寲鐨勫璞?
     * @return 搴忓垪鍖栧悗鐨勫瓧鑺傛暟缁?
     * @throws SerializationException 濡傛灉搴忓垪鍖栧け璐?
     */
    @Override
    public byte[] serialize(@Nullable Object t) throws SerializationException {
        if (t == null) {
            return new byte[0];
        }
        try {
            return JsonUtils.toJsonBytes(t);
        } catch (Exception e) {
            throw new SerializationException("Redis瀵硅薄搴忓垪鍖栧け璐ワ紙Jackson锛?, e);
        }
    }

    /**
     * 鍙嶅簭鍒楀寲瀛楄妭鏁扮粍
     *
     * <p>浣跨敤 {@link JsonUtils#fromJsonBytes(byte[], Class)} 灏嗗瓧鑺傛暟缁勫弽搴忓垪鍖栦负瀵硅薄銆?
     *
     * @param bytes 搴忓垪鍖栧悗鐨勫瓧鑺傛暟缁?
     * @return 鍙嶅簭鍒楀寲鍚庣殑瀵硅薄
     * @throws SerializationException 濡傛灉鍙嶅簭鍒楀寲澶辫触
     */
    @Override
    @Nullable
    public Object deserialize(@Nullable byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length <= 0) {
            return null;
        }
        try {
            return JsonUtils.fromJsonBytes(bytes, clazz);
        } catch (Exception e) {
            throw new SerializationException("Redis瀵硅薄鍙嶅簭鍒楀寲澶辫触锛圝ackson锛?, e);
        }
    }

    /**
     * 鍒涘缓鎸囧畾绫诲瀷鐨勫簭鍒楀寲鍣?
     *
     * @param type 鐩爣绫诲瀷
     * @return 搴忓垪鍖栧櫒瀹炰緥
     */
    public static JacksonRedisSerializer of(Class<?> type) {
        return new JacksonRedisSerializer(type);
    }
}