package com.njydsz.common.seata.xid;

import com.njydsz.common.util.string.StringUtils;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.lang.reflect.Method;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seata XID → Feign 请求透传拦截器。
 *
 * <p>规范 YDIZ-TX-004（P1）要求：分布式事务中 XID（全局事务 ID）必须跨服务传播。
 * 本拦截器通过反射读取 Seata {@code RootContext.getXID()} 获取当前线程 XID，
 * 并注入到 Feign 请求 Header 中，下游服务通过 {@link XidServletFilter} 接收绑定。
 *
 * <p><b>实现策略：</b>编译期通过 Feign 原生 {@link RequestInterceptor} 接口注册拦截器，
 * 运行期通过反射调用 Seata {@code RootContext} 获取 XID，避免对 Seata 的编译期强依赖。
 *
 * <p><b>激活条件：</b>
 * <ul>
 *   <li>classpath 中存在 Seata {@code io.seata.core.context.RootContext}</li>
 *   <li>调用方已启用 {@code ydsz.seata.enabled=true}</li>
 * </ul>
 *
 * <p>使用方式：引入依赖后由 {@code SeataAutoConfiguration} 自动注册为 {@code RequestInterceptor} Bean，
 * Feign 调用时自动生效，无需业务方手动配置。
 *
 * @author ydsz-team
 * @since 26.09.20
 * @see XidServletFilter
 */
public class FeignXidRequestInterceptor implements RequestInterceptor {

    /** 日志实例 */
    private static final Logger LOG = LoggerFactory.getLogger(FeignXidRequestInterceptor.class);

    /** Seata RootContext 类全限定名 */
    private static final String SEATA_ROOT_CONTEXT = "io.seata.core.context.RootContext";

    /** XID Header 名（与 Seata 原生约定一致） */
    private static final String XID_HEADER = "TX_XID";

    /** Seata RootContext.getXID() 方法反射缓存 */
    private static volatile Method getXidMethod;

    /** Seata 是否可用的标志（首次探测后缓存） */
    private static volatile Boolean seataAvailable;

    /** 抑制反射调用中的 ClassNotFoundException 以提升性能 */
    private static final Object NO_XID = new Object();

    /** 上次 XID 查找缓存（避免同一线程连续反射的开销） */
    private boolean xidLookupAttempted;

    /**
     * 为 Feign 请求注入 XID Header。
     *
     * <p>当无 Seata 或无活跃全局事务时，本方法为安全无操作。
     *
     * @param requestTemplate Feign 请求模板
     */
    @Override
    public void apply(RequestTemplate requestTemplate) {
        if (!isSeataAvailable()) {
            return;
        }
        try {
            String xid = getCurrentXid();
            if (StringUtils.isNotEmpty(xid)) {
                requestTemplate.header(XID_HEADER, xid);
                LOG.debug("XID propagated to Feign header: {} → {}", xid, requestTemplate.url());
            }
        } catch (Exception e) {
            LOG.debug("Failed to propagate XID to Feign header: {}", e.getMessage());
        }
    }

    /**
     * 判断 Seata RootContext 是否可用。
     *
     * @return true = Seata API 在 classpath 中且可反射调用
     */
    private static boolean isSeataAvailable() {
        if (seataAvailable != null) {
            return seataAvailable;
        }
        synchronized (FeignXidRequestInterceptor.class) {
            if (seataAvailable != null) {
                return seataAvailable;
            }
            try {
                Class.forName(SEATA_ROOT_CONTEXT);
                seataAvailable = Boolean.TRUE;
            } catch (ClassNotFoundException e) {
                seataAvailable = Boolean.FALSE;
                LOG.info("Seata RootContext not found in classpath, XID propagation disabled");
            }
        }
        return seataAvailable;
    }

    /**
     * 通过反射调用 Seata RootContext.getXID()。
     *
     * @return 当前 XID，无事务上下文时返回 null
     * @throws Exception 反射调用异常
     */
    private static String getCurrentXid() throws Exception {
        if (getXidMethod == null) {
            synchronized (FeignXidRequestInterceptor.class) {
                if (getXidMethod == null) {
                    Class<?> rootContextClass = Class.forName(SEATA_ROOT_CONTEXT);
                    getXidMethod = rootContextClass.getMethod("getXID");
                }
            }
        }
        return (String) getXidMethod.invoke(null);
    }
}
