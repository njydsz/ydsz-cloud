package com.njydsz.pmis.common.feign;

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
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE,
                        "userinfo 服务不可用，角色展开失败: " + roleCode);
            }

            @Override
            public Result<Long> getDeptLeaderByDeptId(Long deptId) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE,
                        "userinfo 服务不可用，部门负责人查询失败: " + deptId);
            }

            @Override
            public Result<Long> getDeptLeaderByDeptCode(String deptCode) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE,
                        "userinfo 服务不可用，部门负责人查询失败: " + deptCode);
            }

            @Override
            public Result<List<String>> listRoleCodesByUserId(Long userId) {
                return Result.ok(Collections.emptyList());
            }

            @Override
            public Result<List<String>> listDeptIdsByUserId(Long userId) {
                return Result.ok(Collections.emptyList());
            }

            @Override
            public Result<List<Long>> listUserIdsByDeptId(Long deptId) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE,
                        "userinfo 服务不可用，部门成员展开失败: " + deptId);
            }

            @Override
            public Result<List<Long>> listUserIdsByPositionCode(String positionCode) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE,
                        "userinfo 服务不可用，岗位展开失败: " + positionCode);
            }

            @Override
            public Result<Long> getLeaderByUserId(Long userId) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE,
                        "userinfo 服务不可用，直属上级查询失败: " + userId);
            }
        };
    }
}
