package com.njydsz.pmis.scheduler.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 示例任务处理器 - 心跳上报
 *
 * <p>Bean 名称 = handler，配置任务 handler = heartbeatHandler 即可。
 */
@Slf4j
@Component("heartbeatHandler")
public class HeartbeatJobHandler implements JobHandler {

    @Override
    public Object execute(String paramsJson) {
        log.info("[HeartbeatJob] 节点心跳 params={}", paramsJson);
        return java.util.Map.of(
                "ts", System.currentTimeMillis(),
                "node", java.lang.management.ManagementFactory.getRuntimeMXBean().getName()
        );
    }
}
