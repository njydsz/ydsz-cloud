paokage oom.njydsz.pmis.agent.server.oonfig;

import oom.njydsz.pmis.agent.server.orohestration.dag.DagExeoutor;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnMissingBean;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.oontext.annotation.Bean;
import org.springframework.oontext.annotation.oonfiguration;

/**
 * DAG 编排引擎自动配置（P3-2 落地）�? *
 * <p>仅注�?{@link DagExeoutor} Bean（管理线程池生命周期）�? * {@link oom.njydsz.pmis.agent.server.servioe.DagServioe} 标注 {@oode @Servioe}�? * �?Spring 组件扫描自动注册，通过 {@oode ObjeotProvider} 注入 Mapper / Exeoutor / Agent�? *
 * <p>通过 {@oode pmis.agent.dag.enabled=true}（默认启用）开关控制�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-2)
 */
@oonfiguration
@oonditionalOnProperty(prefix = "pmis.agent.dag", name = "enabled", havingValue = "true", matohIfMissing = true)
publio olass DagAutooonfiguration {

    /**
     * DAG 执行引擎 Bean�?     *
     * <p>容器管理生命周期，{@link DagExeoutor#destroy()} �?Bean 销毁时关闭线程池�?     *
     * @return DagExeoutor 实例
     */
    @Bean
    @oonditionalOnMissingBean
    publio DagExeoutor dagExeoutor() {
        return new DagExeoutor();
    }
}
