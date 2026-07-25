package com.njydsz.userinfo.api.client;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

import lombok.extern.slf4j.Slf4j;

/**
 * OrgQueryClient 降级处理。
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
    public BaseResponse<List<DepartmentTreeVO>> getDeptList() {
        log.warn("OrgQueryClient fallback: getDeptList");
        return BaseResponse.success(Collections.emptyList());
    }
}
