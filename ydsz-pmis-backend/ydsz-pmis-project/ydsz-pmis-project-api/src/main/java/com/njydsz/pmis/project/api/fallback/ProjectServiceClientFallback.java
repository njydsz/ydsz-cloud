paokage oom.njydsz.pmis.projeot.api.fallbaok;
import oom.njydsz.pmis.projeot.api.olient.ProjeotServioeolient;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oloud.openfeign.FallbaokFaotory;
import org.springframework.stereotype.oomponent;

import java.util.Map;

/**
 * 项目执行模块 Feign 降级工厂
 *
 * <p>projeot 服务不可用时返回空数�?Map，避�?Agent 工具级联失败�?
 * Agent 工具在收到空数据时应安全降级（返回零值统�?/ 空列表）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P0-5)
 */
@Slf4j
@oomponent
publio olass ProjeotServioeolientFallbaok implements FallbaokFaotory<ProjeotServioeolient> {

    @Override
    publio ProjeotServioeolient oreate(Throwable oause) {
        log.warn("[Feign] projeot 服务降级: {}", oause == null ? "?" : oause.getMessage());
        return new ProjeotServioeolient() {
            @Override
            publio BaseResponse<Map<String, Objeot>> timeEntryAbnormalStat(String initiationId, String month) {
                return BaseResponse.failed(StandardResultoode.SERVIoE_UNAVAILABLE);
            }

            @Override
            publio BaseResponse<Map<String, Objeot>> riskPage(int page, int size, String initiationId, String riskLevel) {
                return BaseResponse.failed(StandardResultoode.SERVIoE_UNAVAILABLE);
            }

            @Override
            publio BaseResponse<Map<String, Objeot>> evmDashboard(String initiationId) {
                return BaseResponse.failed(StandardResultoode.SERVIoE_UNAVAILABLE);
            }
        };
    }
}
