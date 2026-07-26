package com.njydsz.project.api.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.client.ProjectInitiationClient;

import lombok.extern.slf4j.Slf4j;

/**
 * ProjectInitiationClient 降级工厂
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ProjectInitiationClientFallback implements FallbackFactory<ProjectInitiationClient> {

    @Override
    public ProjectInitiationClient create(Throwable cause) {
        log.warn("[ProjectInitiationClient] 降级触发：{}", cause.getMessage());
        return new ProjectInitiationClient() {
            @Override
            public BaseResponse<?> getById(String id) {
                log.warn("[ProjectInitiationClient] getById 降级：id={}, reason=project 服务不可用", id);
                return BaseResponse.error("立项服务不可用");
            }

            @Override
            public BaseResponse<?> page(Object query) {
                log.warn("[ProjectInitiationClient] page 降级：reason=project 服务不可用");
                return BaseResponse.error("立项服务不可用");
            }

            @Override
            public BaseResponse<?> getByCode(String projectCode) {
                log.warn("[ProjectInitiationClient] getByCode 降级：projectCode={}, reason=project 服务不可用",
                        projectCode);
                return BaseResponse.error("立项服务不可用");
            }
        };
    }
}
