package com.njydsz.common.jdbc.interceptor;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.sf.jsqlparser.schema.Table;
import org.apache.ibatis.mapping.MappedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.jdbc.config.DataPermissionConfiguration;
import com.njydsz.common.jdbc.enums.InterceptTableStrategy;
import com.njydsz.common.jdbc.permission.DataPermissionBypass;
import com.njydsz.common.jdbc.permission.DataPermissionIgnore;
import com.njydsz.common.util.string.StringUtils;

/**
 * 数据权限辅助工具类。
 *
 * <p>为 {@link DataPermissionInnerInterceptor} 和 {@link RowPermissionInnerInterceptor}
 * 提供共享的数据权限判断逻辑，包括：
 * <ul>
 *   <li>检测 Mapper 方法是否标注了 {@link DataPermissionIgnore}（跳过数据权限拦截）</li>
 *   <li>解析 SQL 中的表名与数据权限规则匹配</li>
 *   <li>缓存忽略标记结果（有界 10000 条），避免重复反射扫描</li>
 * </ul>
 *
 * <h3>缓存设计</h3>
 * <p>使用 ydsz-common-cache 缓存方法级忽略标记，最大容量 10000 条，防止内存泄漏，
 * 并避免 {@code Collections.synchronizedMap} 全局锁带来的并发竞争。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DataPermissionInnerInterceptor
 * @see DataPermissionIgnore
 */
final class DataPermissionHelper {

    private static final Logger log = LoggerFactory.getLogger(DataPermissionHelper.class);

    /** 有界缓存最大容量 10000，防止内存泄漏 */
    private static final int MAX_CACHE_SIZE = 10000;
    private static final Cache<String, Boolean> IGNORE_CACHE =
            YdszCache.<String, Boolean>newBuilder().maximumSize(MAX_CACHE_SIZE).build();

    /**
     * 私有构造方法，工具类禁止实例化。
     */
    private DataPermissionHelper() {
    }

    /**
     * 规范化表名集合（转小写 + 去空）。
     *
     * @param config 数据权限配置
     * @return 规范化后的表名集合（配置为空时返回空集合）
     */
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

    /**
     * 判断表是否应应用数据权限。
     *
     * @param table            SQL 表对象
     * @param config           数据权限配置
     * @param normalizedTables 规范化后的表名集合
     * @return 是否应应用数据权限
     */
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

    /**
     * 规范化表名（去点号后缀 + 转小写）。
     *
     * @param table SQL 表对象
     * @return 规范化后的表名（空输入时返回空字符串）
     */
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

    /**
     * 检查当前线程是否激活了数据权限绕过（系统级绕过，适用于定时任务等无用户上下文场景）。
     *
     * @return 绕过激活时返回 true
     */
    static boolean isBypassActive() {
        return DataPermissionBypass.isActive();
    }

    /**
     * 检查 Mapper 方法是否标注了 {@link DataPermissionIgnore} 注解。
     *
     * <p>使用 ydsz-common-cache 缓存（最大 10000 条）避免重复反射扫描。
     *
     * @param ms MyBatis MappedStatement
     * @return 是否应忽略数据权限拦截
     */
    static boolean isDataPermissionIgnored(MappedStatement ms) {
        if (ms == null || ms.getId() == null) {
            return false;
        }
        String msId = ms.getId();
        return IGNORE_CACHE.get(msId, DataPermissionHelper::checkDataPermissionIgnored);
    }

    /**
     * 检查 Mapper 方法是否标注了 {@link DataPermissionIgnore} 注解（反射扫描）。
     *
     * @param msId MappedStatement ID（格式：{@code package.ClassName.methodName}）
     * @return 是否应忽略数据权限拦截
     */
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
