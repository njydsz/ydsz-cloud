package com.njydsz.pmis.common.json.jsonpath;

import com.njydsz.pmis.common.json.YdszJson;
import java.util.*;
import java.util.regex.*;

/**
 * 增强的 YdszJsonPath 解析器
 *
 * 支持：
 * 1. 基础路径：$.user.name
 * 2. 数组索引：$.items[0]
 * 3. 数组过滤：$.items[?(@.price > 100)]
 * 4. 递归下降：$..author
 * 5. 数组切片：$.items[0:5]
 * 6. 多选择器：$.items[*].name
 * 7. 条件表达式：$.items[?(@.age >= 18 && @.status == 'active')]
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class YdszJsonPath {

    private final List<PathSegment> segments;

    private YdszJsonPath(String path, List<PathSegment> segments) {
        this.segments = segments;
    }

    /**
     * 编译 YdszJsonPath
     */
    public static YdszJsonPath compile(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        
        if (!path.startsWith("$")) {
            throw new IllegalArgumentException("Path must start with '$'");
        }
        
        List<PathSegment> segments = parse(path);
        return new YdszJsonPath(path, segments);
    }

    /**
     * 从对象中获取值
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
     */
    public Object getValue(String json) {
        Object obj = YdszJson.parseObject(json);
        return getValue(obj);
    }

    /**
     * 获取所有匹配的值
     */
    public List<Object> getAllValues(Object obj) {
        List<Object> results = new ArrayList<>();
        evaluate(obj, 0, results);
        return results;
    }

    /**
     * 获取所有匹配的值（从 JSON 字符串）
     */
    public List<Object> getAllValues(String json) {
        Object obj = YdszJson.parseObject(json);
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
            
            // 尝试驼峰和下划线转换
            if (value == null) {
                value = map.get(toCamelCase(segment.property));
            }
            if (value == null) {
                value = map.get(toSnakeCase(segment.property));
            }
            
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
            Object[] array = (Object[]) current;
            int index = normalizeIndex(segment.index, array.length);
            
            if (index >= 0 && index < array.length) {
                evaluate(array[index], segmentIndex + 1, results);
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
            Object[] array = (Object[]) current;
            for (Object item : array) {
                evaluate(item, segmentIndex + 1, results);
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
        
        // 处理比较操作符
        Pattern pattern = Pattern.compile("@\\.(\\w+)\\s*(==|!=|>=|<=|>|<)\\s*(['\"]?)([^'\"\\s]+)\\3");
        Matcher matcher = pattern.matcher(filter);
        
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
            if (value == null) {
                value = map.get(toCamelCase(property));
            }
            if (value == null) {
                value = map.get(toSnakeCase(property));
            }
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
                case "==": return actualNum == expectedNum;
                case "!=": return actualNum != expectedNum;
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
        YdszJsonPath jsonPath = compile(path);
        return jsonPath.getValue(json);
    }
    
    /**
     * 通过 JSONPath 从对象中提取值
     * 
     * @param obj 对象
     * @param path JSONPath 表达式
     * @return 提取的值
     */
    public static Object get(Object obj, String path) {
        YdszJsonPath jsonPath = compile(path);
        return jsonPath.getValue(obj);
    }
}
