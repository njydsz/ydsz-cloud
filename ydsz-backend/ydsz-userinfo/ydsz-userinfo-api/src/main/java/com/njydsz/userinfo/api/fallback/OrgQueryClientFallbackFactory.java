package com.njydsz.userinfo.api.fallback;
import java.util.Collections;
import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.userinfo.api.client.OrgQueryClient;

import lombok.extern.slf4j.Slf4j;

/**
 * OrgQueryClient 降级工厂（P1-5）
 *
 * <p>userinfo 服务不可用时返回空列表，使 workflow 引擎回退到
 * {@code node.ext.emptyStrategy} 兜底处理，保证主流程不阻塞。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Component
public class OrgQueryClientFallbackFactory implements FallbackFactory<OrgQueryClient> {

    @Override
    public OrgQueryClient create(Throwable cause) {
        log.warn("[OrgQueryClient] Feign fallback triggered: {}",
                cause == null ? "null" : cause.getMessage());
        return new OrgQueryClient() {
            @Override
            public BaseResponse<List<Long>> listUserIdsByRoleCode(String roleCode) {
                return BaseResponse.error(BaseResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public BaseResponse<String> getDeptLeaderByDeptId(Long deptId) {
                return BaseResponse.error(BaseResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public BaseResponse<String> getDeptLeaderByDeptCode(String deptCode) {
                return BaseResponse.error(BaseResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public BaseResponse<List<String>> listRoleCodesByUserId(String userId) {
                return BaseResponse.success(Collections.emptyList());
            }

            @Override
            public BaseResponse<List<String>> listDeptIdsByUserId(String userId) {
                return BaseResponse.success(Collections.emptyList());
            }

            @Override
            public BaseResponse<List<Long>> listUserIdsByDeptId(Long deptId) {
                return BaseResponse.error(BaseResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public BaseResponse<List<Long>> listUserIdsByPositionCode(String positionCode) {
                return BaseResponse.error(BaseResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public BaseResponse<String> getLeaderByUserId(String userId) {
                return BaseResponse.error(BaseResultCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
