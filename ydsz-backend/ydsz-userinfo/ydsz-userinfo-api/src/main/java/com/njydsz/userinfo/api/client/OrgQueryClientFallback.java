package com.njydsz.userinfo.api.client;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

import lombok.extern.slf4j.Slf4j;

/**
 * OrgQueryClient 降级处理。
 *
 * <p>所有方法在 Feign 调用失败时返回安全空值（空列表 / null），
 * 不抛异常，避免阻断调用方主流程（如工作流引擎的办理人展开）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class OrgQueryClientFallback implements OrgQueryClient {

    @Override
    public BaseResponse<UserAccountVO> queryUserById(String userId) {
        log.warn("OrgQueryClient fallback: queryUserById={}", userId);
        return BaseResponse.error("服务降级：用户查询不可用");
    }

    @Override
    public BaseResponse<List<DepartmentTreeVO>> getDeptTree() {
        log.warn("OrgQueryClient fallback: getDeptTree");
        return BaseResponse.success(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<DepartmentVO>> getDeptList() {
        log.warn("OrgQueryClient fallback: getDeptList");
        return BaseResponse.success(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<String>> listUserIdsByRoleCode(String roleCode) {
        log.warn("OrgQueryClient fallback: listUserIdsByRoleCode roleCode={}", roleCode);
        return BaseResponse.success(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<String>> listRoleCodesByUserId(String userId) {
        log.warn("OrgQueryClient fallback: listRoleCodesByUserId userId={}", userId);
        return BaseResponse.success(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<String>> listDeptIdsByUserId(String userId) {
        log.warn("OrgQueryClient fallback: listDeptIdsByUserId userId={}", userId);
        return BaseResponse.success(Collections.emptyList());
    }

    @Override
    public BaseResponse<String> getLeaderByUserId(String userId) {
        log.warn("OrgQueryClient fallback: getLeaderByUserId userId={}", userId);
        return BaseResponse.success(null);
    }

    @Override
    public BaseResponse<List<String>> listUserIdsByPositionCode(String positionCode) {
        log.warn("OrgQueryClient fallback: listUserIdsByPositionCode positionCode={}", positionCode);
        return BaseResponse.success(Collections.emptyList());
    }

    @Override
    public BaseResponse<String> getDeptLeaderByDeptId(String deptId) {
        log.warn("OrgQueryClient fallback: getDeptLeaderByDeptId deptId={}", deptId);
        return BaseResponse.success(null);
    }

    @Override
    public BaseResponse<String> getDeptLeaderByDeptCode(String deptCode) {
        log.warn("OrgQueryClient fallback: getDeptLeaderByDeptCode deptCode={}", deptCode);
        return BaseResponse.success(null);
    }
}
