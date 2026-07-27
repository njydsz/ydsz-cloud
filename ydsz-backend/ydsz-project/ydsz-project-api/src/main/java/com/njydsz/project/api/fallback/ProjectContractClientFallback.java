package com.njydsz.project.api.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.client.ProjectContractClient;

import lombok.extern.slf4j.Slf4j;

/**
 * ProjectContractClient 降级工厂。
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
            public BaseResponse<Map<String, Object>> getById(String id) {
                return BaseResponse.error("合同服务不可用");
            }

            @Override
            public BaseResponse<List<Map<String, Object>>> listByInitiationId(String initiationId) {
                return BaseResponse.error("合同服务不可用");
            }
        };
    }
}
