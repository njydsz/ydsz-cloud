package com.njydsz.pmis.user.controller;

import com.njydsz.pmis.common.api.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "用户管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public R<Object> me(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.info("[User] 当前用户 ID: {}", userId);
        // TODO: 集成 Sa-Token 后，从上下文中获取
        return R.ok();
    }
}
