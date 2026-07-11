package com.njydsz.pmis.userinfo.api.fallback;
import com.njydsz.pmis.userinfo.api.client.OrgQueryClient;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * OrgQueryClient 降级工厂（P1-5）
 *
 * <p>userinfo 服务不可用时返回空列表，使 workflow 引擎回退到
 * {@code node.ext.emptyStrategy} 兜底处理，保证主流程不阻塞。
 *
 * @author ydsz-pmis-team
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
            public Result<List<Long>> listUserIdsByRoleCode(String roleCode) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<String> getDeptLeaderByDeptId(Long deptId) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<String> getDeptLeaderByDeptCode(String deptCode) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<List<String>> listRoleCodesByUserId(String userId) {
                return Result.ok(Collections.emptyList());
            }

            @Override
            public Result<List<String>> listDeptIdsByUserId(String userId) {
                return Result.ok(Collections.emptyList());
            }

            @Override
            public Result<List<Long>> listUserIdsByDeptId(Long deptId) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<List<Long>> listUserIdsByPositionCode(String positionCode) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<String> getLeaderByUserId(String userId) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
