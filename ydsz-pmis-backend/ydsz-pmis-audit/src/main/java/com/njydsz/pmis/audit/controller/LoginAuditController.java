package com.njydsz.pmis.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.audit.entity.LoginAuditDO;
import com.njydsz.pmis.audit.mapper.LoginAuditMapper;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录审计查询 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "登录审计")
@RestController
@RequestMapping("/api/v1/audit/login")
@RequiredArgsConstructor
public class LoginAuditController {

    private final LoginAuditMapper loginAuditMapper;

    @Operation(summary = "分页查询")
    @PrePermission("audit:login:view")
    @GetMapping("/page")
    public Result<PageResult<LoginAuditDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String loginIp) {
        Page<LoginAuditDO> p = new Page<>(page, size);
        LambdaQueryWrapper<LoginAuditDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) w.like(LoginAuditDO::getUsername, username);
        if (StringUtils.hasText(status)) w.eq(LoginAuditDO::getStatus, status);
        if (StringUtils.hasText(loginIp)) w.like(LoginAuditDO::getLoginIp, loginIp);
        w.orderByDesc(LoginAuditDO::getLoginAt);
        return Result.ok(PageResult.ofPage(loginAuditMapper.selectPage(p, w)));
    }

    @Operation(summary = "按用户名查询登录历史")
    @PrePermission("audit:login:view")
    @GetMapping("/by-username")
    public Result<List<LoginAuditDO>> byUsername(@RequestParam String username,
                                            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(loginAuditMapper.selectByUsername(username, Math.min(limit, 200)));
    }

    @Operation(summary = "统计某 IP 短期登录失败次数")
    @PrePermission("audit:login:view")
    @GetMapping("/count-by-ip")
    public Result<Long> countByIp(@RequestParam String ip,
                             @RequestParam String status,
                             @RequestParam(defaultValue = "10") int sinceMinutes) {
        return Result.ok(loginAuditMapper.countByIpSince(ip, status, sinceMinutes));
    }
}
