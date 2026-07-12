package com.njydsz.pmis.common.core.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 缁熶竴API杩斿洖缁撴灉灏佽绫? *
 * <p>鐢ㄤ簬鍓嶅悗绔氦浜掔殑鏍囧噯杩斿洖鏍煎紡锛屽皝瑁呬簡鍝嶅簲鐮併€佹秷鎭€佹暟鎹拰鏃堕棿鎴炽€? *
 * <p><b>鍝嶅簲缁撴瀯锛?/b>
 * <ul>
 *   <li>code: 鍝嶅簲鐮侊紝A00000琛ㄧず鎴愬姛锛屽叾浠栬〃绀哄け璐?/li>
 *   <li>msg: 鍝嶅簲娑堟伅</li>
 *   <li>data: 鍝嶅簲鏁版嵁</li>
 *   <li>timestamp: 鍝嶅簲鏃堕棿鎴?/li>
 * </ul>
 *
 * <p><b>浣跨敤绀轰緥锛?/b>
 * <pre>{@code
 * // 杩斿洖鎴愬姛
 * return BaseResponse.success(user);
 *
 * // 杩斿洖鎴愬姛甯︽秷鎭? * return BaseResponse.success("鎿嶄綔鎴愬姛", user);
 *
 * // 杩斿洖澶辫触
 * return BaseResponse.error("鍙傛暟閿欒");
 *
 * // 杩斿洖澶辫触甯﹂敊璇爜
 * return BaseResponse.error("A01002", "鐢ㄦ埛鍚嶅凡瀛樺湪");
 * }</pre>
 *
 * @param <T> 鏁版嵁娉涘瀷
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see IResponse
 * @see PageResponse
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "msg", "data", "timestamp"})
public class BaseResponse<T> implements IResponse<T>, Serializable {

    private static final long serialVersionUID = 3L;

    /**
     * 鎴愬姛鐘舵€佺爜
     */
    public static final String SUCCESS = "A00000";

    /**
     * 澶辫触鐘舵€佺爜
     */
    public static final String ERROR = "A01001";

    /**
     * 鍥介檯鍖栨秷鎭?key
     */
    public static final String MSG_OPERATION_SUCCESS = "response.success";

    /**
     * 鎿嶄綔澶辫触鍥介檯鍖栨秷鎭?key
     */
    public static final String MSG_OPERATION_FAIL = "response.error";

    /**
     * 杩斿洖缂栫爜
     */
    @EqualsAndHashCode.Include
    private String code;

    /**
     * 杩斿洖淇℃伅
     */
    private String msg;

    /**
     * 杩斿洖鏁版嵁
     *
     * <p>娉涘瀷绫诲瀷 T 鏃犳硶闄愬畾涓?Serializable锛圓PI 鍝嶅簲鍙惡甯︿换鎰忕被鍨嬫暟鎹級锛?     * Java 搴忓垪鍖栭潪涓昏搴忓垪鍖栨柟寮忥紙椤圭洰浣跨敤 Jackson JSON锛夛紝姝ゅ鎶戝埗缂栬瘧鍣ㄨ鍛娿€?     */
    @SuppressWarnings("serial")
    private T data;

    /**
     * 鏃堕棿鎴?     */
    private Long timestamp;

    /**
     * 鏃堕挓鎻愪緵鑰?- 浣跨敤 AtomicReference 淇濊瘉绾跨▼瀹夊叏鍜屾€ц兘
     * <p>鐩告瘮 volatile 瀛楁锛孉tomicReference 鎻愪緵鏇村ソ鐨勫唴瀛樺彲瑙佹€ц涔夊拰鏇翠綆鐨勮鍙栧紑閿€
     */
    private static final AtomicReference<Clock> CLOCK_HOLDER = 
        new AtomicReference<>(Clock.systemDefaultZone());

    /**
     * 榛樿鏋勯€犲嚱鏁?     */
    public BaseResponse() {
        this.timestamp = CLOCK_HOLDER.get().millis();
    }

    /**
     * 鍏ㄥ弬鏁版瀯閫犲嚱鏁?     *
     * @param code 鍝嶅簲鐮?     * @param msg 鍝嶅簲娑堟伅
     * @param data 鍝嶅簲鏁版嵁
     */
    public BaseResponse(String code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.timestamp = CLOCK_HOLDER.get().millis();
    }

    /**
     * 璁剧疆鏃堕挓锛堢敤浜庡崟鍏冩祴璇曪級
     *
     * @param clock 鏃堕挓瀹炰緥
     */
    public static void setClock(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock cannot be null");
        }
        CLOCK_HOLDER.set(clock);
    }

    /**
     * 鑾峰彇褰撳墠鏃堕挓锛堢敤浜庢祴璇曢獙璇侊級
     *
     * @return 褰撳墠鏃堕挓瀹炰緥
     */
    public static Clock getClock() {
        return CLOCK_HOLDER.get();
    }

    /**
     * 鍒涘缓BaseResponse瀹炰緥
     *
     * @param code 鐘舵€佺爜
     * @param msg 娑堟伅
     * @param data 鏁版嵁
     * @param <T> 鏁版嵁绫诲瀷
     * @return BaseResponse瀹炰緥
     */
    public static <T> BaseResponse<T> of(String code, String msg, T data) {
        return new BaseResponse<>(code, msg, data);
    }

    /**
     * 杩斿洖鎴愬姛娑堟伅
     *
     * @param <T> 鏁版嵁绫诲瀷
     * @return 鎴愬姛娑堟伅
     */
    public static <T> BaseResponse<T> success() {
        return of(SUCCESS, resolveMessage(MSG_OPERATION_SUCCESS, "鎿嶄綔鎴愬姛"), null);
    }

    /**
     * 杩斿洖鎴愬姛鏁版嵁
     *
     * @param data 鏁版嵁鍐呭
     * @param <T> 鏁版嵁绫诲瀷
     * @return 鎴愬姛娑堟伅
     */
    public static <T> BaseResponse<T> success(T data) {
        return of(SUCCESS, resolveMessage(MSG_OPERATION_SUCCESS, "鎿嶄綔鎴愬姛"), data);
    }

    /**
     * 杩斿洖鎴愬姛娑堟伅
     *
     * @param msg 娑堟伅鍐呭
     * @param <T> 鏁版嵁绫诲瀷
     * @return 鎴愬姛娑堟伅
     */
    public static <T> BaseResponse<T> successMsg(String msg) {
        BaseResponse<T> response = new BaseResponse<>();
        response.code = SUCCESS;
        response.msg = msg;
        return response;
    }

    /**
     * 杩斿洖鎴愬姛娑堟伅
     *
     * @param msg 娑堟伅鍐呭
     * @param data 鏁版嵁鍐呭
     * @param <T> 鏁版嵁绫诲瀷
     * @return 鎴愬姛娑堟伅
     */
    public static <T> BaseResponse<T> success(String msg, T data) {
        return of(SUCCESS, msg, data);
    }

    /**
     * 杩斿洖澶辫触娑堟伅
     *
     * @param <T> 鏁版嵁绫诲瀷
     * @return 澶辫触娑堟伅
     */
    public static <T> BaseResponse<T> error() {
        return of(ERROR, resolveMessage(MSG_OPERATION_FAIL, "鎿嶄綔澶辫触"), null);
    }

    /**
     * 杩斿洖澶辫触娑堟伅
     *
     * @param msg 娑堟伅鍐呭
     * @param <T> 鏁版嵁绫诲瀷
     * @return 澶辫触娑堟伅
     */
    public static <T> BaseResponse<T> error(String msg) {
        return of(ERROR, msg, null);
    }

    /**
     * 杩斿洖澶辫触娑堟伅
     *
     * @param msg 娑堟伅鍐呭
     * @param data 鏁版嵁鍐呭
     * @param <T> 鏁版嵁绫诲瀷
     * @return 澶辫触娑堟伅
     */
    public static <T> BaseResponse<T> error(String msg, T data) {
        return of(ERROR, msg, data);
    }

    /**
     * 杩斿洖澶辫触娑堟伅
     *
     * @param code 閿欒鐮?     * @param msg 娑堟伅鍐呭
     * @param <T> 鏁版嵁绫诲瀷
     * @return 澶辫触娑堟伅
     */
    public static <T> BaseResponse<T> error(String code, String msg) {
        return of(code, msg, null);
    }

    /**
     * 杩斿洖澶辫触娑堟伅
     *
     * @param code 閿欒鐮?     * @param msg 娑堟伅鍐呭
     * @param data 鏁版嵁鍐呭
     * @param <T> 鏁版嵁绫诲瀷
     * @return 澶辫触娑堟伅
     */
    public static <T> BaseResponse<T> error(String code, String msg, T data) {
        return of(code, msg, data);
    }

    /**
     * 鍥介檯鍖栨秷鎭В鏋愬櫒鎺ュ彛
     */
    @FunctionalInterface
    public interface MessageResolver {
        /**
         * 瑙ｆ瀽鍥介檯鍖栨秷鎭?         *
         * @param key 鍥介檯鍖栨秷鎭?key
         * @param defaultValue 榛樿娑堟伅鏂囨湰
         * @return 瑙ｆ瀽鍚庣殑娑堟伅鍐呭
         */
        String resolve(String key, String defaultValue);
    }

    /**
     * 娑堟伅瑙ｆ瀽鍣ㄥ疄渚嬶紙volatile 淇濊瘉澶氱嚎绋嬪彲瑙佹€э級
     */
    private static volatile MessageResolver resolver;

    /**
     * 璁剧疆鍏ㄥ眬娑堟伅瑙ｆ瀽鍣紙鍙鐩栵級
     *
     * <p>鐢变笂灞傚簲鐢紙濡?Spring Boot 鍚姩绫绘垨閰嶇疆绫伙級璋冪敤锛屾敞鍏ュ浗闄呭寲瑙ｆ瀽瀹炵幇銆?     * 鍚庣画璋冪敤灏嗚鐩栦箣鍓嶈缃殑瑙ｆ瀽鍣紝浠ユ渶鍚庝竴娆¤缃负鍑嗐€?     *
     * @param resolver 娑堟伅瑙ｆ瀽鍣ㄥ疄鐜?     */
    public static void setResolver(MessageResolver resolver) {
        BaseResponse.resolver = resolver;
    }

    /**
     * 瑙ｆ瀽鍥介檯鍖栨秷鎭紝鑻ユ湭璁剧疆瑙ｆ瀽鍣ㄥ垯杩斿洖榛樿鍊?     *
     * @param key 鍥介檯鍖栨秷鎭?key
     * @param defaultValue 榛樿娑堟伅鏂囨湰
     * @return 瑙ｆ瀽鍚庣殑娑堟伅鍐呭
     */
    protected static String resolveMessage(String key, String defaultValue) {
        MessageResolver currentResolver = resolver;
        if (currentResolver != null) {
            String result = currentResolver.resolve(key, defaultValue);
            return result != null ? result : defaultValue;
        }
        return defaultValue;
    }

    /**
     * 杩斿洖澶辫触娑堟伅
     *
     * @param resultCode 缁撴灉鐮?     * @param <T> 鏁版嵁绫诲瀷
     * @return 澶辫触娑堟伅
     */
    public static <T> BaseResponse<T> error(ResultCode resultCode) {
        return of(resultCode.getCode(), resultCode.getMsg(), null);
    }

    /**
     * 杩斿洖澶辫触娑堟伅
     *
     * @param resultCode 缁撴灉鐮?     * @param data 鏁版嵁鍐呭
     * @param <T> 鏁版嵁绫诲瀷
     * @return 澶辫触娑堟伅
     */
    public static <T> BaseResponse<T> error(ResultCode resultCode, T data) {
        return of(resultCode.getCode(), resultCode.getMsg(), data);
    }

    /**
     * 杩斿洖澶辫触娑堟伅
     *
     * @param resultCode 缁撴灉鐮?     * @param msg 鑷畾涔夋秷鎭紙瑕嗙洊 ResultCode 榛樿娑堟伅锛?     * @param <T> 鏁版嵁绫诲瀷
     * @return 澶辫触娑堟伅
     */
    public static <T> BaseResponse<T> error(ResultCode resultCode, String msg) {
        return of(resultCode.getCode(), msg, null);
    }

    /**
     * 鍒ゆ柇鏄惁鎴愬姛
     *
     * @return 鎴愬姛杩斿洖true锛屽惁鍒欒繑鍥瀎alse
     */
    @Override
    public boolean isSuccess() {
        return SUCCESS.equals(this.code);
    }
}
