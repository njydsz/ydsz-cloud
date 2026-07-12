package com.njydsz.pmis.common.audit.mask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.njydsz.pmis.common.util.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 鏁忔劅瀛楁鑴辨晱宸ュ叿绫?
 * <p>
 * 瀹¤鍒囬潰璋冪敤鏈伐鍏峰鍏ュ弬銆佸搷搴斾腑鐨勬晱鎰熷瓧娈佃繘琛岃劚鏁忥紝閬垮厤瀵嗛挜銆佸瘑鐮併€佽瘉浠跺彿绛?
 * 鏁忔劅淇℃伅闅忓璁℃棩蹇楄惤鐩樸€傛敮鎸佷袱绉嶈劚鏁忔娴嬫柟寮忥細
 * </p>
 * <ul>
 *   <li>鍩轰簬 {@link MaskField} 娉ㄨВ鐨勫瓧娈电骇绮剧‘鎺у埗</li>
 *   <li>鍩轰簬 {@code patterns} 鍚嶇О闆嗗悎鐨勬ā绯婂尮閰嶏紙濡?password銆乻ecret銆乼oken 绛夛級</li>
 * </ul>
 *
 * <p><b>鑴辨晱瑙勫垯锛?/b></p>
 * <ul>
 *   <li>瀛楃涓诧細淇濈暀鍓嶅悗 2 浣嶏紝涓棿鐢?{@code ****} 鏇挎崲锛堥暱搴︿笉瓒虫椂鍏ㄦ浛鎹负 {@code ****}锛?/li>
 *   <li>闆嗗悎/Map锛氶€掑綊澶勭悊姣忎釜鍏冪礌</li>
 *   <li>鍏朵粬绫诲瀷锛氳浆涓?{@code ***MASKED***}</li>
 * </ul>
 *
 * <p><b>瀹夊叏绾︽潫锛?/b></p>
 * <ul>
 *   <li>绫讳负 final锛屾瀯閫犲櫒绉佹湁锛岀姝㈠疄渚嬪寲</li>
 *   <li>鑴辨晱鍓嶅厛 JSON 搴忓垪鍖栨繁鎷疯礉锛岄伩鍏嶆薄鏌撳師濮嬩笟鍔″璞?/li>
 *   <li>寰幆寮曠敤闃叉姢锛坽@code visited} 闆嗗悎锛?/li>
 *   <li>瑙ｆ瀽澶辫触鏃堕檷绾ц繑鍥炲師鍊艰€岄潪寮傚父锛屼繚璇佸璁′富娴佺▼涓嶅彈褰卞搷</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public final class SensitiveFieldMask {

    /** 鑴辨晱鏃跺瓧绗︿覆鍓嶅悗淇濈暀鐨勫瓧绗︽暟 */
    private static final int KEEP_CHARS = 2;

    /** 鎵嬫満鍙疯劚鏁忥細淇濈暀鍓?3 浣嶅拰鍚?4 浣?*/
    private static final int PHONE_PREFIX = 3;
    private static final int PHONE_SUFFIX = 4;

    /** 韬唤璇佸彿鑴辨晱锛氫繚鐣欏墠 6 浣嶅拰鍚?4 浣?*/
    private static final int IDCARD_PREFIX = 6;
    private static final int IDCARD_SUFFIX = 4;

    /** 閾惰鍗″彿鑴辨晱锛氫繚鐣欏墠 4 浣嶅拰鍚?4 浣?*/
    private static final int BANKCARD_PREFIX = 4;
    private static final int BANKCARD_SUFFIX = 4;

    /** 閭鑴辨晱锛氭湰鍦伴儴鍒嗘渶灏戜繚鐣欏瓧绗︽暟 */
    private static final int EMAIL_LOCAL_KEEP = 1;

    /** 榛樿鏁忔劅瀛楁鍚嶇О鍖归厤妯″紡锛堜笉鍖哄垎澶у皬鍐欍€佸瓙涓插尮閰嶏級 */
    private static final Set<String> DEFAULT_PATTERNS = Set.of(
            // 璁よ瘉鍑嵁绫?
            "password", "secret", "token", "credential", "apikey", "apisecret",
            "privatekey", "publickey", "salt", "auth", "sessionid", "refreshtoken",
            // 涓汉淇℃伅绫?
            "creditcard", "cardno", "cardnumber", "bankcard", "cvv", "pin",
            "idcard", "idnumber", "mobile", "phone", "email", "address",
            // 鍏朵粬鏁忔劅淇℃伅
            "passport", "license", "accountno", "accountnumber"
    );

    /** 鎵嬫満鍙峰尮閰嶆ā寮?*/
    private static final String PATTERN_MOBILE = "mobile";
    /** 鎵嬫満鍙峰尮閰嶆ā寮忥紙澶囬€夛級 */
    private static final String PATTERN_PHONE = "phone";
    /** 閭鍖归厤妯″紡 */
    private static final String PATTERN_EMAIL = "email";
    /** 韬唤璇佸尮閰嶆ā寮?*/
    private static final String PATTERN_IDCARD = "idcard";
    /** 韬唤璇佸尮閰嶆ā寮忥紙澶囬€夛級 */
    private static final String PATTERN_IDNUMBER = "idnumber";
    /** 閾惰鍗″尮閰嶆ā寮?*/
    private static final String PATTERN_BANKCARD = "bankcard";
    /** 閾惰鍗″尮閰嶆ā寮忥紙澶囬€夛級 */
    private static final String PATTERN_CARDNO = "cardno";
    /** 閾惰鍗″尮閰嶆ā寮忥紙澶囬€夛級 */
    private static final String PATTERN_CARDNUMBER = "cardnumber";

    private SensitiveFieldMask() {
        throw new UnsupportedOperationException("SensitiveFieldMask 鏄伐鍏风被锛岀姝㈠疄渚嬪寲");
    }

    /**
     * 瀵瑰璞¤繘琛岃劚鏁忓鐞?
     *
     * @param obj      寰呰劚鏁忓璞★紙涓嶄細琚慨鏀癸級
     * @param patterns 棰濆鏁忔劅瀛楁鍚嶇О闆嗗悎锛堜笌榛樿妯″紡鍚堝苟鐢熸晥锛?
     * @param enabled  鏄惁鍚敤鑴辨晱锛沠alse 鏃剁洿鎺ヨ繑鍥炲師瀵硅薄
     * @return 鑴辨晱鍚庣殑瀵硅薄鍓湰锛涘叆鍙?null 鏃惰繑鍥?null
     */
    public static Object mask(Object obj, Set<String> patterns, boolean enabled) {
        if (!enabled || obj == null) {
            return obj;
        }
        Set<String> combinedPatterns = new HashSet<>(DEFAULT_PATTERNS);
        if (patterns != null) {
            combinedPatterns.addAll(patterns);
        }
        // 鑴辨晱鍓嶅厛娣辨嫹璐濓紝閬垮厤淇敼鍘熷涓氬姟鏁版嵁
        Object copy = deepCopy(obj);
        return maskInternal(copy, combinedPatterns, new HashSet<>());
    }

    /**
     * 娣辨嫹璐濆璞★紝閫氳繃 JSON 搴忓垪鍖?鍙嶅簭鍒楀寲瀹炵幇銆?
     * <p>瀵逛笉鍙彉绫诲瀷锛圫tring/Number/Boolean/Character锛夌洿鎺ュ鐢紝涓嶅仛鎷疯礉銆?
     * 娣辨嫹璐濆け璐ユ椂闄嶇骇杩斿洖鍘熷璞★紙浠呯敤浜庡璁″睍绀猴紝涓嶅奖鍝嶅師涓氬姟锛夈€?
     *
     * @param obj 寰呮嫹璐濆璞?
     * @return 娣辨嫹璐濈粨鏋?
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object deepCopy(Object obj) {
        if (obj == null) {
            return null;
        }
        // 涓嶅彲鍙樼被鍨嬫棤闇€鎷疯礉
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean || obj instanceof Character) {
            return obj;
        }
        try {
            String json = JsonUtils.toJson(obj);
            Class<?> clazz = obj.getClass();
            // Collection/Map 绫诲瀷浣跨敤 parseArray/parseObject 淇濇寔娉涘瀷鍏煎
            if (Collection.class.isAssignableFrom(clazz)) {
                return JsonUtils.fromJsonToList(json, Object.class);
            } else if (Map.class.isAssignableFrom(clazz)) {
                return JsonUtils.fromJson(json, HashMap.class);
            }
            return JsonUtils.fromJson(json, (Class) clazz);
        } catch (Exception e) {
            // 娣辨嫹璐濆け璐ユ椂闄嶇骇杩斿洖鍘熷璞?
            log.debug("[SensitiveFieldMask] 娣辨嫹璐濆け璐ワ紝闄嶇骇杩斿洖鍘熷璞? {}", e.getMessage());
            return obj;
        }
    }

    /**
     * 瀵?JSON 瀛楃涓茶繘琛屾晱鎰熷瓧娈佃劚鏁?
     *
     * @param json     JSON 瀛楃涓?
     * @param patterns 鏁忔劅瀛楁鍚嶇О闆嗗悎
     * @return 鑴辨晱鍚庣殑 JSON 瀛楃涓诧紱瑙ｆ瀽澶辫触鏃惰繑鍥炲師 JSON
     */
    public static String maskJson(String json, Set<String> patterns) {
        if (json == null || json.isEmpty() || patterns == null || patterns.isEmpty()) {
            return json;
        }
        try {
            ObjectMapper mapper = JsonUtils.getMapper();
            JsonNode parsed = mapper.readTree(json);
            maskJsonObject(parsed, patterns, new HashSet<>());
            return mapper.writeValueAsString(parsed);
        } catch (Exception e) {
            // 瑙ｆ瀽澶辫触鏃堕檷绾ц繑鍥炲師濮?JSON
            log.debug("[SensitiveFieldMask] JSON瑙ｆ瀽澶辫触锛岄檷绾ц繑鍥炲師濮婮SON: {}", e.getMessage());
            return json;
        }
    }

    private static void maskJsonObject(Object obj, Set<String> patterns, Set<Object> visited) {
        if (obj == null || visited.contains(obj)) {
            return;
        }
        visited.add(obj);

        if (obj instanceof ObjectNode) {
            ObjectNode jsonObj = (ObjectNode) obj;
            for (Map.Entry<String, JsonNode> entry : jsonObj.properties()) {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (isSensitiveKey(key, patterns)) {
                    jsonObj.put(key, maskValue(value.asText(), key));
                } else if (value.isObject() || value.isArray()) {
                    maskJsonObject(value, patterns, visited);
                }
            }
        } else if (obj instanceof ArrayNode) {
            ArrayNode arr = (ArrayNode) obj;
            for (int i = 0; i < arr.size(); i++) {
                maskJsonObject(arr.get(i), patterns, visited);
            }
        }
    }

    /**
     * 閫掑綊鑴辨晱澶勭悊鏍稿績閫昏緫锛堝熀浜庡弽灏勫鐞?POJO 瀛楁锛?
     *
     * @param obj      寰呰劚鏁忓璞?
     * @param patterns 鏁忔劅瀛楁鍚嶇О闆嗗悎
     * @param visited  宸茶闂璞￠泦鍚堬紝鐢ㄤ簬闃叉寰幆寮曠敤
     * @return 鑴辨晱鍚庣殑瀵硅薄
     */
    private static Object maskInternal(Object obj, Set<String> patterns, Set<Object> visited) {
        if (obj == null) {
            return null;
        }
        if (visited.contains(obj)) {
            return obj;
        }
        visited.add(obj);

        if (obj instanceof String) {
            return maskValue((String) obj);
        }

        if (obj instanceof Collection) {
            Collection<?> collection = (Collection<?>) obj;
            Collection<Object> result = new java.util.ArrayList<>();
            for (Object item : collection) {
                result.add(maskInternal(item, patterns, visited));
            }
            return result;
        }

        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            Map<Object, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (key instanceof String && isSensitiveKey((String) key, patterns)) {
                    result.put(key, maskValue(String.valueOf(value), (String) key));
                } else {
                    result.put(key, maskInternal(value, patterns, visited));
                }
            }
            return result;
        }

        try {
            Class<?> clazz = obj.getClass();
            Class<?> currentClass = clazz;
            while (currentClass != null && currentClass != Object.class) {
                Field[] fields = currentClass.getDeclaredFields();
                for (Field field : fields) {
                    field.setAccessible(true);
                    if (field.isAnnotationPresent(MaskField.class) ||
                            isSensitiveKey(field.getName(), patterns)) {
                        Object fieldValue = field.get(obj);
                        // 璇诲彇 @MaskField 娉ㄨВ鐨?pattern 灞炴€э紝鐢ㄤ簬绫诲瀷鐗瑰畾鑴辨晱
                        String fieldKey = field.getName();
                        MaskField maskAnnotation = field.getAnnotation(MaskField.class);
                        if (maskAnnotation != null && !maskAnnotation.pattern().isEmpty()) {
                            fieldKey = maskAnnotation.pattern();
                        }
                        if (fieldValue instanceof String) {
                            field.set(obj, maskValue((String) fieldValue, fieldKey));
                        } else if (fieldValue != null) {
                            field.set(obj, maskInternal(fieldValue, patterns, visited));
                        }
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
        } catch (Exception ignored) {
            // 鍙嶅皠澶辫触鏃堕潤榛橀檷绾э紙浠呭奖鍝嶅璁″睍绀猴紝涓嶅奖鍝嶄笟鍔★級
        }

        return obj;
    }

    /**
     * 瀵瑰瓧绗︿覆鍊艰繘琛岃劚鏁忥紝鏍规嵁瀛楁鍚嶇О鑷姩閫夋嫨鏈€浣宠劚鏁忕瓥鐣ャ€?
     *
     * <p>鏀寔鐨勭被鍨嬬壒瀹氳劚鏁忕瓥鐣ワ細
     * <ul>
     *   <li>鎵嬫満鍙凤紙mobile/phone锛夛細淇濈暀鍓?3 鍚?4锛屽 {@code 138****1234}</li>
     *   <li>閭锛坋mail锛夛細鏈湴閮ㄥ垎浠呬繚鐣欓瀛楃锛屽 {@code z***@example.com}</li>
     *   <li>韬唤璇佸彿锛坕dcard/idnumber锛夛細淇濈暀鍓?6 鍚?4锛屽 {@code 110101********1234}</li>
     *   <li>閾惰鍗″彿锛坆ankcard/cardno/cardnumber锛夛細淇濈暀鍓?4 鍚?4锛屽 {@code 6222****1234}</li>
     *   <li>鍏朵粬鏁忔劅瀛楁锛氫繚鐣欏墠鍚?2 浣嶏紝涓棿 {@code ****}</li>
     * </ul>
     *
     * @param value 鍘熷瀛楃涓插€?
     * @return 鑴辨晱鍚庣殑瀛楃涓?
     */
    private static String maskValue(String value) {
        return maskValue(value, null);
    }

    /**
     * 瀵瑰瓧绗︿覆鍊艰繘琛岃劚鏁忥紝鏍规嵁瀛楁鍚嶇О鎴?pattern 閫夋嫨鏈€浣宠劚鏁忕瓥鐣ャ€?
     *
     * @param value    鍘熷瀛楃涓插€?
     * @param fieldKey 瀛楁鍚嶇О鎴?pattern锛堝彲涓?null锛?
     * @return 鑴辨晱鍚庣殑瀛楃涓?
     */
    private static String maskValue(String value, String fieldKey) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        // 濡傛灉鎻愪緵浜嗗瓧娈靛悕绉帮紝灏濊瘯绫诲瀷鐗瑰畾鑴辨晱
        if (fieldKey != null && !fieldKey.isEmpty()) {
            String lowerKey = fieldKey.toLowerCase();
            String masked = maskByFieldType(value, lowerKey);
            if (masked != null) {
                return masked;
            }
        }
        // 榛樿鑴辨晱绛栫暐锛氫繚鐣欏墠鍚?2 浣?
        if (value.length() <= KEEP_CHARS * 2) {
            return "****";
        }
        return value.substring(0, KEEP_CHARS) + "****" + value.substring(value.length() - KEEP_CHARS);
    }

    /**
     * 鏍规嵁瀛楁鍚嶇О绫诲瀷閫夋嫨鐗瑰畾鐨勮劚鏁忕瓥鐣ャ€?
     *
     * @param value    鍘熷鍊?
     * @param lowerKey 灏忓啓瀛楁鍚嶇О
     * @return 鑴辨晱鍚庣殑瀛楃涓诧紝涓嶅尮閰嶇被鍨嬫椂杩斿洖 null
     */
    private static String maskByFieldType(String value, String lowerKey) {
        // 鎵嬫満鍙疯劚鏁忥細淇濈暀鍓?3 鍚?4
        if (lowerKey.contains(PATTERN_MOBILE) || lowerKey.contains(PATTERN_PHONE)) {
            if (value.length() <= PHONE_PREFIX + PHONE_SUFFIX) {
                return "****";
            }
            return value.substring(0, PHONE_PREFIX) + "****" + value.substring(value.length() - PHONE_SUFFIX);
        }
        // 閭鑴辨晱锛氭湰鍦伴儴鍒嗕粎淇濈暀棣栧瓧绗?
        if (lowerKey.contains(PATTERN_EMAIL)) {
            int atIndex = value.indexOf('@');
            if (atIndex > 0) {
                String localPart = value.substring(0, atIndex);
                String domain = value.substring(atIndex);
                if (localPart.length() <= EMAIL_LOCAL_KEEP) {
                    return "*" + domain;
                }
                return localPart.charAt(0) + "***" + domain;
            }
            // 涓嶆槸閭鏍煎紡锛屼娇鐢ㄩ粯璁ょ瓥鐣?
            return null;
        }
        // 韬唤璇佸彿鑴辨晱锛氫繚鐣欏墠 6 鍚?4
        if (lowerKey.contains(PATTERN_IDCARD) || lowerKey.contains(PATTERN_IDNUMBER)) {
            if (value.length() <= IDCARD_PREFIX + IDCARD_SUFFIX) {
                return "****";
            }
            return value.substring(0, IDCARD_PREFIX) + "********" + value.substring(value.length() - IDCARD_SUFFIX);
        }
        // 閾惰鍗″彿鑴辨晱锛氫繚鐣欏墠 4 鍚?4
        if (lowerKey.contains(PATTERN_BANKCARD) || lowerKey.contains(PATTERN_CARDNO) || lowerKey.contains(PATTERN_CARDNUMBER)) {
            if (value.length() <= BANKCARD_PREFIX + BANKCARD_SUFFIX) {
                return "****";
            }
            return value.substring(0, BANKCARD_PREFIX) + "****" + value.substring(value.length() - BANKCARD_SUFFIX);
        }
        // 涓嶅尮閰嶄换浣曠壒瀹氱被鍨?
        return null;
    }

    /**
     * 鍒ゆ柇瀛楁鍚嶇О鏄惁涓烘晱鎰熷瓧娈碉紙澶у皬鍐欎笉鏁忔劅銆佸瓙涓插尮閰嶏級
     *
     * @param key      瀛楁鍚嶇О
     * @param patterns 鏁忔劅瀛楁鍚嶇О鍖归厤妯″紡闆嗗悎
     * @return 鏄晱鎰熷瓧娈佃繑鍥?true锛屽惁鍒欒繑鍥?false
     */
    private static boolean isSensitiveKey(String key, Set<String> patterns) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        for (String pattern : patterns) {
            if (lower.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
