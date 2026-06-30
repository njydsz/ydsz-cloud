package com.njydsz.pmis.user.controller;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.user.entity.UserSessionDO;
import com.njydsz.pmis.user.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话管理 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "用户会话")
@RestController
@RequestMapping("/api/v1/user/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @Operation(summary = "我的活跃会话")
    @GetMapping("/active")
    public R<List<UserSessionDO>> active() {
        Long userId = SecurityContext.getUserId();
        return R.ok(sessionService.listActive(userId));
    }

    @Operation(summary = "下线指定会话")
    @DeleteMapping("/{sessionId}")
    public R<Void> invalidate(@PathVariable String sessionId) {
        sessionService.invalidate(sessionId, "用户主动下线");
        return R.ok();
    }

    @Operation(summary = "下线其他会话（同账号仅保留当前）")
    @DeleteMapping("/others")
    public R<Integer> kickOthers() {
        Long userId = SecurityContext.getUserId();
        return R.ok(sessionService.kickOthers(userId, ""));
    }
}
