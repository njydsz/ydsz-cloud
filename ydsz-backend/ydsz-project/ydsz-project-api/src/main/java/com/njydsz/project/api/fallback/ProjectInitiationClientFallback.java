package com.njydsz.project.api.fallback;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.client.ProjectInitiationClient;

import lombok.extern.slf4j.Slf4j;

/**
 * ProjectInitiationClient 降级工厂。
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
            public BaseResponse<Map<String, Object>> getById(String id) {
                log.warn("[ProjectInitiationClient] getById 降级：id={}, reason=project 服务不可用", id);
                return BaseResponse.error("立项服务不可用");
            }

            @Override
            public BaseResponse<Map<String, Object>> getByCode(String projectCode) {
                log.warn("[ProjectInitiationClient] getByCode 降级：projectCode={}, reason=project 服务不可用",
                        projectCode);
                return BaseResponse.error("立项服务不可用");
            }

            @Override
            public BaseResponse<List<Map<String, Object>>> listByPmId(String pmId) {
                log.warn("[ProjectInitiationClient] listByPmId 降级：pmId={}, reason=project 服务不可用", pmId);
                return BaseResponse.error("立项服务不可用");
            }
        };
    }
}
