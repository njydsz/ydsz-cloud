package com.njydsz.pmis.scheduler.handler;

import com.njydsz.pmis.common.job.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 示例任务处理器 - 心跳上报
 *
 * <p>Bean 名称 = handler，配置任务 handler = heartbeatHandler 即可。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component("heartbeatHandler")
public class HeartbeatJobHandler implements JobHandler {

    /**
     * 执行心跳上报
     *
     * @param paramsJson 参数 JSON（可选）
     * @return 心跳结果，包含时间戳和节点标识
     */
    @Override
    public Object execute(String paramsJson) {
        log.info("[HeartbeatJob] 节点心跳 params={}", paramsJson);
        return Map.of(
                "ts", System.currentTimeMillis(),
                "node", java.lang.management.ManagementFactory.getRuntimeMXBean().getName()
        );
    }
}
