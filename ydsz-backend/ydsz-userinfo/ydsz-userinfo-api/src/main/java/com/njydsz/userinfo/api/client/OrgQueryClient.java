package com.njydsz.userinfo.api.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

/**
 * 组织架构查询 Feign 客户端（供跨服务调用）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-userinfo", contextId = "orgQueryClient",
        fallbackFactory = OrgQueryClientFallback.class)
public interface OrgQueryClient {

    @GetMapping("/api/internal/user/query")
    BaseResponse<UserAccountVO> queryUserById(@RequestParam String userId);

    @GetMapping("/api/internal/dept/tree")
    BaseResponse<List<DepartmentTreeVO>> getDeptTree();

    @GetMapping("/api/internal/dept/list")
    BaseResponse<List<DepartmentTreeVO>> getDeptList();
}
