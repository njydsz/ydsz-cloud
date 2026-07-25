package com.njydsz.userinfo.web.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.userinfo.domain.entity.UserAccountDO;
import com.njydsz.userinfo.domain.entity.DepartmentDO;
import com.njydsz.userinfo.server.service.UserAccountService;
import com.njydsz.userinfo.server.service.DepartmentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Internal API controller for cross-service Feign calls.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalApiController {

    private final UserAccountService userAccountService;
    private final DepartmentService departmentService;

    @GetMapping("/user/query")
    public Map<String, Object> queryUserById(@RequestParam String userId) {
        UserAccountDO user = userAccountService.getById(userId);
        if (user == null) {
            return new HashMap<>();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("realName", user.getRealName());
        result.put("phone", user.getPhone());
        result.put("email", user.getEmail());
        result.put("status", user.getStatus());
        return result;
    }

    @GetMapping("/user/info")
    public Map<String, Object> getUserInfo(@RequestParam String userId) {
        return queryUserById(userId);
    }

    @GetMapping("/dept/tree")
    public List<DepartmentDO> getDeptTree() {
        return departmentService.list();
    }
}
