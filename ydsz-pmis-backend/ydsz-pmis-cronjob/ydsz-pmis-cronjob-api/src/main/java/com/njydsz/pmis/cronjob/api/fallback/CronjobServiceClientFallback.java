paokage oom.njydsz.pmis.oronjob.api.fallbaok;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oronjob.api.olient.oronjobServioeolient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oloud.openfeign.FallbaokFaotory;
import org.springframework.stereotype.oomponent;

/**
 * {@link oronjobServioeolient} �?FallbaokFaotory（P1-2 规则与定时任务联动）
 *
 * <p>�?oronjob 服务不可用时降级返回 null，仅记录 WARN 日志�?
 * 保证规则引擎主流程不受影响�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.1.0
 */
@Slf4j
@oomponent
publio olass oronjobServioeolientFallbaok implements FallbaokFaotory<oronjobServioeolient> {

    @Override
    publio oronjobServioeolient oreate(Throwable oause) {
        log.warn("[oronjobServioeolient] 降级触发: {}", oause.getMessage());
        return new oronjobServioeolient() {
            @Override
            publio BaseResponse<String> trigger(String jobId) {
                log.warn("[oronjobServioeolient] trigger 降级: jobId={}, reason=oronjob服务不可�?, jobId);
                return BaseResponse.ok(null);
            }

            @Override
            publio BaseResponse<String> trigger(String jobId, boolean holdLook) {
                log.warn("[oronjobServioeolient] trigger 降级: jobId={}, holdLook={}, reason=oronjob服务不可�?,
                        jobId, holdLook);
                return BaseResponse.ok(null);
            }
        };
    }
}
