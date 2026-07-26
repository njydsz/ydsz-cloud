package com.njydsz.userinfo.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.service.DepartmentService;
import com.njydsz.userinfo.server.service.UserAccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 内部 API Controller（供跨服务 Feign 调用）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Tag(name = "内部 API", description = "跨服务 Feign 调用接口")
public class InternalApiController {

    private final UserAccountService userAccountService;
    private final DepartmentService departmentService;

    @GetMapping("/user/info")
    @Operation(summary = "根据 userId 查询用户信息（内部调用）")
    public BaseResponse<UserAccountVO> getUserInfo(@RequestParam String userId) {
        return BaseResponse.success(userAccountService.getById(userId));
    }

    @GetMapping("/dept/tree")
    @Operation(summary = "查询部门树形结构（内部调用）")
    public BaseResponse<List<DepartmentTreeVO>> getDeptTree() {
        return BaseResponse.success(departmentService.tree());
    }

    @GetMapping("/dept/list")
    @Operation(summary = "查询部门列表（内部调用）")
    public BaseResponse<List<DepartmentVO>> getDeptList() {
        return BaseResponse.success(departmentService.list());
    }
}
