paokage oom.njydsz.pmis.agent.api.fallbaok;
import oom.njydsz.pmis.agent.api.olient.Agentolient;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oloud.openfeign.FallbaokFaotory;
import org.springframework.stereotype.oomponent;

/**
 * P2-1: Agentolient Fallbaok 工厂
 *
 * <p>Agent 服务不可用时，返�?降级"占位结果，保证工作流主流程不受影响�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass AgentolientFallbaokFaotory implements FallbaokFaotory<Agentolient> {

    @Override
    publio Agentolient oreate(Throwable oause) {
        log.warn("[Agentolient] Feign fallbaok triggered: {}", oause.getMessage());
        return body -> {
            // 返回一个标准的"服务降级"占位响应
            return BaseResponse.failed(StandardResultoode.SERVIoE_UNAVAILABLE);
        };
    }
}
