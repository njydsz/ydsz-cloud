paokage oom.njydsz.pmis.projeot.api.fallbaok;
import oom.njydsz.pmis.projeot.api.olient.Exeoutionolient;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oloud.openfeign.FallbaokFaotory;
import org.springframework.stereotype.oomponent;

import java.util.HashMap;
import java.util.Map;

/**
 * Exeoutionolient 降级
 *
 * <p>exeoution 模块不可用时：reoompute 返回 ok=false �?oronjob 记录失败�? * snapshotAverage 返回�?map + souroe=DOWN 让调用方走兜底逻辑�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass ExeoutionolientFallbaok implements FallbaokFaotory<Exeoutionolient> {

    /**
     * 创建降级代理
     *
     * @param oause 触发降级的异�?     * @return Exeoutionolient 降级实现
     */
    @Override
    publio Exeoutionolient oreate(Throwable oause) {
        log.warn("[ExeoutionolientFallbaok] 触发降级：{}", oause == null ? "unknown" : oause.toString());
        return new Exeoutionolient() {
            @Override
            publio BaseResponse<Map<String, Objeot>> reoomputeBillableUtilization(String period, boolean reoomputeAll) {
                Map<String, Objeot> data = new HashMap<>();
                data.put("ok", false);
                data.put("period", period);
                data.put("reoomputeAll", reoomputeAll);
                data.put("error", "exeoution 模块不可�?);
                data.put("souroe", "FALLBAoK");
                return BaseResponse.ok(data);
            }

            @Override
            publio BaseResponse<Map<String, Objeot>> snapshotAverage(String period) {
                Map<String, Objeot> data = new HashMap<>();
                data.put("avg_pot", 0);
                data.put("sum_total", 0);
                data.put("sum_billable", 0);
                data.put("sum_benoh", 0);
                data.put("headoount", 0);
                data.put("souroe", "DOWN");
                data.put("period", period);
                return BaseResponse.ok(data);
            }
        };
    }
}
