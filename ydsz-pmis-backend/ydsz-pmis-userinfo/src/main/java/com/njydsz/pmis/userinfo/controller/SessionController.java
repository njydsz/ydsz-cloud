package com.njydsz.pmis.userinfo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.userinfo.entity.UserSessionDO;
import com.njydsz.pmis.userinfo.mapper.UserSessionMapper;
import com.njydsz.pmis.userinfo.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户会话 Controller
 *
 * <p>提供用户活跃会话查询、主动下线、管理员分页查询与强制下线能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "用户会话")
@RestController
@RequestMapping("/api/v1/user/session")
@RequiredArgsConstructor
public class SessionController {

    /** 会话服务 */
    private final SessionService sessionService;
    /** 会话 Mapper，用于管理员分页查询 */
    private final UserSessionMapper sessionMapper;

    /**
     * 查询当前用户的活跃会话
     *
     * @return 统一响应结果，包含活跃会话列表
     */
    @Operation(summary = "我的活跃会话")
    @GetMapping("/active")
    public Result<List<UserSessionDO>> active() {
        Long userId = SecurityContext.getUserId();
        return Result.ok(sessionService.listActive(userId));
    }

    /**
     * 下线指定会话
     *
     * @param sessionId 会话 ID
     * @return 统一响应结果
     */
    @Operation(summary = "下线指定会话")
    @DeleteMapping("/{sessionId}")
    public Result<Void> invalidate(@PathVariable String sessionId) {
        sessionService.invalidate(sessionId, "用户主动下线");
        return Result.ok();
    }

    /**
     * 下线当前用户的其他会话（同账号仅保留当前）
     *
     * @return 统一响应结果，包含被下线的会话数
     */
    @Operation(summary = "下线其他会话（同账号仅保留当前）")
    @DeleteMapping("/others")
    public Result<Integer> kickOthers() {
        Long userId = SecurityContext.getUserId();
        return Result.ok(sessionService.kickOthers(userId, ""));
    }

    /**
     * 管理员分页查询所有会话（按用户/状态/IP 过滤）
     *
     * @param page     页码
     * @param size     每页大小
     * @param userId   用户 ID（可选）
     * @param status   会话状态（可选）
     * @param clientIp 客户端 IP（可选）
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "管理员分页查询所有会话（按用户/状态/IP 过滤）")
    @GetMapping("/admin/page")
    public Result<PageResult<UserSessionDO>> adminPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String clientIp) {
        Page<UserSessionDO> p = new Page<>(page, size);
        LambdaQueryWrapper<UserSessionDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(UserSessionDO::getUserId, userId);
        if (StringUtils.hasText(status)) w.eq(UserSessionDO::getStatus, status);
        if (StringUtils.hasText(clientIp)) w.like(UserSessionDO::getClientIp, clientIp);
        w.orderByDesc(UserSessionDO::getLoginAt);
        return Result.ok(PageResult.ofPage(sessionMapper.selectPage(p, w)));
    }

    /**
     * 管理员强制下线任意会话
     *
     * @param sessionId 会话 ID
     * @return 统一响应结果
     */
    @Operation(summary = "管理员强制下线任意会话")
    @DeleteMapping("/admin/{sessionId}")
    public Result<Void> adminKick(@PathVariable String sessionId) {
        sessionService.invalidate(sessionId, "管理员强制下线");
        return Result.ok();
    }
}
