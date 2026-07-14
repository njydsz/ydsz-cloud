ackage com.njydsz.pmis.common.auth.aspect;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.util.ReflectionUtils;

import com.njydsz.pmis.common.auth.annotation.AuthColPermission;
import com.njydsz.pmis.common.auth.config.AuthProperties;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.auth.desensitize.ColumnDesensitizationService;
import com.njydsz.pmis.common.auth.model.ColumnPermission;
import com.njydsz.pmis.common.auth.model.ColumnPermissionInfo;
import com.njydsz.pmis.common.auth.model.ColumnScopeAware;
import com.njydsz.pmis.common.auth.model.ColumnScopeInfo;
import com.njydsz.pmis.common.auth.service.ColumnPermissionResolver;
import com.njydsz.pmis.common.auth.util.AuthColPermissionSigner;
import com.njydsz.pmis.common.core.constant.HeaderConstants;
import com.njydsz.pmis.common.safe.desensitize.ColumnDesensitizationContext;
import com.njydsz.pmis.common.safe.desensitize.ColumnDesensitizationExecutor;
import com.njydsz.pmis.common.util.auth.AuthInfoUtils;
import com.njydsz.pmis.common.util.auth.RequestHolder;
import com.njydsz.pmis.common.json.YdszJson;
import com.njydsz.pmis.common.util.string.StringUtils;

/**
 * 列级数据权限过滤切面。
 *
 * <p>拦截标注了 {@link AuthColPermission} 注解的方法或类，
 * 在方法执行前解析并注入列权限信息，在方法执行后过滤返回值中无权限的字段。
 *
 * <p><b>核心功能：</b>
 * <ol>
 *   <li>解析当前用户的列权限规则</li>
 *   <li>将列权限信息注入到方法参数（支持 {@link ColumnScopeAware} 或 Map）</li>
 *   <li>将列权限规则以 header 形式透传给下游服务</li>
 *   <li>方法执行后，对返回值中的字段进行过滤（无权限字段置为 null）</li>
 *   <li>支持字段脱敏处理（如手机号、身份证号、邮箱等）</li>
 * </ol>
 *
 * <p><b>与 SQL 拦截器联动：</b>
 * <p>列权限信息会通过以下 header 透传到下游服务：
 * <ul>
 *   <li>X-Visible-Columns：表名到可见字段集合的映射</li>
 *   <li>X-Editable-Columns：表名到可编辑字段集合的映射</li>
 * </ul>
 *
 * <p><b>切面顺序：</b>
 * <p>本切面 Order 为 12，在行级权限注入之后执行。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see AuthColPermission
 * @see ColumnScopeInfo
 * @see ColumnScopeAware
 * @see ColumnPermissionResolver
 * @see ColumnDesensitizationService
 */
@Aspect
@Order(12)
public class AuthColPermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(AuthColPermissionAspect.class);
    private static final ColumnDesensitizationExecutor DESENSITIZER = ColumnDesensitizationExecutor.getInstance();

    /**
     * 缓存 Method -> ResolvedColumnPermission 的映射，避免重复反射解析注解
     * 使用 WeakHashMap 包装 Method 作为 key，防止类卸载导致的内存泄漏
     */
    private final ConcurrentHashMap<Method, ResolvedColumnPermission> annotationCache = new ConcurrentHashMap<>(256);

    private final ColumnPermissionResolver resolver;
    private final ColumnDesensitizationService desensitizationService;
    private final AuthColPermissionSigner signer;

    public AuthColPermissionAspect(ColumnPermissionResolver resolver,
                                   ColumnDesensitizationService desensitizationService,
                                   AuthProperties properties) {
        this.resolver = resolver;
        this.desensitizationService = desensitizationService;
        this.signer = new AuthColPermissionSigner(properties.getColPermissionSignKey());
    }

    @Pointcut("@annotation(com.njydsz.pmis.common.auth.annotation.AuthColPermission) || @within(com.njydsz.pmis.common.auth.annotation.AuthColPermission)")
    public void colPermissionPointcut() {
    }

    /**
     * 列级权限切面环绕通知。
     *
     * <p>拦截标注了 {@link AuthColPermission} 的方法，在方法执行前解析并注入列权限信息，
     * 方法执行后对返回值中无权限的字段进行过滤或脱敏处理。
     *
     * @param joinPoint 切面连接点
     * @return 方法返回值（已过滤无权限字段）
     * @throws Throwable 方法执行异常
     */
    @Around("colPermissionPointcut()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        ResolvedColumnPermission ann = findAnnotation(method, joinPoint.getTarget());
        if (ann == null) {
            return joinPoint.proceed();
        }

        Map<String, String> snapshot = RequestHolder.snapshotExtraHeaders();
        ColumnPermissionBundle bundle = buildColumnPermissionBundle(ann.table);
        boolean hasReturn = signature.getReturnType() != void.class;

        try {
            AuthContext.setColumnPermission(bundle.permissionInfo);
            injectIntoArgs(joinPoint, ann, bundle.scopeInfo);
            applyExtraHeadersIfAbsent(bundle.scopeInfo);

            Object returnValue = joinPoint.proceed();

            if (hasReturn && returnValue != null) {
                returnValue = filterReturnObject(returnValue, ann.mode, bundle.permissionInfo,
                        bundle.strict, bundle.desensitizationContext, ann.table);
            }

            return returnValue;
        } finally {
            AuthContext.clear();
            RequestHolder.restoreExtraHeaders(snapshot);
        }
    }

    private ResolvedColumnPermission findAnnotation(Method method, Object target) {
        ResolvedColumnPermission cached = annotationCache.get(method);
        if (cached != null) {
            return cached == ResolvedColumnPermission.NULL_MARKER ? null : cached;
        }

        AuthColPermission classAnnotation = AnnotationUtils.findAnnotation(target.getClass(), AuthColPermission.class);
        AuthColPermission methodAnnotation = AnnotationUtils.findAnnotation(method, AuthColPermission.class);

        if (classAnnotation == null && methodAnnotation == null) {
            annotationCache.put(method, ResolvedColumnPermission.NULL_MARKER);
            return null;
        }

        ResolvedColumnPermission resolved = new ResolvedColumnPermission();
        AuthColPermission source = methodAnnotation != null ? methodAnnotation : classAnnotation;

        if (source == null) {
            annotationCache.put(method, ResolvedColumnPermission.NULL_MARKER);
            return null;
        }

        resolved.mode = source.mode();
        resolved.table = resolveString(methodAnnotation == null ? null : methodAnnotation.table(),
                classAnnotation == null ? null : classAnnotation.table());
        resolved.mapKey = resolveString(methodAnnotation == null ? null : methodAnnotation.mapKey(),
                classAnnotation == null ? null : classAnnotation.mapKey(),
                "columnPermission");
        resolved.targetParamName = resolveString(methodAnnotation == null ? null : methodAnnotation.targetParamName(),
                classAnnotation == null ? null : classAnnotation.targetParamName());

        annotationCache.put(method, resolved);
        return resolved;
    }

    private Object filterReturnObject(Object returnValue, AuthColPermission.ColumnMode mode,
                                      ColumnPermissionInfo colInfo, boolean strict,
                                      ColumnDesensitizationContext desensitizeCtx, String table) {
        if (returnValue == null) {
            return null;
        }
        if (returnValue instanceof Collection) {
            List<Object> filtered = new ArrayList<>();
            for (Object item : (Collection<?>) returnValue) {
                filtered.add(filterObject(item, mode, colInfo, strict, desensitizeCtx, table));
            }
            return filtered;
        }
        if (returnValue.getClass().isArray()) {
            int length = Array.getLength(returnValue);
            List<Object> filtered = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                filtered.add(filterObject(Array.get(returnValue, i), mode, colInfo, strict, desensitizeCtx, table));
            }
            return filtered;
        }
        if (filterRecordsProperty(returnValue, mode, colInfo, strict, desensitizeCtx, table)) {
            return returnValue;
        }
        return filterObject(returnValue, mode, colInfo, strict, desensitizeCtx, table);
    }

    private Object filterObject(Object obj, AuthColPermission.ColumnMode mode,
                                ColumnPermissionInfo colInfo, boolean strict,
                                ColumnDesensitizationContext desensitizeCtx, String table) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            return filterMap((Map<?, ?>) obj, mode, colInfo, strict, desensitizeCtx, table);
        }
        return filterBean(obj, mode, colInfo, strict, desensitizeCtx, table);
    }

    private Map<?, ?> filterMap(Map<?, ?> map, AuthColPermission.ColumnMode mode,
                                ColumnPermissionInfo colInfo, boolean strict,
                                ColumnDesensitizationContext desensitizeCtx, String table) {
        if (map == null || map.isEmpty()) {
            return map;
        }
        Map<Object, Object> result = new LinkedHashMap<>(map);
        Set<Object> toRemove = new LinkedHashSet<>();
        for (Map.Entry<?, ?> e : result.entrySet()) {
            String columnName = String.valueOf(e.getKey());
            if (!isReadable(columnName, mode, colInfo, strict)) {
                toRemove.add(e.getKey());
            } else {
                Object value = e.getValue();
                if (value instanceof String && isDesensitizeEnabled(desensitizeCtx, table, columnName)) {
                    String desensitized = DESENSITIZER.desensitize((String) value,
                            desensitizeCtx.getRule(table, columnName));
                    result.put(e.getKey(), desensitized);
                }
            }
        }
        for (Object key : toRemove) {
            result.remove(key);
        }
        return result;
    }

    private Object filterBean(Object bean, AuthColPermission.ColumnMode mode,
                              ColumnPermissionInfo colInfo, boolean strict,
                              ColumnDesensitizationContext desensitizeCtx, String table) {
        // 深拷贝原始对象，避免反射修改影响调用方原始数据
        Object copy;
        try {
            String json = YdszJson.toJson(bean);
            Class<Object> clazz = (Class<Object>) bean.getClass();
            copy = YdszJson.toObject(json, clazz);
        } catch (Exception e) {
            log.warn("列权限过滤深拷贝失败，降级到原始对象: {}", e.getMessage());
            copy = bean;
        }

        for (Field field : listAllFields(copy.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            String columnName = field.getName();
            if (!isReadable(columnName, mode, colInfo, strict)) {
                try {
                    field.set(copy, null);
                } catch (IllegalAccessException e) {
                    log.warn("无法过滤字段 {}: {}", field.getName(), e.getMessage());
                }
            } else if (isDesensitizeEnabled(desensitizeCtx, table, columnName)) {
                Object value;
                try {
                    value = field.get(copy);
                    if (value instanceof String) {
                        String desensitized = DESENSITIZER.desensitize((String) value,
                                desensitizeCtx.getRule(table, columnName));
                        field.set(copy, desensitized);
                    }
                } catch (IllegalAccessException e) {
                    log.warn("无法读取字段 {}: {}", field.getName(), e.getMessage());
                }
            }
        }
        return copy;
    }

    private boolean isReadable(String column, AuthColPermission.ColumnMode mode,
                               ColumnPermissionInfo colInfo, boolean strict) {
        if (colInfo == null || colInfo.isEmpty()) {
            return true;
        }
        ColumnPermission p = colInfo.get(normalizeColumn(column));
        if (p == null) {
            return !strict;
        }
        switch (mode) {
            case READ:
                return p.isReadable();
            case WRITE:
                return p.isWritable();
            case READ_WRITE:
                return p.isReadable() && p.isWritable();
            default:
                return true;
        }
    }

    private boolean isDesensitizeEnabled(ColumnDesensitizationContext ctx, String table, String column) {
        if (ctx == null || ctx.isEmpty()) {
            return false;
        }
        return ctx.hasRule(table, column);
    }

    private void injectIntoArgs(ProceedingJoinPoint joinPoint, ResolvedColumnPermission annotation, ColumnScopeInfo scopeInfo) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Parameter[] parameters = method.getParameters();

        if (StringUtils.isNotBlank(annotation.targetParamName)) {
            for (int i = 0; i < parameters.length; i++) {
                if (!annotation.targetParamName.equals(parameters[i].getName())) {
                    continue;
                }
                inject(args[i], scopeInfo, annotation.mapKey);
                return;
            }
            return;
        }

        for (Object arg : args) {
            if (arg instanceof ColumnScopeAware) {
                ((ColumnScopeAware) arg).setColumnScope(scopeInfo);
                return;
            }
        }

        for (Object arg : args) {
            if (arg instanceof Map) {
                inject(arg, scopeInfo, annotation.mapKey);
                return;
            }
        }
    }

    private void inject(Object arg, ColumnScopeInfo scopeInfo, String mapKey) {
        if (arg == null) {
            return;
        }
        if (arg instanceof ColumnScopeAware) {
            ((ColumnScopeAware) arg).setColumnScope(scopeInfo);
            return;
        }
        if (arg instanceof Map) {
            safeMapPut(arg, mapKey, scopeInfo);
        }
    }

    private ColumnPermissionBundle buildColumnPermissionBundle(String table) {
        verifyColumnPermissionSignIfPresent();

        Map<String, Set<String>> visibleColumnsByTable = resolveRuleMap(HeaderConstants.X_VISIBLE_COLUMNS,
                AuthInfoUtils.getVisibleColumnsByTable());
        Map<String, Set<String>> editableColumnsByTable = resolveRuleMap(HeaderConstants.X_EDITABLE_COLUMNS,
                AuthInfoUtils.getEditableColumnsByTable());

        if (visibleColumnsByTable.isEmpty() && editableColumnsByTable.isEmpty() && resolver != null) {
            ColumnScopeInfo resolvedScope = resolver.resolve();
            visibleColumnsByTable = normalizeRuleMap(resolvedScope.getVisibleColumnsByTable());
            editableColumnsByTable = normalizeRuleMap(resolvedScope.getEditableColumnsByTable());
        }

        Map<String, Set<String>> scopedVisible = selectTableRules(visibleColumnsByTable, table);
        Map<String, Set<String>> scopedEditable = selectTableRules(editableColumnsByTable, table);

        ColumnPermissionInfo info = new ColumnPermissionInfo();
        addPermissions(info, scopedVisible, true, false);
        addPermissions(info, scopedEditable, false, true);

        boolean strict = !scopedVisible.isEmpty() || !scopedEditable.isEmpty();

        ColumnDesensitizationContext desensitizeCtx = buildDesensitizationContext(table);

        return new ColumnPermissionBundle(info, new ColumnScopeInfo(scopedVisible, scopedEditable),
                strict, desensitizeCtx);
    }

    private ColumnDesensitizationContext buildDesensitizationContext(String table) {
        if (desensitizationService == null) {
            return ColumnDesensitizationContext.empty();
        }
        try {
            String accessToken = RequestHolder.getAuthInfo() != null
                    ? RequestHolder.getAuthInfo().getAccessToken()
                    : null;
            if (accessToken == null) {
                return ColumnDesensitizationContext.empty();
            }
            String userId = AuthInfoUtils.getUniqueId();
            return desensitizationService.loadByToken(userId, accessToken);
        } catch (Exception e) {
            log.debug("构建脱敏上下文失败：{}", e.getMessage());
            return ColumnDesensitizationContext.empty();
        }
    }

    private Map<String, Set<String>> resolveRuleMap(String headerName, Map<String, Set<String>> authInfoRules) {
        String raw = RequestHolder.getExtraHeader(headerName);
        if (StringUtils.isBlank(raw)) {
            return normalizeRuleMap(authInfoRules);
        }
        return parseTableColumnsRule(raw);
    }

    private Map<String, Set<String>> selectTableRules(Map<String, Set<String>> rules, String table) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyMap();
        }
        if (StringUtils.isBlank(table)) {
            return rules;
        }
        Set<String> columns = rules.get(normalizeColumn(table));
        if (columns == null || columns.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Set<String>> result = new LinkedHashMap<>();
        result.put(normalizeColumn(table), columns);
        return result;
    }

    private void addPermissions(ColumnPermissionInfo info, Map<String, Set<String>> rules, boolean readable, boolean writable) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (Set<String> columns : rules.values()) {
            if (columns == null || columns.isEmpty()) {
                continue;
            }
            for (String column : columns) {
                if (StringUtils.isBlank(column)) {
                    continue;
                }
                info.add(normalizeColumn(column), new ColumnPermission(normalizeColumn(column), readable, writable));
            }
        }
    }

    private void applyExtraHeadersIfAbsent(ColumnScopeInfo scopeInfo) {
        forceSet(HeaderConstants.X_VISIBLE_COLUMNS, serializeRuleMap(scopeInfo.getVisibleColumnsByTable()));
        forceSet(HeaderConstants.X_EDITABLE_COLUMNS, serializeRuleMap(scopeInfo.getEditableColumnsByTable()));
    }

    private void verifyColumnPermissionSignIfPresent() {
        if (!signer.isEnabled()) {
            return;
        }

        String visibleColumns = RequestHolder.getExtraHeader(HeaderConstants.X_VISIBLE_COLUMNS);
        String editableColumns = RequestHolder.getExtraHeader(HeaderConstants.X_EDITABLE_COLUMNS);
        String receivedSign = RequestHolder.getExtraHeader(HeaderConstants.X_COL_PERMISSION_SIGN);

        if (StringUtils.isBlank(visibleColumns) && StringUtils.isBlank(editableColumns)) {
            return;
        }

        signer.verifySign(visibleColumns, editableColumns, receivedSign);
    }

    private void forceSet(String headerName, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        RequestHolder.putExtraHeader(headerName, value);
    }

    private String serializeRuleMap(Map<String, Set<String>> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        return rules.entrySet().stream()
                .filter(entry -> StringUtils.isNotBlank(entry.getKey())
                        && entry.getValue() != null
                        && !entry.getValue().isEmpty())
                .map(entry -> entry.getKey() + ":" + entry.getValue().stream()
                        .filter(StringUtils::isNotBlank)
                        .map(this::normalizeColumn)
                        .collect(Collectors.joining(",")))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(";"));
    }

    private Map<String, Set<String>> parseTableColumnsRule(String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptyMap();
        }
        Map<String, Set<String>> out = new LinkedHashMap<>();
        String[] blocks = value.split(";");
        for (String block : blocks) {
            if (StringUtils.isBlank(block) || !block.contains(":")) {
                continue;
            }
            String[] pair = block.split(":", 2);
            String table = normalizeColumn(pair[0]);
            Set<String> cols = Arrays.stream(pair[1].split(","))
                    .map(this::normalizeColumn)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (StringUtils.isNotBlank(table) && !cols.isEmpty()) {
                out.put(table, cols);
            }
        }
        return out;
    }

    private Map<String, Set<String>> normalizeRuleMap(Map<String, Set<String>> rules) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Set<String>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : rules.entrySet()) {
            String table = normalizeColumn(entry.getKey());
            if (StringUtils.isBlank(table) || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            Set<String> columns = entry.getValue().stream()
                    .map(this::normalizeColumn)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!columns.isEmpty()) {
                normalized.put(table, columns);
            }
        }
        return normalized;
    }

    private String normalizeColumn(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean filterRecordsProperty(Object bean, AuthColPermission.ColumnMode mode,
                                          ColumnPermissionInfo colInfo, boolean strict,
                                          ColumnDesensitizationContext desensitizeCtx, String table) {
        Method getter = ReflectionUtils.findMethod(bean.getClass(), "getRecords");
        Method setter = ReflectionUtils.findMethod(bean.getClass(), "setRecords", List.class);
        if (getter == null || setter == null) {
            return false;
        }
        try {
            Object records = getter.invoke(bean);
            if (!(records instanceof Collection)) {
                return false;
            }
            List<Object> filtered = new ArrayList<>();
            for (Object item : (Collection<?>) records) {
                filtered.add(filterObject(item, mode, colInfo, strict, desensitizeCtx, table));
            }
            setter.invoke(bean, filtered);
            return true;
        } catch (Exception e) {
            log.warn("列权限过滤 records 失败: {}", e.getMessage());
            return false;
        }
    }

    private static final ConcurrentHashMap<Class<?>, List<Field>> fieldListCache = new ConcurrentHashMap<>();

    private List<Field> listAllFields(Class<?> clazz) {
        return fieldListCache.computeIfAbsent(clazz, k -> {
            List<Field> fields = new ArrayList<>();
            Class<?> current = k;
            while (current != null && current != Object.class) {
                fields.addAll(Arrays.asList(current.getDeclaredFields()));
                current = current.getSuperclass();
            }
            return Collections.unmodifiableList(fields);
        });
    }

    private String resolveString(String primary, String secondary) {
        return resolveString(primary, secondary, "");
    }

    private String resolveString(String primary, String secondary, String defaultValue) {
        if (StringUtils.isNotBlank(primary)) {
            return primary.trim();
        }
        if (StringUtils.isNotBlank(secondary)) {
            return secondary.trim();
        }
        return defaultValue;
    }

    private static class ResolvedColumnPermission {
        private static final ResolvedColumnPermission NULL_MARKER = new ResolvedColumnPermission();

        private AuthColPermission.ColumnMode mode;
        private String table;
        private String mapKey;
        private String targetParamName;
    }

    private static class ColumnPermissionBundle {
        private final ColumnPermissionInfo permissionInfo;
        private final ColumnScopeInfo scopeInfo;
        private final boolean strict;
        private final ColumnDesensitizationContext desensitizationContext;

        private ColumnPermissionBundle(ColumnPermissionInfo permissionInfo, ColumnScopeInfo scopeInfo,
                                       boolean strict, ColumnDesensitizationContext desensitizationContext) {
            this.permissionInfo = permissionInfo;
            this.scopeInfo = scopeInfo;
            this.strict = strict;
            this.desensitizationContext = desensitizationContext;
        }
    }

    private static void safeMapPut(Object mapObj, String key, Object value) {
        if (mapObj instanceof Map<?, ?> map) {
            Map<String, Object> typedMap = (Map<String, Object>) map;
            typedMap.put(key, value);
        }
    }
}
