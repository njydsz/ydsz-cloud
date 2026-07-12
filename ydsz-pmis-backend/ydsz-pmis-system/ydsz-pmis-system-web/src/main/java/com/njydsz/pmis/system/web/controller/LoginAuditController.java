package com.njydsz.pmis.system.web.controller.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.system.domain.entity.audit.LoginAuditDO;
import com.njydsz.pmis.system.infra.mapper.audit.LoginAuditMapper;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.core.response.PageResponse;
import com.njydsz.pmis.common.core.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 登录审计查询 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "登录审计", description = "登录审计日志查询接口")
@RestController
@RequestMapping("/audit/login")
@RequiredArgsConstructor
@Validated
public class LoginAuditController {

    /** 登录审计 Mapper */
    private final LoginAuditMapper loginAuditMapper;

    /**
     * 分页查询登录审计日志
     *
     * @param page     页码
     * @param size     每页大小
     * @param username 用户名（可选，模糊匹配）
     * @param status   登录状态（可选）
     * @param loginIp  登录 IP（可选，模糊匹配）
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "分页查询")
    @PrePermission("audit:login:view")
    @GetMapping("/page")
    public BaseResponse<PageResponse<LoginAuditDO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "用户名") @RequestParam(required = false) String username,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "登录IP") @RequestParam(required = false) String loginIp) {
        Page<LoginAuditDO> p = new Page<>(page, size);
        LambdaQueryWrapper<LoginAuditDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) w.like(LoginAuditDO::getUsername, username);
        if (StringUtils.hasText(status)) w.eq(LoginAuditDO::getStatus, status);
        if (StringUtils.hasText(loginIp)) w.like(LoginAuditDO::getLoginIp, loginIp);
        w.orderByDesc(LoginAuditDO::getLoginAt);
        return BaseResponse.ok(PageResponse.ofPage(loginAuditMapper.selectPage(p, w)));
    }

    @Operation(summary = "按用户名查询登录历史")
    @PrePermission("audit:login:view")
    @GetMapping("/byUsername")
    /**
     * 按用户名查询登录历史
     *
     * @param username 用户名
     * @param limit    最大条数
     * @return 统一响应结果，包含登录审计列表
     */
    public BaseResponse<List<LoginAuditDO>> byUsername(
            @Parameter(description = "用户名") @RequestParam String username,
            @Parameter(description = "最大条数") @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return BaseResponse.ok(loginAuditMapper.selectByUsername(username, Math.min(limit, 200)));
    }

    @Operation(summary = "统计某 IP 短期登录失败次数")
    @PrePermission("audit:login:view")
    @GetMapping("/countByIp")
    /**
     * 统计某 IP 在指定时间窗口内的登录次数
     *
     * @param ip            IP 地址
     * @param status        登录状态
     * @param sinceMinutes  统计时间窗口（分钟）
     * @return 统一响应结果，包含匹配的记录数
     */
    public BaseResponse<Long> countByIp(
            @Parameter(description = "IP地址") @RequestParam String ip,
            @Parameter(description = "状态") @RequestParam String status,
            @Parameter(description = "统计时间窗口（分钟）") @RequestParam(defaultValue = "10") int sinceMinutes) {
        return BaseResponse.ok(loginAuditMapper.countByIpSince(ip, status, sinceMinutes));
    }
}
