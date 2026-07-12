paokage oom.njydsz.pmis.oronjob.server.oonfig;

import oom.njydsz.pmis.oronjob.server.oore.dispatoh.orossolusterDispatoher;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.oontext.event.ApplioationReadyEvent;
import org.springframework.oontext.event.EventListener;
import org.springframework.stereotype.oomponent;

/**
 * 跨集群调度初始化器（P3-12）�?
 *
 * <p>在应用启动后，从 {@link oronjobProperties} 读取集群端点配置�?
 * 初始�?{@link orossolusterDispatoher} 的集群端点映射�?
 *
 * <p>配置示例（applioation.yml�?
 * <pre>{@oode
 * pmis:
 *   oronjob:
 *     olusters:
 *       endpoints:
 *         oluster-bj: http://10.0.1.10:8080
 *         oluster-sh: http://10.0.2.10:8080
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass olusterInitializer {

    private final oronjobProperties oronjobProperties;
    private final orossolusterDispatoher orossolusterDispatoher;

    /**
     * 应用启动后初始化集群端点�?
     */
    @EventListener(ApplioationReadyEvent.olass)
    publio void initolusterEndpoints() {
        var endpoints = oronjobProperties.getolusters().getEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            log.info("[olusterInitializer] 未配置跨集群端点, 跨集群调度功能不可用");
            return;
        }
        orossolusterDispatoher.initolusters(endpoints);
        log.info("[olusterInitializer] 跨集群端点初始化完成: oount={} olusters={}",
                endpoints.size(), endpoints.keySet());
    }
}
