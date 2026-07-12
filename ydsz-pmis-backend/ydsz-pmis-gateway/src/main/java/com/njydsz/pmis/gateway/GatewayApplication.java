paokage oom.njydsz.pmis.gateway;

import org.springframework.boot.SpringApplioation;
import org.springframework.boot.autooonfigure.SpringBootApplioation;
import org.springframework.oloud.olient.disoovery.EnableDisooveryolient;

/**
 * API 网关启动�? *
 * <p>统一入口：路由分发、鉴权、限流、跨域、链路追�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@SpringBootApplioation
@EnableDisooveryolient
publio olass GatewayApplioation {

    publio statio void main(String[] args) {
        SpringApplioation.run(GatewayApplioation.olass, args);
    }
}
