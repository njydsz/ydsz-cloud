package com.njydsz.common.seata.xid;

import com.njydsz.common.util.string.StringUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XID Servlet 过滤器 — 接收上游传播的 Seata XID 并绑定到当前线程。
 *
 * <p>规范 YDIZ-TX-004（P1）要求：下游服务必须接收上游传播的 XID 并绑定到 Seata {@code RootContext}，
 * 以延续全局事务上下文。
 *
 * <p>本 Filter 从 HTTP 请求 Header {@code TX_XID} 中读取 XID，通过反射调用
 * Seata {@code RootContext.bind(xid)} 进行绑定。请求处理完成后自动解绑（finally 块保障）。
 *
 * <p><b>实现策略：</b>运行期反射调用 Seata API，编译期仅依赖 Servlet API。
 * 当 classpath 无 Seata 时 Filter 降级为无操作。
 *
 * <p>使用方式：由 {@code SeataAutoConfiguration} 自动注册为 {@code FilterRegistrationBean}，
 * URL Pattern 为 {@code /*}，order 为 {@code HIGHEST_PRECEDENCE + 100}。
 *
 * @author ydsz-team
 * @since 26.09.20
 * @see FeignXidRequestInterceptor
 */
public class XidServletFilter implements Filter {

    /** 日志实例 */
    private static final Logger LOG = LoggerFactory.getLogger(XidServletFilter.class);

    /** Seata RootContext 类全限定名 */
    private static final String SEATA_ROOT_CONTEXT = "io.seata.core.context.RootContext";

    /** XID Header 名（与 Seata 原生约定一致） */
    private static final String XID_HEADER = "TX_XID";

    /** Seata RootContext.bind/unbind 方法反射缓存 */
    private static volatile Method bindMethod;

    private static volatile Method unbindMethod;

    /** Seata 是否可用的标志 */
    private static volatile Boolean seataAvailable;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !isSeataAvailable()) {
            // 非 HTTP 请求或 Seata 不可用时，直接放行
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String xid = httpRequest.getHeader(XID_HEADER);

        if (StringUtils.isEmpty(xid)) {
            // 无 XID Header，无需绑定，直接放行
            chain.doFilter(request, response);
            return;
        }

        try {
            bindXid(xid);
            LOG.debug("XID bound to RootContext: {}", xid);
        } catch (Exception e) {
            LOG.warn("Failed to bind XID from request header: {}", e.getMessage());
            // 绑定失败不影响请求处理
            chain.doFilter(request, response);
            return;
        }

        try {
            chain.doFilter(request, response);
        } finally {
            try {
                unbindXid();
                LOG.debug("XID unbound from RootContext");
            } catch (Exception e) {
                LOG.warn("Failed to unbind XID from RootContext: {}", e.getMessage());
            }
        }
    }

    /**
     * 判断 Seata RootContext 是否可用。
     */
    private static boolean isSeataAvailable() {
        if (seataAvailable != null) {
            return seataAvailable;
        }
        synchronized (XidServletFilter.class) {
            if (seataAvailable != null) {
                return seataAvailable;
            }
            try {
                Class.forName(SEATA_ROOT_CONTEXT);
                seataAvailable = Boolean.TRUE;
            } catch (ClassNotFoundException e) {
                seataAvailable = Boolean.FALSE;
                LOG.info("Seata RootContext not found in classpath, XID receiving disabled");
            }
        }
        return seataAvailable;
    }

    /**
     * 通过反射调用 RootContext.bind(xid)。
     *
     * @param xid 待绑定的 XID
     * @throws Exception 反射调用异常
     */
    private static void bindXid(String xid) throws Exception {
        if (bindMethod == null) {
            synchronized (XidServletFilter.class) {
                if (bindMethod == null) {
                    Class<?> rootContextClass = Class.forName(SEATA_ROOT_CONTEXT);
                    bindMethod = rootContextClass.getMethod("bind", String.class);
                }
            }
        }
        bindMethod.invoke(null, xid);
    }

    /**
     * 通过反射调用 RootContext.unbind()。
     *
     * @throws Exception 反射调用异常
     */
    private static void unbindXid() throws Exception {
        if (unbindMethod == null) {
            synchronized (XidServletFilter.class) {
                if (unbindMethod == null) {
                    Class<?> rootContextClass = Class.forName(SEATA_ROOT_CONTEXT);
                    unbindMethod = rootContextClass.getMethod("unbind");
                }
            }
        }
        unbindMethod.invoke(null);
    }
}
