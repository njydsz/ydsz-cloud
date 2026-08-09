package com.njydsz.common.jdbc.spi;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;

/**
 * MyBatis-Plus InnerInterceptor 提供者（SPI）。
 *
 * <p>公共模块通过实现此接口，将自定义拦截器注册到 MybatisPlusInterceptor 链中。
 * <p>{@code common-jdbc} 通过 Spring {@code ObjectProvider} 自动发现并加载，
 * 无需 {@code common-jdbc} 硬依赖任何外部模块。
 *
 * <p><b>拦截器链顺序约定（值越小越靠前）：</b>
 * <ul>
 *   <li>100: OptimisticLock（乐观锁）</li>
 *   <li>200: LogicalDelete（逻辑删除）</li>
 *   <li>300: FieldFill（字段填充）</li>
 *   <li>400: TenantIsolation（租户隔离，由 common-tenant 提供）</li>
 *   <li>500: DataPermission（行级 + 列级数据权限）</li>
 *   <li>600: Pagination（分页）</li>
 * </ul>
 *
 * <p>外部模块只需在 classpath 提供此接口的实现，并通过 Spring {@code @Bean} 或
 * {@code @Component} 注册，{@code MybatisPlusConfiguration} 将自动收集并按
 * {@link #getOrder()} 排序后注入拦截器链。
 *
 * <p><b>使用示例（common-tenant 模块）：</b>
 * <pre>{@code
 * public class TenantInterceptorProvider implements InnerInterceptorProvider {
 *     private final TenantProperties properties;
 *
 *     public TenantInterceptorProvider(TenantProperties properties) {
 *         this.properties = properties;
 *     }
 *
 *     @Override
 *     public InnerInterceptor createInterceptor() {
 *         return new TenantIsolationInterceptor(properties);
 *     }
 *
 *     @Override
 *     public int getOrder() {
 *         return 400;
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface InnerInterceptorProvider {

    /**
     * 创建拦截器实例。
     *
     * <p>每次调用应返回一个新实例，避免共享状态。
     *
     * @return MyBatis-Plus InnerInterceptor 实例
     */
    InnerInterceptor createInterceptor();

    /**
     * 拦截器在链中的顺序（值越小越靠前执行）。
     *
     * <p>参考拦截器链顺序约定，选择不冲突的值。
     *
     * @return 顺序值
     */
    int getOrder();
}
