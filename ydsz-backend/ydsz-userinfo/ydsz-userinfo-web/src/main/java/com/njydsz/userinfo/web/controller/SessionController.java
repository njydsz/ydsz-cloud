package com.njydsz.userinfo.web.controller.auth;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.userinfo.domain.entity.user.UserSessionDO;
import com.njydsz.userinfo.infra.mapper.user.UserSessionMapper;
import com.njydsz.userinfo.server.service.auth.SessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 用户会话 Controller
 *
 * <p>提供用户活跃会话查询、主动下线、管理员分页查询与强制下线能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "用户会话")
@RestController
@RequestMapping("/user/session")
@RequiredArgsConstructor
@Validated
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
    public BaseResponse<List<UserSessionDO>> active() {
        String userId = AuthContext.getUserId();
        return BaseResponse.success(sessionService.listActive(userId));
    }

    /**
     * 下线指定会话
     *
     * @param sessionId 会话 ID
     * @return 统一响应结果
     */
    @Operation(summary = "下线指定会话")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @DeleteMapping("/{sessionId}")
    public BaseResponse<Void> invalidate(@PathVariable String sessionId) {
        sessionService.invalidate(sessionId, "用户主动下线");
        return BaseResponse.success();
    }

    /**
     * 下线当前用户的其他会话（同账号仅保留当前）
     *
     * @return 统一响应结果，包含被下线的会话数
     */
    @Operation(summary = "下线其他会话（同账号仅保留当前）")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @DeleteMapping("/others")
    public BaseResponse<Integer> kickOthers() {
        String userId = AuthContext.getUserId();
        return BaseResponse.success(sessionService.kickOthers(userId, ""));
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
    public BaseResponse<PageResponse<List<UserSessionDO>>> adminPage(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String clientIp) {
        LambdaQueryWrapper<UserSessionDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(UserSessionDO::getUserId, userId);
        if (StringUtils.hasText(status)) w.eq(UserSessionDO::getStatus, status);
        if (StringUtils.hasText(clientIp)) w.like(UserSessionDO::getClientIp, clientIp);
        w.orderByDesc(UserSessionDO::getLoginAt);
        Page<UserSessionDO> p = sessionMapper.selectPage(new Page<>(page, size), w);
        return BaseResponse.success(PageResponse.success(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords()));
    }

    /**
     * 管理员强制下线任意会话
     *
     * @param sessionId 会话 ID
     * @return 统一响应结果
     */
    @Operation(summary = "管理员强制下线任意会话")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @DeleteMapping("/admin/{sessionId}")
    public BaseResponse<Void> adminKick(@PathVariable String sessionId) {
        sessionService.invalidate(sessionId, "管理员强制下线");
        return BaseResponse.success();
    }
}
