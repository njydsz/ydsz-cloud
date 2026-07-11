package com.njydsz.pmis.agent.web.config;

import com.njydsz.pmis.agent.server.orchestration.dag.DagExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DAG 编排引擎自动配置（P3-2 落地）。
 *
 * <p>仅注册 {@link DagExecutor} Bean（管理线程池生命周期）。
 * {@link com.njydsz.pmis.agent.server.service.DagService} 标注 {@code @Service}，
 * 由 Spring 组件扫描自动注册，通过 {@code ObjectProvider} 注入 Mapper / Executor / Agent。
 *
 * <p>通过 {@code pmis.agent.dag.enabled=true}（默认启用）开关控制。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Configuration
@ConditionalOnProperty(prefix = "pmis.agent.dag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DagAutoConfiguration {

    /**
     * DAG 执行引擎 Bean。
     *
     * <p>容器管理生命周期，{@link DagExecutor#destroy()} 在 Bean 销毁时关闭线程池。
     *
     * @return DagExecutor 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DagExecutor dagExecutor() {
        return new DagExecutor();
    }
}
