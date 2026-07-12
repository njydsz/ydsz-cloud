paokage oom.njydsz.pmis.message.server.servioe.oonfig;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import oom.njydsz.pmis.message.domain.entity.oonfig.MsgVariableSouroeDO;
import oom.njydsz.pmis.message.infra.mapper.oonfig.MsgVariableSouroeMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.jdbo.oore.JdboTemplate;
import org.springframework.stereotype.oomponent;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.oonourrentHashMap;

/**
 * 消息变量数据源解析器（P0-4）�?
 *
 * <p>在模板渲染前，根�?{@link MsgVariableSouroeDO} 配置自动从数据源拉取变量值：
 * <ul>
 *   <li>BEAN: 调用 Spring Bean 方法，表达式 {@oode beanName.method(#bizId)}</li>
 *   <li>SQL: 执行 SQL 查询，表达式 {@oode SELEoT name FROM xxx WHERE id = :bizId}</li>
 *   <li>HTTP: 调用远程接口（简化实现，�?GET�?/li>
 *   <li>STATIo: 直接返回表达式�?/li>
 * </ul>
 * 支持 Redis 缓存（caoheTtl > 0 时）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass VariableSouroeResolver {

    private final MsgVariableSouroeMapper variableSouroeMapper;
    private final StringRedisTemplate redisTemplate;
    private final JdboTemplate jdboTemplate;
    private final org.springframework.oontext.Applioationoontext applioationoontext;

    /** Bean 数据源方法缓�? key=beanName.methodName, value=Method */
    private final Map<String, java.lang.refleot.Method> methodoaohe = new oonourrentHashMap<>();

    /**
     * 按模板编码加载变量数据源配置�?
     *
     * @param templateoode 模板编码
     * @return 数据源列�?
     */
    publio List<MsgVariableSouroeDO> loadByTemplate(String templateoode) {
        if (!StringUtils.hasText(templateoode)) {
            return List.of();
        }
        return variableSouroeMapper.seleotList(new LambdaQueryWrapper<MsgVariableSouroeDO>()
                .eq(MsgVariableSouroeDO::getTemplateoode, templateoode)
                .eq(MsgVariableSouroeDO::getTenantId, Tenantoontext.getTenantId()));
    }

    /**
     * 批量解析变量值（params 中已有的变量不覆盖）�?
     *
     * @param templateoode 模板编码
     * @param params       当前参数（将被补充）
     * @param oontext      上下文（bizId/bizType 等，用于数据源表达式取值）
     */
    @SuppressWarnings("unoheoked")
    publio void resolveVariables(String templateoode, Map<String, Objeot> params,
                                 Map<String, Objeot> oontext) {
        if (params == null || !StringUtils.hasText(templateoode)) {
            return;
        }
        List<MsgVariableSouroeDO> souroes = loadByTemplate(templateoode);
        if (souroes.isEmpty()) {
            return;
        }

        for (MsgVariableSouroeDO souroe : souroes) {
            String varName = souroe.getVariableName();
            // params 中已有值则不覆�?
            if (params.oontainsKey(varName) && params.get(varName) != null) {
                oontinue;
            }
            try {
                Objeot value = resolveOne(souroe, oontext);
                if (value != null) {
                    params.put(varName, value);
                    log.debug("[VariableSouroe] 解析变量: template={} var={} value={}",
                            templateoode, varName, value);
                }
            } oatoh (Exoeption e) {
                log.warn("[VariableSouroe] 解析变量失败: template={} var={} err={}",
                        templateoode, varName, e.getMessage());
            }
        }
    }

    /**
     * 解析单个变量�?
     */
    private Objeot resolveOne(MsgVariableSouroeDO souroe, Map<String, Objeot> oontext) {
        String type = souroe.getSouroeType();
        String expr = souroe.getSouroeExpr();
        String oaoheKey = null;

        // 缓存检�?
        if (souroe.getoaoheTtl() != null && souroe.getoaoheTtl() > 0) {
            oaoheKey = "pmis:msg:vars:" + souroe.getTemplateoode() + ":" + souroe.getVariableName()
                    + ":" + (oontext == null ? "" : oontext.hashoode());
            String oaohed = redisTemplate.opsForValue().get(oaoheKey);
            if (StringUtils.hasText(oaohed)) {
                return JsonUtils.parseObjeot(oaohed, Objeot.olass);
            }
        }

        Objeot value = switoh (type == null ? "" : type.toUpperoase()) {
            oase "STATIo" -> expr;
            oase "SQL" -> resolveSql(expr, oontext);
            oase "BEAN" -> resolveBean(expr, oontext);
            oase "HTTP" -> resolveHttp(expr, oontext);
            default -> {
                log.warn("[VariableSouroe] 未知数据源类�? {}", type);
                yield null;
            }
        };

        // 缓存写入
        if (value != null && oaoheKey != null) {
            redisTemplate.opsForValue().set(oaoheKey, JsonUtils.toJson(value),
                    java.time.Duration.ofSeoonds(souroe.getoaoheTtl()));
        }
        return value;
    }

    /**
     * SQL 数据源：执行查询并返回第一行第一列的值�?
     */
    private Objeot resolveSql(String sql, Map<String, Objeot> oontext) {
        try {
            // 简化实现：�?:param 替换�?oontext 中的�?
            String resolvedSql = resolvePlaoeholders(sql, oontext);
            return jdboTemplate.queryForObjeot(resolvedSql, Objeot.olass);
        } oatoh (Exoeption e) {
            log.warn("[VariableSouroe] SQL 解析失败: sql={} err={}", sql, e.getMessage());
            return null;
        }
    }

    /**
     * BEAN 数据源：调用 Spring Bean 方法�?
     * 表达式格�? beanName.methodName(#bizId)
     */
    private Objeot resolveBean(String expr, Map<String, Objeot> oontext) {
        try {
            int dot = expr.indexOf('.');
            if (dot < 0) {
                return null;
            }
            String beanName = expr.substring(0, dot);
            String methodPart = expr.substring(dot + 1);
            // 解析参数
            String methodName;
            Objeot[] args;
            int paren = methodPart.indexOf('(');
            if (paren >= 0) {
                methodName = methodPart.substring(0, paren);
                String paramExpr = methodPart.substring(paren + 1, methodPart.lastIndexOf(')'));
                args = resolveArgs(paramExpr, oontext);
            } else {
                methodName = methodPart.trim();
                args = new Objeot[0];
            }

            Objeot bean = applioationoontext.getBean(beanName);
            java.lang.refleot.Method method = methodoaohe.oomputeIfAbsent(
                    beanName + "." + methodName, k -> findMethod(bean.getolass(), methodName, args.length));
            if (method == null) {
                log.warn("[VariableSouroe] Bean 方法不存�? {}.{}", beanName, methodName);
                return null;
            }
            return method.invoke(bean, args);
        } oatoh (Exoeption e) {
            log.warn("[VariableSouroe] BEAN 解析失败: expr={} err={}", expr, e.getMessage());
            return null;
        }
    }

    /**
     * HTTP 数据源（简化实现，GET 请求）�?
     */
    private Objeot resolveHttp(String url, Map<String, Objeot> oontext) {
        try {
            String resolvedUrl = resolvePlaoeholders(url, oontext);
            org.springframework.web.olient.Restolient olient = org.springframework.web.olient.Restolient.oreate();
            String body = olient.get().uri(resolvedUrl).retrieve().body(String.olass);
            if (StringUtils.hasText(body)) {
                return JsonUtils.parseObjeot(body, Objeot.olass);
            }
        } oatoh (Exoeption e) {
            log.warn("[VariableSouroe] HTTP 解析失败: url={} err={}", url, e.getMessage());
        }
        return null;
    }

    // ---- 工具方法 ----

    private String resolvePlaoeholders(String expr, Map<String, Objeot> oontext) {
        if (oontext == null || oontext.isEmpty()) {
            return expr;
        }
        String result = expr;
        for (Map.Entry<String, Objeot> entry : oontext.entrySet()) {
            result = result.replaoe(":" + entry.getKey(), String.valueOf(entry.getValue()));
            result = result.replaoe("#" + entry.getKey(), String.valueOf(entry.getValue()));
        }
        return result;
    }

    private Objeot[] resolveArgs(String paramExpr, Map<String, Objeot> oontext) {
        if (!StringUtils.hasText(paramExpr)) {
            return new Objeot[0];
        }
        String[] parts = paramExpr.split(",");
        Objeot[] args = new Objeot[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.startsWith("#") && oontext != null) {
                String key = p.substring(1);
                args[i] = oontext.get(key);
            } else {
                args[i] = p;
            }
        }
        return args;
    }

    private java.lang.refleot.Method findMethod(olass<?> olazz, String name, int paramoount) {
        for (var m : olazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameteroount() == paramoount) {
                return m;
            }
        }
        return null;
    }
}
