package com.njydsz.project.api.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.client.ProjectContractClient;

import lombok.extern.slf4j.Slf4j;

/**
 * ProjectContractClient 降级工厂
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ProjectContractClientFallback implements FallbackFactory<ProjectContractClient> {

    @Override
    public ProjectContractClient create(Throwable cause) {
        log.warn("[ProjectContractClient] 降级触发：{}", cause.getMessage());
        return new ProjectContractClient() {
            @Override
            public BaseResponse<?> getById(String id) {
                log.warn("[ProjectContractClient] getById 降级：id={}, reason=project 服务不可用", id);
                return BaseResponse.error("合同服务不可用");
            }

            @Override
            public BaseResponse<?> page(Object query) {
                log.warn("[ProjectContractClient] page 降级：reason=project 服务不可用");
                return BaseResponse.error("合同服务不可用");
            }

            @Override
            public BaseResponse<?> listByInitiationId(String initiationId) {
                log.warn("[ProjectContractClient] listByInitiationId 降级：initiationId={}, reason=project 服务不可用",
                        initiationId);
                return BaseResponse.error("合同服务不可用");
            }
        };
    }
}
