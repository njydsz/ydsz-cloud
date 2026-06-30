package com.njydsz.pmis.audit.controller;

import com.njydsz.pmis.audit.entity.LoginAuditDO;
import com.njydsz.pmis.audit.mapper.LoginAuditMapper;
import com.njydsz.pmis.common.api.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @Operation(summary = "按用户名查询登录历史")
    @GetMapping("/by-username")
    public R<List<LoginAuditDO>> byUsername(@RequestParam String username,
                                            @RequestParam(defaultValue = "50") int limit) {
        return R.ok(loginAuditMapper.selectByUsername(username, Math.min(limit, 200)));
    }

    @Operation(summary = "统计某 IP 短期登录失败次数")
    @GetMapping("/count-by-ip")
    public R<Long> countByIp(@RequestParam String ip,
                             @RequestParam String status,
                             @RequestParam(defaultValue = "10") int sinceMinutes) {
        return R.ok(loginAuditMapper.countByIpSince(ip, status, sinceMinutes));
    }
}
