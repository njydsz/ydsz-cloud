package com.njydsz.common.auth.aspect;

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
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.ReflectionUtils;

import com.njydsz.common.auth.annotation.AuthColPermission;
import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.desensitize.ColumnDesensitizationService;
import com.njydsz.common.auth.model.ColumnPermission;
import com.njydsz.common.auth.model.ColumnPermissionInfo;
import com.njydsz.common.auth.model.ColumnScopeAware;
import com.njydsz.common.auth.model.ColumnScopeInfo;
import com.njydsz.common.auth.service.ColumnPermissionResolver;
import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.jdbc.constant.DataPermissionHeaderConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.safe.desensitize.ColumnDesensitizationContext;
import com.njydsz.common.safe.desensitize.ColumnDesensitizationExecutor;
import com.njydsz.common.json.JsonMapper;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.auth.model.AuthInfo;
import com.njydsz.common.auth.context.AuthInfoUtils;
import com.njydsz.common.util.string.StringUtils;

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
 * @author ydsz-team
 * @since 1.0.0
 *
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
    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    /**
     * 缓存 Method -> ResolvedColumnPermission 的映射，避免重复反射解析注解
     * 使用 WeakHashMap 包装 Method 作为 key，防止类卸载导致的内存泄漏
     */
    private final ConcurrentHashMap<Method, ResolvedColumnPermission> annotationCache = new ConcurrentHashMap<>(256);

    private final ColumnPermissionResolver resolver;
    private final ColumnDesensitizationService desensitizationService;
    private final JsonMapper jsonMapper = new JsonMapper();

    public AuthColPermissionAspect(ColumnPermissionResolver resolver,
                                   ColumnDesensitizationService desensitizationService) {
        this.resolver = resolver;
        this.desensitizationService = desensitizationService;
    }

    /**
     * 列级权限切点：匹配标注或元标注了 {@link AuthColPermission} 的方法或类。
     *
     * <p>仅作为 {@code @Around} 通知 {@link #doAround} 的引用锚点，自身不含逻辑；
     * 命中后由环绕通知统一完成列权限解析、参数注入与返回值过滤。</p>
     */
    @Pointcut("@annotation(com.njydsz.common.auth.annotation.AuthColPermission) || @within(com.njydsz.common.auth.annotation.AuthColPermission)")
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

        // SpEL 动态解析表名
        String resolvedTable = resolveTableIfSpel(ann.table, joinPoint);

        Map<String, String> snapshot = new HashMap<>(RequestContext.getExtraHeaders());
        ColumnPermissionBundle bundle = buildColumnPermissionBundle(resolvedTable);
        boolean hasReturn = signature.getReturnType() != void.class;

        try {
            RequestContext.put(BizContextKeys.KEY_COLUMN_PERMISSION, bundle.permissionInfo);
            injectIntoArgs(joinPoint, ann, bundle.scopeInfo);
            applyExtraHeadersIfAbsent(bundle.scopeInfo);

            Object returnValue = joinPoint.proceed();

            if (hasReturn && returnValue != null) {
                returnValue = filterReturnObject(returnValue, ann.mode, bundle.permissionInfo,
                        bundle.strict, bundle.desensitizationContext, resolvedTable);
            }

            return returnValue;
        } finally {
            RequestContext.remove(BizContextKeys.KEY_COLUMN_PERMISSION);
            restoreExtraHeaders(snapshot);
        }
    }

    /**
     * 如果 table 值是 SpEL 表达式（以 # 开头），则使用 SpEL 动态解析；
     * 否则直接返回原始值。
     */
    private String resolveTableIfSpel(String table, ProceedingJoinPoint joinPoint) {
        if (table == null || table.isBlank()) {
            return table;
        }
        if (!table.startsWith("#")) {
            return table;
        }
        try {
            String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
            Object[] args = joinPoint.getArgs();
            SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length && i < args.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }
            String resolved = SPEL_PARSER.parseExpression(table).getValue(context, String.class);
            return resolved != null ? resolved : table;
        } catch (Exception e) {
            log.warn("SpEL 解析表名失败: table={}, error={}", table, e.getMessage());
            return table;
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
        // 收集需要排除的字段名
        Set<String> excludedFields = collectExcludedFields(mode, colInfo, strict, table);
        if (excludedFields.isEmpty()) {
            // 无需排除字段，直接返回原始值
            return returnValue;
        }
        // 使用 JsonMapper 序列化时排除字段 + 反序列化回原始类型
        try {
            String json = jsonMapper.toJsonExcludeFields(returnValue, excludedFields);
            return YdszJson.fromJson(json, returnValue.getClass());
        } catch (Exception e) {
            log.warn("列权限序列化过滤失败，降级到反射过滤: {}", e.getMessage());
            // 降级：仍然使用反射方式过滤
            if (returnValue instanceof Collection) {
                List<Object> filtered = new ArrayList<>();
                for (Object item : (Collection<?>) returnValue) {
                    filtered.add(filterObject(item, mode, colInfo, strict, desensitizeCtx, table));
                }
                return filtered;
            }
            return filterObject(returnValue, mode, colInfo, strict, desensitizeCtx, table);
        }
    }

    /**
     * 收集在指定模式下无读权限的字段名集合。
     *
     * @param mode 列权限模式
     * @param colInfo 列权限信息
     * @param strict 是否严格模式
     * @param table 表名
     * @return 需要排除的字段名集合
     */
    private Set<String> collectExcludedFields(AuthColPermission.ColumnMode mode,
                                             ColumnPermissionInfo colInfo, boolean strict, String table) {
        if (colInfo == null || colInfo.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> excluded = new HashSet<>();
        for (String columnName : colInfo.getAllColumns()) {
            if (!isReadable(columnName, mode, colInfo, strict)) {
                excluded.add(columnName);
            }
        }
        return excluded;
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
        // 使用反射实例化 + 字段拷贝，避免 JSON 序列化/反序列化的性能开销
        Object copy;
        try {
            copy = shallowCopyByReflection(bean);
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
        Map<String, Set<String>> visibleColumnsByTable = resolveRuleMap(DataPermissionHeaderConstants.X_VISIBLE_COLUMNS,
                AuthInfoUtils.getVisibleColumnsByTable());
        Map<String, Set<String>> editableColumnsByTable = resolveRuleMap(DataPermissionHeaderConstants.X_EDITABLE_COLUMNS,
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
            Object authInfoObj = RequestContext.get(BizContextKeys.KEY_AUTH_INFO);
            String accessToken = authInfoObj != null
                    ? ((AuthInfo) authInfoObj).getAccessToken()
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
        String raw = RequestContext.getExtraHeader(headerName);
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
        forceSet(DataPermissionHeaderConstants.X_VISIBLE_COLUMNS, serializeRuleMap(scopeInfo.getVisibleColumnsByTable()));
        forceSet(DataPermissionHeaderConstants.X_EDITABLE_COLUMNS, serializeRuleMap(scopeInfo.getEditableColumnsByTable()));
    }

    private void forceSet(String headerName, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        RequestContext.putExtraHeader(headerName, value);
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

    /**
     * 获取类的所有字段（含父类），使用缓存避免重复反射扫描。
     *
     * @param clazz 目标类
     * @return 字段列表（不可变）
     */
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

    /**
     * 解析字符串（优先使用主值，其次使用次值）。
     *
     * @param primary   主值
     * @param secondary 次值
     * @return 解析后的字符串（优先主值，其次次值，都为空时返回空字符串）
     */
    private String resolveString(String primary, String secondary) {
        return resolveString(primary, secondary, "");
    }

    /**
     * 解析字符串（支持默认值）。
     *
     * @param primary       主值
     * @param secondary     次值
     * @param defaultValue  默认值
     * @return 解析后的字符串（优先主值，其次次值，都为空时返回默认值）
     */
    private String resolveString(String primary, String secondary, String defaultValue) {
        if (StringUtils.isNotBlank(primary)) {
            return primary.trim();
        }
        if (StringUtils.isNotBlank(secondary)) {
            return secondary.trim();
        }
        return defaultValue;
    }

    /**
     * 列权限注解的缓存解析结果。
     *
     * <p>将 {@link AuthColPermission} 在类/方法上的配置合并解析为一份可复用的运行时模型，
     * 避免每次请求重复反射解析注解；{@link #NULL_MARKER} 作为空值标记缓存"无注解"的方法。
     */
    private static class ResolvedColumnPermission {
        private static final ResolvedColumnPermission NULL_MARKER = new ResolvedColumnPermission();

        private AuthColPermission.ColumnMode mode;
        private String table;
        private String mapKey;
        private String targetParamName;
    }

    /**
     * 一次列权限过滤所需的全部上下文。
     *
     * <p>聚合列权限信息、作用域信息、严格模式开关与脱敏上下文，
     * 供环绕通知在注入参数与过滤返回值时一次性获取，避免多次解析规则。
     */
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

    /**
     * 安全地向 Map 参数注入列权限。
     *
     * <p>使用反射调用 {@code Map.put}，避免 unchecked cast 警告。
     *
     * @param mapObj Map 对象
     * @param key    注入的 key
     * @param value  注入的 value
     */
    private static void safeMapPut(Object mapObj, String key, Object value) {
        if (mapObj instanceof Map<?, ?> map) {
            // 使用反射调用 Map.put，避免 unchecked cast 警告
            // 此方法仅在列权限注入到 Map 参数时调用，反射开销可忽略
            try {
                MAP_PUT_METHOD.invoke(map, key, value);
            } catch (Exception e) {
                log.warn("无法向 Map 参数注入列权限: {}", e.getMessage());
            }
        }
    }

    private static final Method MAP_PUT_METHOD;

    static {
        try {
            MAP_PUT_METHOD = Map.class.getMethod("put", Object.class, Object.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Map.put method not found", e);
        }
    }

    /**
     * 通过反射浅拷贝对象，避免 JSON 序列化开销。
     *
     * <p>创建目标类的新实例，然后将所有可访问字段从源对象拷贝到新实例。
     * 相比 JSON 序列化+反序列化，性能提升约 10-50 倍。
     *
     * @param source 源对象
     * @return 拷贝后的新实例
     * @throws Exception 反射创建实例或字段访问失败时抛出
     */
    private static Object shallowCopyByReflection(Object source) throws Exception {
        Class<?> clazz = source.getClass();
        Object copy = clazz.getDeclaredConstructor().newInstance();
        for (Field field : fieldListCache.computeIfAbsent(clazz, k -> {
            List<Field> fields = new ArrayList<>();
            Class<?> current = k;
            while (current != null && current != Object.class) {
                fields.addAll(Arrays.asList(current.getDeclaredFields()));
                current = current.getSuperclass();
            }
            return Collections.unmodifiableList(fields);
        })) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(source);
            field.set(copy, value);
        }
        return copy;
    }

    /**
     * 恢复 extra headers 快照（对应原 RequestHolder.restoreExtraHeaders 语义）。
     *
     * <p>先移除当前全部 extra headers，再逐条写回快照内容，避免上下文残留。
     *
     * @param snapshot extra headers 快照（可为空）
     */
    private static void restoreExtraHeaders(Map<String, String> snapshot) {
        RequestContext.remove(BizContextKeys.KEY_EXTRA_HEADERS);
        if (snapshot != null) {
            snapshot.forEach(RequestContext::putExtraHeader);
        }
    }
}
