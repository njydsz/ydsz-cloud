package com.njydsz.project.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.model.Result;
import com.njydsz.common.exception.custom.SysException;

/**
 * ProjectInitiationClient 降级工厂。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class ProjectInitiationClientFallbackFactory implements FallbackFactory<ProjectInitiationClient> {

    @Override
    public ProjectInitiationClient create(Throwable cause) {
        log.error("[Feign降级] ProjectInitiationClient 调用失败", cause);
        return new ProjectInitiationClient() {
            @Override
            public Result<?> getById(String id) {
                throw new SysException("项目服务暂不可用，请稍后重试");
            }
        };
    }
}
