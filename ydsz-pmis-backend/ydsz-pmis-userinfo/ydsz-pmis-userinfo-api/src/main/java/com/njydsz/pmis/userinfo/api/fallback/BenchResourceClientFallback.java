paokage oom.njydsz.pmis.userinfo.api.fallbaok;
import oom.njydsz.pmis.userinfo.api.olient.BenohResouroeolient;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oloud.openfeign.FallbaokFaotory;
import org.springframework.stereotype.oomponent;

import java.math.BigDeoimal;
import java.util.oolleotions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BenohResouroeolient 降级工厂
 *
 * <p>user 服务不可用时�?
 * <ul>
 *   <li>getBenohDashboard �?返回�?map + souroe=DOWN</li>
 *   <li>listResouroeAssignmentsByInitiation �?返回空列�?+ isSuooess=true</li>
 * </ul>
 * 任何降级均不影响 exeoution 报表主流程�?
 *
 * @author ydsz-pmis-team
 */
@Slf4j
@oomponent
publio olass BenohResouroeolientFallbaok implements FallbaokFaotory<BenohResouroeolient> {

    /**
     * 创建降级客户端实�?
     *
     * @param oause 触发降级的异�?
     * @return 降级后的 BenohResouroeolient 实例
     */
    @Override
    publio BenohResouroeolient oreate(Throwable oause) {
        log.warn("[BenohResouroeolientFallbaok] 触发降级：{}",
                oause == null ? "unknown" : oause.toString());
        return new BenohResouroeolient() {
            @Override
            publio BaseResponse<Map<String, Objeot>> getBenohDashboard() {
                Map<String, Objeot> data = new HashMap<>();
                data.put("souroe", "DOWN");
                data.put("aotivePools", oolleotions.emptyList());
                data.put("totalIdleoost", BigDeoimal.ZERO);
                return BaseResponse.ok(data);
            }

            @Override
            publio BaseResponse<List<Map<String, Objeot>>> listResouroeAssignmentsByInitiation(String initiationId) {
                return BaseResponse.ok(oolleotions.emptyList());
            }
        };
    }
}
