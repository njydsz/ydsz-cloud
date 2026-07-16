package com.njydsz.common.jdbc.interceptor;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.ibatis.mapping.MappedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.jdbc.config.DataPermissionConfiguration;
import com.njydsz.common.jdbc.enums.InterceptTableStrategy;
import com.njydsz.common.jdbc.permission.DataPermissionIgnore;
import com.njydsz.common.util.string.StringUtils;

import net.sf.jsqlparser.schema.Table;

/**
 *
 * @author ydsz-team
 * @since 1.0.0
 */
final class DataPermissionHelper {

    private static final Logger log = LoggerFactory.getLogger(DataPermissionHelper.class);

    /** 有界 LRU 缓存，最大容量 10000，防止内存泄漏 */
    private static final int MAX_CACHE_SIZE = 10000;
    private static final Map<String, Boolean> IGNORE_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    /**
     * 私有构造方法，工具类禁止实例化。
     */
    private DataPermissionHelper() {
    }

    static Set<String> normalizeTableSet(DataPermissionConfiguration config) {
        if (config == null || config.getTables() == null) {
            return Collections.emptySet();
        }
        return config.getTables().stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    static boolean shouldApply(Table table, DataPermissionConfiguration config, Set<String> normalizedTables) {
        if (table == null) {
            return false;
        }
        String name = normalizeTableName(table);
        if (StringUtils.isBlank(name)) {
            return false;
        }
        if (config.getInterceptTableStrategy() == InterceptTableStrategy.INCLUDE) {
            return normalizedTables.contains(name);
        }
        return !normalizedTables.contains(name);
    }

    static String normalizeTableName(Table table) {
        String name = table.getName();
        if (StringUtils.isBlank(name)) {
            return "";
        }
        if (name.contains(".")) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }
        return name.toLowerCase();
    }

    static boolean isDataPermissionIgnored(MappedStatement ms) {
        if (ms == null || ms.getId() == null) {
            return false;
        }
        String msId = ms.getId();
        Boolean cached = IGNORE_CACHE.get(msId);
        if (cached != null) {
            return cached;
        }
        boolean result = checkDataPermissionIgnored(msId);
        IGNORE_CACHE.put(msId, result);
        return result;
    }

    private static boolean checkDataPermissionIgnored(String msId) {
        try {
            int lastDot = msId.lastIndexOf('.');
            if (lastDot <= 0) {
                return false;
            }
            String className = msId.substring(0, lastDot);
            String methodName = msId.substring(lastDot + 1);
            Class<?> mapperClass = Class.forName(className);
            for (Method method : mapperClass.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    return method.isAnnotationPresent(DataPermissionIgnore.class);
                }
            }
        } catch (ClassNotFoundException | SecurityException e) {
            log.debug("无法检查数据权限注解: {} | 原因: {}", msId, e.getMessage());
        }
        return false;
    }
}
