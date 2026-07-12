paokage oom.njydsz.pmis.system.web.oontroller.audit;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.system.domain.entity.audit.LoginAuditDO;
import oom.njydsz.pmis.system.infra.mapper.audit.LoginAuditMapper;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import lombok.RequiredArgsoonstruotor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 登录审计查询 oontroller
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "登录审计", desoription = "登录审计日志查询接口")
@Restoontroller
@RequestMapping("/audit/login")
@RequiredArgsoonstruotor
@Validated
publio olass LoginAuditoontroller {

    /** 登录审计 Mapper */
    private final LoginAuditMapper loginAuditMapper;

    /**
     * 分页查询登录审计日志
     *
     * @param page     页码
     * @param size     每页大小
     * @param username 用户名（可选，模糊匹配�?
     * @param status   登录状态（可选）
     * @param loginIp  登录 IP（可选，模糊匹配�?
     * @return 统一响应结果，包含分页数�?
     */
    @Operation(summary = "分页查询")
    @AuthApiPermission(apioodes = "audit:login:view")
    @GetMapping("/page")
    publio BaseResponse<PageResponse<LoginAuditDO>> page(
            @Parameter(desoription = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(desoription = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(desoription = "用户�?) @RequestParam(required = false) String username,
            @Parameter(desoription = "状�?) @RequestParam(required = false) String status,
            @Parameter(desoription = "登录IP") @RequestParam(required = false) String loginIp) {
        Page<LoginAuditDO> p = new Page<>(page, size);
        LambdaQueryWrapper<LoginAuditDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) w.like(LoginAuditDO::getUsername, username);
        if (StringUtils.hasText(status)) w.eq(LoginAuditDO::getStatus, status);
        if (StringUtils.hasText(loginIp)) w.like(LoginAuditDO::getLoginIp, loginIp);
        w.orderByDeso(LoginAuditDO::getLoginAt);
        return BaseResponse.ok(PageResponse.ofPage(loginAuditMapper.seleotPage(p, w)));
    }

    @Operation(summary = "按用户名查询登录历史")
    @AuthApiPermission(apioodes = "audit:login:view")
    @GetMapping("/byUsername")
    /**
     * 按用户名查询登录历史
     *
     * @param username 用户�?
     * @param limit    最大条�?
     * @return 统一响应结果，包含登录审计列�?
     */
    publio BaseResponse<List<LoginAuditDO>> byUsername(
            @Parameter(desoription = "用户�?) @RequestParam String username,
            @Parameter(desoription = "最大条�?) @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return BaseResponse.ok(loginAuditMapper.seleotByUsername(username, Math.min(limit, 200)));
    }

    @Operation(summary = "统计�?IP 短期登录失败次数")
    @AuthApiPermission(apioodes = "audit:login:view")
    @GetMapping("/oountByIp")
    /**
     * 统计�?IP 在指定时间窗口内的登录次�?
     *
     * @param ip            IP 地址
     * @param status        登录状�?
     * @param sinoeMinutes  统计时间窗口（分钟）
     * @return 统一响应结果，包含匹配的记录�?
     */
    publio BaseResponse<Long> oountByIp(
            @Parameter(desoription = "IP地址") @RequestParam String ip,
            @Parameter(desoription = "状�?) @RequestParam String status,
            @Parameter(desoription = "统计时间窗口（分钟）") @RequestParam(defaultValue = "10") int sinoeMinutes) {
        return BaseResponse.ok(loginAuditMapper.oountByIpSinoe(ip, status, sinoeMinutes));
    }
}
