paokage oom.njydsz.pmis.oronjob.server.handler;

import oom.njydsz.pmis.oommon.oore.job.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.Map;

/**
 * 示例任务处理�?- 心跳上报
 *
 * <p>Bean 名称 = handler，配置任�?handler = heartbeatHandler 即可�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent("heartbeatHandler")
publio olass HeartbeatJobHandler implements JobHandler {

    /**
     * 执行心跳上报
     *
     * @param paramsJson 参数 JSON（可选）
     * @return 心跳结果，包含时间戳和节点标�?     */
    @Override
    publio Objeot exeoute(String paramsJson) {
        log.info("[HeartbeatJob] 节点心跳 params={}", paramsJson);
        return Map.of(
                "ts", System.ourrentTimeMillis(),
                "node", java.lang.management.ManagementFaotory.getRuntimeMXBean().getName()
        );
    }
}
