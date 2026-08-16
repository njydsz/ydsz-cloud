package com.njydsz.common.auth.aspect;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
import org.springframework.http.HttpStatus;
import com.njydsz.common.auth.annotation.AuthRowPermission;
import com.njydsz.common.auth.context.AuthInfoUtils;
import com.njydsz.common.auth.model.DataScopeAware;
import com.njydsz.common.auth.model.DataScopeInfo;
import com.njydsz.common.auth.service.impl.RedisRoleDataPermissionResolver;
import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.jdbc.constant.DataPermissionHeaderConstants;
import com.njydsz.common.jdbc.permission.DataPermissionContext;
import com.njydsz.common.jdbc.permission.DataScopeContextHolder;
import com.njydsz.common.util.string.StringUtils;

/**
 * 行级数据权限注入切面。
 *
 * <p>拦截标注了 {@link AuthRowPermission} 注解的方法或类，
 * 在方法执行前解析并注入当前用户的数据权限范围。
 *
 * <p><b>核心功能：</b>
 * <ol>
 *   <li>解析当前用户的数据权限范围 {@link DataScopeInfo}</li>
 *   <li>将数据权限信息注入到方法参数（支持 {@link DataScopeAware} 或 Map）</li>
 *   <li>将数据范围信息以 header 形式透传给下游服务</li>
 *   <li>根据 {@link AuthRowPermission#required()} 决定无权限时的行为</li>
 * </ol>
 *
 * <p><b>与 SQL 拦截器联动：</b>
 * <p>数据权限信息会通过以下 header 透传到 SQL 拦截层：
 * <ul>
 *   <li>X-Data-Scope：数据权限范围类型</li>
 *   <li>X-Tenant-Id：租户 ID</li>
 *   <li>X-Unique-Id：用户 ID</li>
 *   <li>X-Company-Ids：公司 ID 集合</li>
 *   <li>X-Dept-Ids：部门 ID 集合</li>
 *   <li>X-Project-Ids：项目 ID 集合</li>
 *   <li>X-Region-Ids：区域 ID 集合</li>
 * </ul>
 *
 * <p><b>切面顺序：</b>
 * <p>本切面 Order 为 11，在菜单权限校验之后、接口权限校验之前执行。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see AuthRowPermission
 * @see DataScopeInfo
 * @see DataScopeAware
 * @see RedisRoleDataPermissionResolver
 */
@Aspect
@Order(11)
public class AuthRowPermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(AuthRowPermissionAspect.class);

    /**
     * 缓存 Method -> ResolvedRowPermission 的映射，避免重复反射解析注解
     */
    private final ConcurrentHashMap<Method, ResolvedRowPermission> annotationCache = new ConcurrentHashMap<>(256);

    private final RedisRoleDataPermissionResolver resolver;

    public AuthRowPermissionAspect(RedisRoleDataPermissionResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * 行级数据权限切点：匹配标注或元标注了 {@link AuthRowPermission} 的方法或类。
     *
     * <p>作为 {@link #doAround} 的引用锚点；命中后由该通知完成数据权限范围解析与参数注入。</p>
     */
    @Pointcut("@annotation(com.njydsz.common.auth.annotation.AuthRowPermission) || @within(com.njydsz.common.auth.annotation.AuthRowPermission)")
    public void rowPermissionPointCut() {
    }

    /**
     * 行级权限切面环绕通知。
     *
     * <p>拦截标注了 {@link AuthRowPermission} 的方法，在方法执行前解析并注入数据权限范围，
     * 并将数据范围信息以 header 形式透传给下游服务。
     *
     * @param joinPoint 切面连接点
     * @return 方法返回值
     * @throws Throwable 方法执行异常
     */
    @Around("rowPermissionPointCut()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        Map<String, String> snapshot = new HashMap<>(RequestContext.getExtraHeaders());
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        ResolvedRowPermission ann = findAnnotation(method, joinPoint);
        if (ann == null) {
            return joinPoint.proceed();
        }

        try {
            DataScopeInfo info = resolver.resolve();

            if (!isEffective(info)) {
                if (ann.required) {
                    throw BusinessException.builder().code(String.valueOf(HttpStatus.FORBIDDEN.value())).message("数据权限信息缺失").build();
                }
            }

            injectIntoArgs(joinPoint, info, ann);
            applyExtraHeadersIfAbsent(info);

            // P2: 将 DataScopeInfo 适配为 DataPermissionContext 注入到 Holder，
            // 使 SQL 拦截器可直接读取结构化的数据权限上下文，无需经过 HTTP Header 反序列化
            DataPermissionContext context = adaptToDataPermissionContext(info);
            DataScopeContextHolder.set(context);

            if (log.isDebugEnabled()) {
                log.debug("行权限注入: scope={}, companyIds={}, deptIds={}, contextInjected={}",
                        info == null ? null : info.getScope(),
                        info == null ? 0 : safeSize(info.getCompanyIds()),
                        info == null ? 0 : safeSize(info.getDeptIds()),
                        context != null);
            }

            return joinPoint.proceed();
        } finally {
            DataScopeContextHolder.remove();
            restoreExtraHeaders(snapshot);
        }
    }

    /**
     * 将 {@link DataScopeInfo} 适配为 {@link DataPermissionContext}。
     *
     * <p>字段映射关系：
     * <ul>
     *   <li>scope → dataScope（行级维度编码）</li>
     *   <li>userId → userId（当前用户 ID）</li>
     *   <li>companyIds → companyIds（公司维度集合，复制为新 HashSet 避免不可变集合问题）</li>
     *   <li>deptIds → deptIds（部门维度集合）</li>
     *   <li>projectIds → projectIds（项目维度集合）</li>
     *   <li>regionIds → regionIds（区域维度集合）</li>
     * </ul>
     *
     * <p>注意：customSqlCondition 不在此映射，因为 SQL 拦截器不直接使用自定义 SQL 条件片段。
     * 如需支持列级权限联动，可扩展本方法填充 visibleColumnsByTable / editableColumnsByTable。
     *
     * @param info 数据权限信息，可为 {@code null}
     * @return 数据权限上下文，info 为 {@code null} 时返回空上下文
     */
    private DataPermissionContext adaptToDataPermissionContext(DataScopeInfo info) {
        if (info == null) {
            return DataPermissionContext.empty();
        }
        DataPermissionContext context = new DataPermissionContext();
        context.setDataScope(info.getScope());
        context.setUserId(info.getUserId());
        context.setCompanyIds(info.getCompanyIds() != null ? new HashSet<>(info.getCompanyIds()) : new HashSet<>());
        context.setDeptIds(info.getDeptIds() != null ? new HashSet<>(info.getDeptIds()) : new HashSet<>());
        context.setProjectIds(info.getProjectIds() != null ? new HashSet<>(info.getProjectIds()) : new HashSet<>());
        context.setRegionIds(info.getRegionIds() != null ? new HashSet<>(info.getRegionIds()) : new HashSet<>());
        return context;
    }

    private ResolvedRowPermission findAnnotation(Method method, ProceedingJoinPoint joinPoint) {
        ResolvedRowPermission cached = annotationCache.get(method);
        if (cached != null) {
            return cached == ResolvedRowPermission.NULL_MARKER ? null : cached;
        }

        AuthRowPermission classAnnotation = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), AuthRowPermission.class);
        AuthRowPermission methodAnnotation = AnnotationUtils.findAnnotation(method, AuthRowPermission.class);

        if (classAnnotation == null && methodAnnotation == null) {
            annotationCache.put(method, ResolvedRowPermission.NULL_MARKER);
            return null;
        }

        ResolvedRowPermission resolved = new ResolvedRowPermission();
        resolved.required = (classAnnotation != null && classAnnotation.required())
                || (methodAnnotation != null && methodAnnotation.required());
        resolved.mapKey = resolveMapKey(classAnnotation, methodAnnotation);
        resolved.targetParamName = resolveTargetParamName(classAnnotation, methodAnnotation);

        annotationCache.put(method, resolved);
        return resolved;
    }

    private boolean isEffective(DataScopeInfo info) {
        if (info == null) {
            return false;
        }
        return (info.getScope() != null)
                || StringUtils.isNotBlank(info.getTenantId())
                || StringUtils.isNotBlank(info.getUserId())
                || (!safeIsEmpty(info.getCompanyIds()))
                || (!safeIsEmpty(info.getDeptIds()))
                || (!safeIsEmpty(info.getProjectIds()))
                || (!safeIsEmpty(info.getRegionIds()))
                || info.hasCustomSqlCondition();
    }

    private int safeSize(Set<String> set) {
        return set == null ? 0 : set.size();
    }

    private boolean safeIsEmpty(Set<String> set) {
        return set == null || set.isEmpty();
    }

    private void injectIntoArgs(ProceedingJoinPoint joinPoint, DataScopeInfo info, ResolvedRowPermission annotation) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Parameter[] params = method.getParameters();
        String targetParam = annotation.targetParamName;

        if (StringUtils.isNotBlank(targetParam)) {
            for (int i = 0; i < params.length; i++) {
                if (!targetParam.equals(params[i].getName())) {
                    continue;
                }
                Object arg = args[i];
                if (arg == null) {
                    continue;
                }
                inject(arg, info, annotation.mapKey);
                return;
            }
            log.warn("@AuthRowPermission(targetParamName=\"{}\") 未在方法中找到匹配参数，忽略注入", targetParam);
            return;
        }

        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof DataScopeAware) {
                ((DataScopeAware) arg).setDataScope(info);
                return;
            }
        }

        for (Object arg : args) {
            if (arg instanceof Map) {
                safeMapPut(arg, annotation.mapKey, info);
                return;
            }
        }
    }

    private void inject(Object arg, DataScopeInfo info, String mapKey) {
        if (arg instanceof DataScopeAware) {
            ((DataScopeAware) arg).setDataScope(info);
            return;
        }
        if (arg instanceof Map) {
            safeMapPut(arg, mapKey, info);
        }
    }

    private void applyExtraHeadersIfAbsent(DataScopeInfo info) {
        if (info == null) {
            return;
        }
        String scopeCode = info.getScope();
        forceSet(DataPermissionHeaderConstants.X_DATA_SCOPE, scopeCode);
        forceSet(DataPermissionHeaderConstants.X_TENANT_ID, resolveTenantId(info));
        forceSet(DataPermissionHeaderConstants.X_UNIQUE_ID, resolveUserId(info));
        forceSet(DataPermissionHeaderConstants.X_COMPANY_IDS, joinIds(info.getCompanyIds()));
        forceSet(DataPermissionHeaderConstants.X_DEPT_IDS, joinIds(info.getDeptIds()));
        forceSet(DataPermissionHeaderConstants.X_PROJECT_IDS, joinIds(info.getProjectIds()));
        forceSet(DataPermissionHeaderConstants.X_REGION_IDS, joinIds(info.getRegionIds()));
        forceSet(DataPermissionHeaderConstants.X_CUSTOM_SQL_CONDITION, info.resolveCustomSqlCondition());
    }

    private String resolveMapKey(AuthRowPermission classAnnotation, AuthRowPermission methodAnnotation) {
        if (methodAnnotation != null && StringUtils.isNotBlank(methodAnnotation.mapKey())) {
            return methodAnnotation.mapKey();
        }
        if (classAnnotation != null && StringUtils.isNotBlank(classAnnotation.mapKey())) {
            return classAnnotation.mapKey();
        }
        return "rowPermission";
    }

    private String resolveTargetParamName(AuthRowPermission classAnnotation, AuthRowPermission methodAnnotation) {
        if (methodAnnotation != null && StringUtils.isNotBlank(methodAnnotation.targetParamName())) {
            return methodAnnotation.targetParamName().trim();
        }
        if (classAnnotation != null && StringUtils.isNotBlank(classAnnotation.targetParamName())) {
            return classAnnotation.targetParamName().trim();
        }
        return "";
    }

    private String resolveTenantId(DataScopeInfo info) {
        if (info != null && StringUtils.isNotBlank(info.getTenantId())) {
            return info.getTenantId();
        }
        return AuthInfoUtils.getTenantId();
    }

    private String resolveUserId(DataScopeInfo info) {
        if (info != null && StringUtils.isNotBlank(info.getUserId())) {
            return info.getUserId();
        }
        return AuthInfoUtils.getUniqueId();
    }

    private void forceSet(String headerName, String value) {
        if (value == null) {
            return;
        }
        RequestContext.putExtraHeader(headerName, value);
    }

    private String joinIds(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream()
                .filter(it -> it != null && !it.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.joining(","));
    }

    /**
     * 行级数据权限注解的缓存解析结果。
     *
     * <p>将 {@link AuthRowPermission} 在类/方法上的配置合并解析为一份可复用的运行时模型，
     * 避免每次请求重复反射解析注解；{@link #NULL_MARKER} 作为空值标记缓存"无注解"的方法。
     */
    private static class ResolvedRowPermission {
        private static final ResolvedRowPermission NULL_MARKER = new ResolvedRowPermission();

        private boolean required;
        private String mapKey;
        private String targetParamName;
    }

    private static void safeMapPut(Object mapObj, String key, Object value) {
        if (mapObj instanceof Map<?, ?> map) {
            Map<String, Object> typedMap = (Map<String, Object>) map;
            typedMap.put(key, value);
        }
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
