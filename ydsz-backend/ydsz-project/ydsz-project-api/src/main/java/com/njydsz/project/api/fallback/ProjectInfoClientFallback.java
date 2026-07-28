package com.njydsz.project.api.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.client.ProjectInfoClient;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link ProjectInfoClient} 的 FallbackFactory。
 *
 * <p>项目管理服务不可用时降级返回 null，仅记录 WARN 日志。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ProjectInfoClientFallback implements FallbackFactory<ProjectInfoClient> {

    @Override
    public ProjectInfoClient create(Throwable cause) {
        log.warn("[ProjectInfoClient] 降级触发: {}", cause.getMessage());
        return new ProjectInfoClient() {
            @Override
            public BaseResponse<String> getProjectName(String projectId) {
                log.warn("[ProjectInfoClient] getProjectName 降级: projectId={}, reason=项目管理服务不可用",
                        projectId);
                return BaseResponse.success(null);
            }

            @Override
            public BaseResponse<String> getProjectStatus(String projectId) {
                log.warn("[ProjectInfoClient] getProjectStatus 降级: projectId={}, reason=项目管理服务不可用",
                        projectId);
                return BaseResponse.success(null);
            }
        };
    }
}
