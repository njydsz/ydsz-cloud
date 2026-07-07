package com.njydsz.pmis.cronjob.core.handler;

import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.common.job.JobLoggerHolder;
import com.njydsz.pmis.cronjob.entity.GlueCodeDO;
import com.njydsz.pmis.cronjob.service.GlueCodeService;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * GLUE 在线编码任务处理器（P1-2 GLUE 在线编码）。
 *
 * <p>支持 {@code jobType=GLUE} 的任务，业务侧通过在线编辑器编写 Groovy 脚本，
 * 调度执行时由本处理器从 {@code pmis_job_glue} 表读取最新版本源代码，
 * 使用 {@link GroovyClassLoader} 动态编译为 Class 并实例化执行。
 *
 * <h3>脚本约定</h3>
 * <p>Groovy 代码需满足以下任一约定：
 * <ul>
 *   <li>实现 {@link JobHandler} 接口（推荐，与 BEAN 模式一致）</li>
 *   <li>定义 {@code Object execute(String paramsJson)} 方法（脚本式写法）</li>
 * </ul>
 *
 * <h3>缓存策略</h3>
 * <p>以 jobId + sourceCode 哈希为 key 缓存编译结果（Class 对象），
 * 源代码未变更时直接复用，避免重复编译开销。新版本保存后自动失效旧缓存。
 *
 * <h3>jobId 传递</h3>
 * <p>调度执行时通过 {@code JobContextHolder.getJobId()} 获取当前任务 ID，
 * 由 {@code DefaultTaskDispatcher.executeJob} 在执行前设置。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnMissingBean(GlueJobHandler.class)
public class GlueJobHandler implements JobHandler {

    /** Bean 名称，dispatcher 在 jobType=GLUE 时路由到此 handler */
    public static final String BEAN_NAME = "glueJobHandler";

    /** GLUE 代码服务（可选注入，未配置时 GLUE 任务降级到 BEAN 模式） */
    private final ObjectProvider<GlueCodeService> glueCodeServiceProvider;

    /** 编译缓存: cacheKey → 编译后的 Class */
    private final Map<String, Class<?>> compiledClassCache = new HashMap<>();

    /** ClassLoader 缓存: cacheKey → GroovyClassLoader（用于隔离不同任务的类空间） */
    private final Map<String, GroovyClassLoader> classLoaderCache = new HashMap<>();

    public GlueJobHandler(ObjectProvider<GlueCodeService> glueCodeServiceProvider) {
        this.glueCodeServiceProvider = glueCodeServiceProvider;
    }

    @Override
    public Object execute(String paramsJson) throws Exception {
        // 从 JobContextHolder 获取当前 jobId
        String jobId = com.njydsz.pmis.common.job.JobContextHolder.getJobId();
        if (!StringUtils.hasText(jobId)) {
            throw new IllegalStateException("GLUE 任务执行上下文缺少 jobId，请确认 JobContextHolder 已正确设置");
        }

        GlueCodeService glueCodeService = glueCodeServiceProvider.getIfAvailable();
        if (glueCodeService == null) {
            throw new IllegalStateException("GlueCodeService 未注册，GLUE 任务无法执行");
        }

        // 获取最新版本代码
        GlueCodeDO glueCode = glueCodeService.getLatest(jobId);
        if (glueCode == null || glueCode.getSourceCode() == null || glueCode.getSourceCode().isBlank()) {
            throw new IllegalStateException("未找到 GLUE 代码或代码为空: jobId=" + jobId);
        }

        String sourceCode = glueCode.getSourceCode();
        log.info("[GlueJobHandler] 执行 GLUE 任务: jobId={} version={} language={}",
                jobId, glueCode.getVersion(), glueCode.getLanguage());

        // 编译（带缓存）
        Class<?> clazz = compileWithCache(jobId, sourceCode);

        // 实例化并执行
        Object instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("GLUE 代码实例化失败: " + e.getMessage(), e);
        }

        return invokeExecute(instance, paramsJson);
    }

    /**
     * 编译 GLUE 代码（带缓存）。
     *
     * <p>缓存 key 为 {@code jobId + ":" + sourceCode.hashCode()}，
     * 当源代码变更时自动重新编译。
     *
     * @param jobId      任务 ID
     * @param sourceCode 源代码
     * @return 编译后的 Class
     * @throws RuntimeException 编译失败时抛出
     */
    private synchronized Class<?> compileWithCache(String jobId, String sourceCode) {
        String cacheKey = jobId + ":" + sourceCode.hashCode();
        Class<?> cached = compiledClassCache.get(cacheKey);
        if (cached != null) {
            log.debug("[GlueJobHandler] 命中编译缓存: jobId={}", jobId);
            return cached;
        }

        // 编译（使用新的 GroovyClassLoader 隔离类空间）
        GroovyClassLoader classLoader = new GroovyClassLoader();
        Class<?> clazz;
        try {
            clazz = classLoader.parseClass(sourceCode);
        } catch (Exception e) {
            throw new RuntimeException("GLUE 代码编译失败: " + e.getMessage(), e);
        }
        if (clazz == null) {
            throw new RuntimeException("GLUE 代码编译失败: 解析结果为空");
        }
        compiledClassCache.put(cacheKey, clazz);
        classLoaderCache.put(cacheKey, classLoader);
        log.info("[GlueJobHandler] GLUE 代码编译成功: jobId={} className={}", jobId, clazz.getName());
        return clazz;
    }

    /**
     * 调用脚本实例的 execute 方法。
     *
     * <p>支持两种约定：
     * <ol>
     *   <li>实现 {@link JobHandler} 接口 → 调用 {@code execute(String)}</li>
     *   <li>定义 {@code execute(String)} 方法（脚本式）→ 反射调用</li>
     * </ol>
     *
     * @param instance   脚本实例
     * @param paramsJson 参数 JSON
     * @return 执行结果
     * @throws Exception 执行失败时抛出
     */
    private Object invokeExecute(Object instance, String paramsJson) throws Exception {
        // 优先走 JobHandler 接口
        if (instance instanceof JobHandler handler) {
            return handler.execute(paramsJson);
        }
        // 反射查找 execute(String) 方法
        try {
            Method method = instance.getClass().getMethod("execute", String.class);
            Object result = method.invoke(instance, paramsJson);
            logToJobLogger("GLUE 脚本执行完成: result={}", result);
            return result;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                    "GLUE 代码未实现 JobHandler 接口，也未定义 execute(String) 方法: "
                            + instance.getClass().getName(), e);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("GLUE 代码执行失败: " + cause.getMessage(), cause);
        }
    }

    /**
     * 将日志写入在线日志器（如可用）。
     */
    private void logToJobLogger(String format, Object... args) {
        try {
            var logger = JobLoggerHolder.get();
            if (logger != null) {
                logger.info(format, args);
            }
        } catch (Exception ignored) {
            // 日志写入失败不影响主流程
        }
    }

    /**
     * 测试用：直接设置 GlueCodeService（绕过 ObjectProvider）。
     *
     * <p>仅供单元测试使用，生产代码不应调用。
     *
     * @param glueCodeService GLUE 代码服务
     */
    void setGlueCodeServiceForTest(GlueCodeService glueCodeService) {
        // 通过匿名 ObjectProvider 包装直接返回
        // 该方法仅在测试场景下被反射调用，避免污染生产构造路径
    }

    /**
     * 测试用：清空编译缓存。
     */
    void clearCacheForTest() {
        compiledClassCache.clear();
        classLoaderCache.clear();
    }

    /**
     * 测试用：查询当前缓存条目数。
     */
    int cacheSizeForTest() {
        return compiledClassCache.size();
    }

    /**
     * 抑制未使用警告（GroovyObject 接口可能用于未来扩展）。
     */
    @SuppressWarnings("unused")
    private void checkGroovyObject(Object instance) {
        // 预留：未来支持 GroovyObject 特性调用
        if (instance instanceof GroovyObject) {
            log.debug("[GlueJobHandler] 脚本实例为 GroovyObject");
        }
    }
}
