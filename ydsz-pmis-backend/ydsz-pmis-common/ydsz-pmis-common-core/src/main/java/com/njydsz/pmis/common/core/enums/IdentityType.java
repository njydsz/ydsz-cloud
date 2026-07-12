package com.njydsz.pmis.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 韬唤绫诲瀷鏋氫妇
 *
 * <p>瀹氫箟绯荤粺涓敤鎴风殑韬唤绫诲瀷锛岀敤浜庡尯鍒嗕笉鍚岀骇鍒殑鐢ㄦ埛璁块棶鏉冮檺銆?
 * 鏀寔鐟炵背杞欢璐﹀彿銆侀泦鍥㈠叕鍙歌处鎴枫€佹父瀹綋楠岃处鍙蜂笁绉嶇被鍨嬨€?
 *
 * <p><b>浣跨敤鍦烘櫙锛?/b>
 * <ul>
 *   <li>閰嶅悎 HeaderConstants.X_IDENTITY_TYPE 璇锋眰澶翠娇鐢?/li>
 *   <li>鍖哄垎涓嶅悓韬唤鐢ㄦ埛鐨勮闂帶鍒跺拰鏁版嵁鏉冮檺</li>
 *   <li>鐢ㄦ埛娉ㄥ唽鍜岀櫥褰曟椂鐨勮韩浠介獙璇?/li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see com.njydsz.pmis.common.core.constant.HeaderConstants
 */
@Getter
@AllArgsConstructor
public enum IdentityType implements TypeEnum<String> {

    njydsz.pmis("njydsz.pmis", "鐟炵背杞欢璐﹀彿"),
    COMPANY("company", "闆嗗洟鍏徃璐︽埛"),
    VISITOR("visitor", "娓稿浣撻獙璐﹀彿");

    /** 韬唤绫诲瀷缂栫爜 */
    private final String code;
    /** 韬唤绫诲瀷鎻忚堪 */
    private final String desc;

    private static final Map<String, IdentityType> CODE_MAP;

    static {
        CODE_MAP = Collections.unmodifiableMap(
                Arrays.stream(values())
                        .collect(Collectors.toMap(IdentityType::getCode, Function.identity()))
        );
    }

    /**
     * 鏍规嵁缂栫爜鑾峰彇韬唤绫诲瀷锛堝畨鍏ㄧ増鏈級
     *
     * @param code 缂栫爜鍊?
     * @return 瀵瑰簲鐨勬灇涓惧€硷紝鏈壘鍒拌繑鍥?null
     */
    public static IdentityType of(String code) {
        if (code == null) {
            return null;
        }
        return CODE_MAP.get(code);
    }

    /**
     * 鏍规嵁缂栫爜鑾峰彇韬唤绫诲瀷
     *
     * @param code 缂栫爜鍊?
     * @return 瀵瑰簲鐨勬灇涓惧€?
     * @throws IllegalArgumentException 褰撶紪鐮佷笉瀛樺湪鏃舵姏鍑?
     */
    public static IdentityType codeOf(String code) {
        IdentityType value = of(code);
        if (value == null) {
            throw new IllegalArgumentException("Unknown IdentityType code: " + code);
        }
        return value;
    }

    /**
     * 妫€鏌ョ紪鐮佹槸鍚︽湁鏁?
     *
     * @param code 缂栫爜鍊?
     * @return 鏈夋晥杩斿洖 true锛屽惁鍒欒繑鍥?false
     */
    public static boolean isValidCode(String code) {
        return of(code) != null;
    }
}