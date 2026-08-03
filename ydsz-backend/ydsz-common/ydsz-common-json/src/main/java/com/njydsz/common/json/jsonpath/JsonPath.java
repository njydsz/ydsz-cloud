package com.njydsz.common.json.jsonpath;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.parser.JsonParserUtil;

/**
 * 增强的 JsonPath 解析器
 *
 * <p>支持：
 * <ol>
 *   <li>基础路径：$.user.name</li>
 *   <li>数组索引：$.items[0]</li>
 *   <li>数组过滤：$.items[?(@.price > 100)]</li>
 *   <li>递归下降：$..author</li>
 *   <li>数组切片：$.items[0:5]</li>
 *   <li>多选择器：$.items[*].name</li>
 *   <li>条件表达式：$.items[?(@.age >= 18 && @.status == 'active')]</li>
 * </ol>
 *
 * <p><b>性能优化：</b>编译后的 {@code JsonPath} 实例和正则表达式均被缓存，
 * 避免每次调用重复解析路径表达式和编译正则。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class JsonPath {

    /**
     * 编译缓存（path -> JsonPath），LRU 有界避免内存泄漏。
     * 对标 Jayway JsonPath 的编译缓存机制。
     */
    private static final int COMPILE_CACHE_MAX = 512;
    private static final Map<String, JsonPath> COMPILE_CACHE =
        Collections.synchronizedMap(new LinkedHashMap<String, JsonPath>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, JsonPath> eldest) {
                return size() > COMPILE_CACHE_MAX;
            }
        });

    /**
     * 过滤表达式正则（预编译，避免每次调用 Pattern.compile）。
     * 对标 Jackson 的预编译 Pattern 常量。
     */
    private static final Pattern FILTER_PATTERN = Pattern.compile(
        "@\\.(\\w+)\\s*(==|!=|>=|<=|>|<)\\s*(['\"]?)([^'\"\\s]+)\\3");

    private final List<PathSegment> segments;

    private JsonPath(String path, List<PathSegment> segments) {
        this.segments = segments;
    }

    /**
     * 编译 JSONPath 表达式
     *
     * @param path JSONPath 表达式（必须以 $ 开头）
     * @return 编译后的 JsonPath 实例
     * @throws IllegalArgumentException 如果路径格式无效
     */
    public static JsonPath compile(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        if (!path.startsWith("$")) {
            throw new IllegalArgumentException("Path must start with '$'");
        }

        // 编译缓存：命中则直接返回，避免重复解析路径表达式
        JsonPath cached = COMPILE_CACHE.get(path);
        if (cached != null) {
            return cached;
        }

        List<PathSegment> segments = parse(path);
        JsonPath compiled = new JsonPath(path, segments);
        COMPILE_CACHE.putIfAbsent(path, compiled);
        return compiled;
    }

    /**
     * 从对象中获取值
     *
     * @param obj 要查询的对象
     * @return 匹配的值，如果路径不存在返回 null，多个结果返回 List
     */
    public Object getValue(Object obj) {
        if (obj == null) {
            return null;
        }
        
        List<Object> results = new ArrayList<>();
        evaluate(obj, 0, results);
        
        if (results.isEmpty()) {
            return null;
        }
        
        if (results.size() == 1) {
            return results.get(0);
        }
        
        return results;
    }

    /**
     * 从 JSON 字符串中获取值
     *
     * @param json JSON 字符串
     * @return 匹配的值，如果路径不存在返回 null
     */
    public Object getValue(String json) {
        Object obj = YdszJson.parseObjectToJsonObject(json);
        return getValue(obj);
    }

    /**
     * 获取所有匹配的值
     *
     * @param obj 要查询的对象
     * @return 所有匹配值的列表
     */
    public List<Object> getAllValues(Object obj) {
        List<Object> results = new ArrayList<>();
        evaluate(obj, 0, results);
        return results;
    }

    /**
     * 获取所有匹配的值（从 JSON 字符串）
     *
     * @param json JSON 字符串
     * @return 所有匹配值的列表
     */
    public List<Object> getAllValues(String json) {
        Object obj = YdszJson.parseObjectToJsonObject(json);
        return getAllValues(obj);
    }

    private void evaluate(Object current, int segmentIndex, List<Object> results) {
        if (segmentIndex >= segments.size()) {
            if (current != null) {
                results.add(current);
            }
            return;
        }

        PathSegment segment = segments.get(segmentIndex);
        
        switch (segment.type) {
            case PROPERTY:
                evaluateProperty(current, segment, segmentIndex, results);
                break;
            case ARRAY_INDEX:
                evaluateArrayIndex(current, segment, segmentIndex, results);
                break;
            case ARRAY_SLICE:
                evaluateArraySlice(current, segment, segmentIndex, results);
                break;
            case ARRAY_FILTER:
                evaluateArrayFilter(current, segment, segmentIndex, results);
                break;
            case RECURSIVE:
                evaluateRecursive(current, null, segmentIndex, results);
                break;
            case WILDCARD:
                evaluateWildcard(current, segment, segmentIndex, results);
                break;
        }
    }

    private void evaluateProperty(Object current, PathSegment segment, int segmentIndex, List<Object> results) {
        if (current instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) current;
            Object value = map.get(segment.property);
            
            // 精确字段名匹配（不做隐式驼峰/下划线回退，避免安全策略绕过）
            // 如需兼容命名风格差异，请在查询时使用精确字段名
            
            if (value != null) {
                evaluate(value, segmentIndex + 1, results);
            }
        }
    }

    private void evaluateArrayIndex(Object current, PathSegment segment, int segmentIndex, List<Object> results) {
        if (current instanceof List) {
            List<?> list = (List<?>) current;
            int index = normalizeIndex(segment.index, list.size());
            
            if (index >= 0 && index < list.size()) {
                evaluate(list.get(index), segmentIndex + 1, results);
            }
        } else if (current.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(current);
            int index = normalizeIndex(segment.index, len);
            if (index >= 0 && index < len) {
                evaluate(java.lang.reflect.Array.get(current, index), segmentIndex + 1, results);
            }
        }
    }

    private void evaluateArraySlice(Object current, PathSegment segment, int segmentIndex, List<Object> results) {
        if (!(current instanceof List)) {
            return;
        }
        
        List<?> list = (List<?>) current;
        int start = normalizeIndex(segment.sliceStart, list.size());
        int end = segment.sliceEnd != null ? 
            normalizeIndex(segment.sliceEnd, list.size()) : list.size();
        
        for (int i = start; i < Math.min(end, list.size()); i++) {
            evaluate(list.get(i), segmentIndex + 1, results);
        }
    }

    private void evaluateArrayFilter(Object current, PathSegment segment, int segmentIndex, List<Object> results) {
        if (!(current instanceof List)) {
            return;
        }
        
        List<?> list = (List<?>) current;
        
        for (Object item : list) {
            if (matchesFilter(item, segment.filter)) {
                evaluate(item, segmentIndex + 1, results);
            }
        }
    }

    private void evaluateRecursive(Object current, PathSegment segment, int segmentIndex, List<Object> results) {
        if (segmentIndex >= segments.size()) {
            return;
        }
        
        PathSegment targetSegment = segments.get(segmentIndex);
        
        // 深度优先搜索所有匹配
        searchRecursive(current, targetSegment, segmentIndex, results);
    }

    private void searchRecursive(Object current, PathSegment targetSegment, int segmentIndex, List<Object> results) {
        if (current == null) {
            return;
        }
        
        // 尝试匹配当前节点
        if (targetSegment.type == PathSegmentType.PROPERTY && current instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) current;
            Object value = map.get(targetSegment.property);
            if (value != null) {
                evaluate(value, segmentIndex + 1, results);
            }
        }
        
        // 递归搜索子节点
        if (current instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) current;
            for (Object value : map.values()) {
                searchRecursive(value, targetSegment, segmentIndex, results);
            }
        } else if (current instanceof List) {
            List<?> list = (List<?>) current;
            for (Object item : list) {
                searchRecursive(item, targetSegment, segmentIndex, results);
            }
        }
    }

    private void evaluateWildcard(Object current, PathSegment segment, int segmentIndex, List<Object> results) {
        if (current instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) current;
            for (Object value : map.values()) {
                evaluate(value, segmentIndex + 1, results);
            }
        } else if (current instanceof List) {
            List<?> list = (List<?>) current;
            for (Object item : list) {
                evaluate(item, segmentIndex + 1, results);
            }
        } else if (current.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(current);
            for (int i = 0; i < len; i++) {
                evaluate(java.lang.reflect.Array.get(current, i), segmentIndex + 1, results);
            }
        }
    }

    private boolean matchesFilter(Object item, String filter) {
        if (filter == null || filter.trim().isEmpty()) {
            return true;
        }
        
        // 解析过滤条件
        filter = filter.trim();
        
        // 处理 && 和 ||
        if (filter.contains("&&")) {
            String[] parts = filter.split("&&");
            for (String part : parts) {
                if (!matchesFilter(item, part.trim())) {
                    return false;
                }
            }
            return true;
        }
        
        if (filter.contains("||")) {
            String[] parts = filter.split("\\|\\|");
            for (String part : parts) {
                if (matchesFilter(item, part.trim())) {
                    return true;
                }
            }
            return false;
        }
        
        // 处理比较操作符（使用预编译的 static final Pattern）
        Matcher matcher = FILTER_PATTERN.matcher(filter);
        
        if (matcher.matches()) {
            String property = matcher.group(1);
            String operator = matcher.group(2);
            String value = matcher.group(4);
            
            Object actualValue = getPropertyValue(item, property);
            return compare(actualValue, operator, value);
        }
        
        return false;
    }

    private Object getPropertyValue(Object obj, String property) {
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            Object value = map.get(property);
            return value;
        }
        return null;
    }

    private boolean compare(Object actual, String operator, String expected) {
        if (actual == null) {
            return false;
        }
        
        // 尝试数值比较
        try {
            double actualNum = Double.parseDouble(actual.toString());
            double expectedNum = Double.parseDouble(expected);
            
            switch (operator) {
                case "==": return Double.compare(actualNum, expectedNum) == 0;
                case "!=": return Double.compare(actualNum, expectedNum) != 0;
                case ">": return actualNum > expectedNum;
                case "<": return actualNum < expectedNum;
                case ">=": return actualNum >= expectedNum;
                case "<=": return actualNum <= expectedNum;
            }
        } catch (NumberFormatException e) {
            // 字符串比较
            String actualStr = actual.toString();
            switch (operator) {
                case "==": return actualStr.equals(expected);
                case "!=": return !actualStr.equals(expected);
                case ">": return actualStr.compareTo(expected) > 0;
                case "<": return actualStr.compareTo(expected) < 0;
                case ">=": return actualStr.compareTo(expected) >= 0;
                case "<=": return actualStr.compareTo(expected) <= 0;
            }
        }
        
        return false;
    }

    private int normalizeIndex(int index, int size) {
        if (index < 0) {
            return size + index;
        }
        return index;
    }

    private static String toCamelCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static String toSnakeCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static List<PathSegment> parse(String path) {
        List<PathSegment> segments = new ArrayList<>();
        
        // 移除开头的 $
        String remaining = path.substring(1);
        
        while (!remaining.isEmpty()) {
            if (remaining.startsWith("..")) {
                // 递归下降
                remaining = remaining.substring(2);
                int end = findSegmentEnd(remaining);
                String prop = remaining.substring(0, end);
                segments.add(new PathSegment(PathSegmentType.RECURSIVE, prop));
                remaining = remaining.substring(end);
            } else if (remaining.startsWith(".")) {
                // 属性访问
                remaining = remaining.substring(1);
                int end = findSegmentEnd(remaining);
                String prop = remaining.substring(0, end);
                segments.add(new PathSegment(PathSegmentType.PROPERTY, prop));
                remaining = remaining.substring(end);
            } else if (remaining.startsWith("[")) {
                // 数组访问
                int end = remaining.indexOf(']');
                if (end == -1) {
                    throw new IllegalArgumentException("Unclosed bracket in path: " + path);
                }
                
                String arrayPart = remaining.substring(1, end);
                
                if (arrayPart.equals("*")) {
                    segments.add(new PathSegment(PathSegmentType.WILDCARD));
                } else if (arrayPart.startsWith("?")) {
                    // 过滤
                    String filter = arrayPart.substring(2);
                    segments.add(new PathSegment(PathSegmentType.ARRAY_FILTER, filter));
                } else if (arrayPart.contains(":")) {
                    // 切片
                    String[] parts = arrayPart.split(":");
                    int start = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
                    int sliceEnd = parts[1].isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(parts[1]);
                    segments.add(new PathSegment(PathSegmentType.ARRAY_SLICE, start, sliceEnd));
                } else {
                    // 索引
                    int index = Integer.parseInt(arrayPart.trim());
                    segments.add(new PathSegment(PathSegmentType.ARRAY_INDEX, index));
                }
                
                remaining = remaining.substring(end + 1);
            } else {
                break;
            }
            
            if (remaining.startsWith(".")) {
                continue;
            }
        }
        
        return segments;
    }

    private static int findSegmentEnd(String str) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '.' || c == '[') {
                return i;
            }
        }
        return str.length();
    }

    private enum PathSegmentType {
        PROPERTY,
        ARRAY_INDEX,
        ARRAY_SLICE,
        ARRAY_FILTER,
        RECURSIVE,
        WILDCARD
    }

    private static class PathSegment {
        final PathSegmentType type;
        final String property;
        final int index;
        final Integer sliceStart;
        final Integer sliceEnd;
        final String filter;

        PathSegment(PathSegmentType type) {
            this(type, null, -1, null, null, null);
        }

        PathSegment(PathSegmentType type, String property) {
            this(type, property, -1, null, null, null);
        }

        PathSegment(PathSegmentType type, int index) {
            this(type, null, index, null, null, null);
        }

        PathSegment(PathSegmentType type, int sliceStart, int sliceEnd) {
            this(type, null, -1, sliceStart, sliceEnd, null);
        }

        PathSegment(PathSegmentType type, String property, int index, Integer sliceStart, Integer sliceEnd, String filter) {
            this.type = type;
            this.property = property;
            this.index = index;
            this.sliceStart = sliceStart;
            this.sliceEnd = sliceEnd;
            this.filter = filter;
        }
    }
    
    /**
     * 通过 JSONPath 从 JSON 字符串中提取值
     * 
     * @param json JSON 字符串
     * @param path JSONPath 表达式
     * @return 提取的值
     */
    public static Object get(String json, String path) {
        return compile(path).getValue(json);
    }
    
    /**
     * 通过 JSONPath 从对象中提取值
     * 
     * @param obj 对象
     * @param path JSONPath 表达式
     * @return 提取的值
     */
    public static Object get(Object obj, String path) {
        return compile(path).getValue(obj);
    }

    /**
     * 清除编译缓存（用于测试或配置变更场景）。
     *
     * @since 1.0.0
     */
    public static void clearCache() {
        COMPILE_CACHE.clear();
    }

    /**
     * 获取 JSONPath 结果集的长度。
     *
     * @param json JSON 字符串
     * @param path JSONPath 表达式
     * @return 结果集大小，路径不存在返回 0
     */
    public static int length(String json, String path) {
        return compile(path).getResults(json).size();
    }

    /**
     * 获取 JSONPath 结果集中数值的最大值。
     *
     * @param json JSON 字符串
     * @param path JSONPath 表达式
     * @return 最大值，无结果返回 Double.NaN
     */
    public static double max(String json, String path) {
        List<Object> results = compile(path).getResults(json);
        double maxVal = Double.NEGATIVE_INFINITY;
        for (Object r : results) {
            double v = toDouble(r);
            if (v > maxVal) {
                maxVal = v;
            }
        }
        return maxVal == Double.NEGATIVE_INFINITY ? Double.NaN : maxVal;
    }

    /**
     * 获取 JSONPath 结果集中数值的最小值。
     *
     * @param json JSON 字符串
     * @param path JSONPath 表达式
     * @return 最小值，无结果返回 Double.NaN
     */
    public static double min(String json, String path) {
        List<Object> results = compile(path).getResults(json);
        double minVal = Double.POSITIVE_INFINITY;
        for (Object r : results) {
            double v = toDouble(r);
            if (v < minVal) {
                minVal = v;
            }
        }
        return minVal == Double.POSITIVE_INFINITY ? Double.NaN : minVal;
    }

    /**
     * 获取 JSONPath 结果集中数值的总和。
     *
     * @param json JSON 字符串
     * @param path JSONPath 表达式
     * @return 总和，无结果返回 0
     */
    public static double sum(String json, String path) {
        List<Object> results = compile(path).getResults(json);
        double total = 0.0;
        for (Object r : results) {
            total += toDouble(r);
        }
        return total;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0;
    }

    @SuppressWarnings("unchecked")
    private List<Object> getResults(String json) {
        Object parsed = JsonParserUtil.parse(json);
        List<Object> results = new ArrayList<>();
        evaluate(parsed, 0, results);
        return results;
    }
}
