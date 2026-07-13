package com.njydsz.pmis.common.json.merge;

import com.njydsz.pmis.common.json.parser.YdszJsonParser;

import java.util.LinkedHashMap;
import java.util.Map;
import com.njydsz.pmis.common.json.YdszJson.toJson;

/**
 * JSON Merge Patch 实现（RFC 7396）
 *
 * <p>RFC 7396 定义了一种简单的 JSON 合并算法：</p>
 * <ul>
 *   <li>patch 是对象：递归合并到 target</li>
 *   <li>patch 是 null：从 target 中删除对应字段</li>
 *   <li>patch 是其他值：替换 target 中对应字段</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * String target = "{\"a\":1,\"b\":2}";
 * String patch = "{\"b\":3,\"c\":4}";
 * String result = JsonMergePatch.merge(target, patch);
 * // result: {"a":1,"b":3,"c":4}
 *
 * // 删除字段
 * String patch2 = "{\"b\":null}";
 * String result2 = JsonMergePatch.merge(target, patch2);
 * // result2: {"a":1}
 * </pre>
 *
 * @see <a href="https://tools.ietf.org/html/rfc7396">RFC 7396</a>
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class JsonMergePatch {

    private JsonMergePatch() {
        throw new UnsupportedOperationException();
    }

    /**
     * 合并两个 JSON 字符串
     *
     * @param target 目标 JSON
     * @param patch 补丁 JSON
     * @return 合并后的 JSON 字符串
     */
    public static String merge(String target, String patch) {
        if (target == null || target.isEmpty()) {
            return patch;
        }
        if (patch == null || patch.isEmpty()) {
            return target;
        }

        Object targetObj = YdszJsonParser.parse(target);
        Object patchObj = YdszJsonParser.parse(patch);

        Object result = merge(targetObj, patchObj);
        return toJson(result);
    }

    /**
     * 合并两个对象
     *
     * @param target 目标对象
     * @param patch 补丁对象
     * @return 合并后的对象
     */
    
    public static Object merge(Object target, Object patch) {
        if (patch == null) {
            return null;
        }

        if (!(patch instanceof Map<?, ?> patchMap)) {
            return patch;
        }

        if (target instanceof Map<?, ?> targetMapRaw) {
            Map<String, Object> targetMap = new LinkedHashMap<>(targetMapRaw.size());
            for (Map.Entry<?, ?> entry : targetMapRaw.entrySet()) {
                targetMap.put((String) entry.getKey(), entry.getValue());
            }
            for (Map.Entry<?, ?> entry : patchMap.entrySet()) {
                String key = (String) entry.getKey();
                Object patchValue = entry.getValue();

                if (patchValue == null) {
                    targetMap.remove(key);
                } else {
                    Object targetValue = targetMap.get(key);
                    targetMap.put(key, merge(targetValue, patchValue));
                }
            }
            return targetMap;
        } else {
            Map<String, Object> result = new LinkedHashMap<>(patchMap.size());
            for (Map.Entry<?, ?> entry : patchMap.entrySet()) {
                result.put((String) entry.getKey(), entry.getValue());
            }
            return result;
        }
    }

    /**
     * 计算两个 JSON 的差异补丁
     *
     * @param source 源 JSON
     * @param target 目标 JSON
     * @return 差异补丁 JSON
     */
    public static String diff(String source, String target) {
        if (source == null || source.isEmpty()) {
            return target;
        }
        if (target == null || target.isEmpty()) {
            return "{}";
        }

        Object sourceObj = YdszJsonParser.parse(source);
        Object targetObj = YdszJsonParser.parse(target);

        Object diffObj = diffInternal(sourceObj, targetObj);
        return toJson(diffObj);
    }

    
    private static Object diffInternal(Object source, Object target) {
        if (source == null) {
            return target;
        }

        if (target == null) {
            return null;
        }

        if (source.equals(target)) {
            return new LinkedHashMap<>();
        }

        if (source instanceof Map<?, ?> sourceMap && target instanceof Map<?, ?> targetMap) {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : targetMap.entrySet()) {
                String key = (String) entry.getKey();
                Object targetValue = entry.getValue();
                Object sourceValue = sourceMap.get(key);

                if (sourceValue == null) {
                    result.put(key, targetValue);
                } else {
                    Object diff = diffInternal(sourceValue, targetValue);
                    if (diff != null && !(diff instanceof Map<?, ?> diffMap && diffMap.isEmpty())) {
                        result.put(key, diff);
                    }
                }
            }

            for (Object key : sourceMap.keySet()) {
                if (!targetMap.containsKey(key)) {
                    result.put((String) key, null);
                }
            }

            return result;
        }

        return target;
    }
}
