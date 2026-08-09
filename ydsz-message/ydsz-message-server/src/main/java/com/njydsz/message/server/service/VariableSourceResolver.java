package com.njydsz.message.server.service.config;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationContext;
import com.njydsz.common.redis.service.RedisService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.security.TenantContext;
import com.njydsz.common.json.YdszJson;
import com.njydsz.message.domain.entity.config.MsgVariableSource;
import com.njydsz.message.infra.mapper.config.MsgVariableSourceMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 变量数据源解析器。
 * <p>模板渲染前自动从 BEAN/SQL/HTTP 数据源拉取变量值。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Component
@RequiredArgsConstructor
public class VariableSourceResolver {

    private final MsgVariableSourceMapper variableSourceMapper;
    private final RedisService redisService;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationContext applicationContext;

    /** Bean 数据源方法缓存: key=beanName.methodName, value=Method */
    private final Map<String, Method> methodCache = new ConcurrentHashMap<>();

    /**
     * 按模板编码加载变量数据源配置。
     *
     * @param templateCode 模板编码
     * @return 数据源列表
     */
    public List<MsgVariableSource> loadByTemplate(String templateCode) {
        if (!StringUtils.hasText(templateCode)) {
            return List.of();
        }
        return variableSourceMapper.selectList(new LambdaQueryWrapper<MsgVariableSource>()
                .eq(MsgVariableSource::getTemplateCode, templateCode)
                .eq(MsgVariableSource::getTenantId, TenantContext.getTenantId()));
    }

    /**
     * 批量解析变量值（params 中已有的变量不覆盖）。
     *
     * @param templateCode 模板编码
     * @param params       当前参数（将被补充）
     * @param context      上下文（bizId/bizType 等，用于数据源表达式取值）
     */
    public void resolveVariables(String templateCode, Map<String, Object> params,
                                 Map<String, Object> context) {
        if (params == null || !StringUtils.hasText(templateCode)) {
            return;
        }
        List<MsgVariableSource> sources = loadByTemplate(templateCode);
        if (sources.isEmpty()) {
            return;
        }

        for (MsgVariableSource source : sources) {
            String varName = source.getVariableName();
            // params 中已有值则不覆盖
            if (params.containsKey(varName) && params.get(varName) != null) {
                continue;
            }
            try {
                Object value = resolveOne(source, context);
                if (value != null) {
                    params.put(varName, value);
                    log.debug("[VariableSource] 解析变量: template={} var={} value={}",
                            templateCode, varName, value);
                }
            } catch (Exception e) {
                log.warn("[VariableSource] 解析变量失败: template={} var={} err={}",
                        templateCode, varName, e.getMessage());
            }
        }
    }

    /**
     * 解析单个变量。
     */
    private Object resolveOne(MsgVariableSource source, Map<String, Object> context) {
        String type = source.getSourceType();
        String expr = source.getSourceExpr();
        String cacheKey = null;

        // 缓存检查
        if (source.getCacheTtl() != null && source.getCacheTtl() > 0) {
            cacheKey = "ydsz:msg:vars:" + source.getTemplateCode() + ":" + source.getVariableName()
                    + ":" + (context == null ? "" : context.hashCode());
            String cached = redisService.get(cacheKey, String.class);
            if (StringUtils.hasText(cached)) {
                return YdszJson.fromJson(cached, Object.class);
            }
        }

        Object value = switch (type == null ? "" : type.toUpperCase()) {
            case "STATIC" -> expr;
            case "SQL" -> resolveSql(expr, context);
            case "BEAN" -> resolveBean(expr, context);
            case "HTTP" -> resolveHttp(expr, context);
            default -> {
                log.warn("[VariableSource] 未知数据源类型: {}", type);
                yield null;
            }
        };

        // 缓存写入
        if (value != null && cacheKey != null) {
            redisService.set(cacheKey, YdszJson.toJson(value),
                    Duration.ofSeconds(source.getCacheTtl()));
        }
        return value;
    }

    /**
     * SQL 数据源：执行查询并返回第一行第一列的值。
     */
    private Object resolveSql(String sql, Map<String, Object> context) {
        try {
            // 简化实现：将 :param 替换为 context 中的值
            String resolvedSql = resolvePlaceholders(sql, context);
            return jdbcTemplate.queryForObject(resolvedSql, Object.class);
        } catch (Exception e) {
            log.warn("[VariableSource] SQL 解析失败: sql={} err={}", sql, e.getMessage(), e);
            return null;
        }
    }

    /**
     * BEAN 数据源：调用 Spring Bean 方法。
     * 表达式格式: beanName.methodName(#bizId)
     */
    private Object resolveBean(String expr, Map<String, Object> context) {
        try {
            int dot = expr.indexOf('.');
            if (dot < 0) {
                return null;
            }
            String beanName = expr.substring(0, dot);
            String methodPart = expr.substring(dot + 1);
            // 解析参数
            String methodName;
            Object[] args;
            int paren = methodPart.indexOf('(');
            if (paren >= 0) {
                methodName = methodPart.substring(0, paren);
                String paramExpr = methodPart.substring(paren + 1, methodPart.lastIndexOf(')'));
                args = resolveArgs(paramExpr, context);
            } else {
                methodName = methodPart.trim();
                args = new Object[0];
            }

            Object bean = applicationContext.getBean(beanName);
            Method method = methodCache.computeIfAbsent(
                    beanName + "." + methodName, k -> findMethod(bean.getClass(), methodName, args.length));
            if (method == null) {
                log.warn("[VariableSource] Bean 方法不存在: {}.{}", beanName, methodName);
                return null;
            }
            return method.invoke(bean, args);
        } catch (Exception e) {
            log.warn("[VariableSource] BEAN 解析失败: expr={} err={}", expr, e.getMessage(), e);
            return null;
        }
    }

    /**
     * HTTP 数据源（简化实现，GET 请求）。
     */
    private Object resolveHttp(String url, Map<String, Object> context) {
        try {
            String resolvedUrl = resolvePlaceholders(url, context);
            RestClient client = RestClient.create();
            String body = client.get().uri(resolvedUrl).retrieve().body(String.class);
            if (StringUtils.hasText(body)) {
                return YdszJson.fromJson(body, Object.class);
            }
        } catch (Exception e) {
            log.warn("[VariableSource] HTTP 解析失败: url={} err={}", url, e.getMessage(), e);
        }
        return null;
    }

    // ---- 工具方法 ----

    private String resolvePlaceholders(String expr, Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return expr;
        }
        String result = expr;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            result = result.replace(":" + entry.getKey(), String.valueOf(entry.getValue()));
            result = result.replace("#" + entry.getKey(), String.valueOf(entry.getValue()));
        }
        return result;
    }

    private Object[] resolveArgs(String paramExpr, Map<String, Object> context) {
        if (!StringUtils.hasText(paramExpr)) {
            return new Object[0];
        }
        String[] parts = paramExpr.split(",");
        Object[] args = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.startsWith("#") && context != null) {
                String key = p.substring(1);
                args[i] = context.get(key);
            } else {
                args[i] = p;
            }
        }
        return args;
    }

    private Method findMethod(Class<?> clazz, String name, int paramCount) {
        for (var m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        return null;
    }
}
