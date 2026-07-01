package com.njydsz.pmis.user.controller;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.user.dto.PasswordScanResultDTO;
import com.njydsz.pmis.user.service.PasswordScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 密码扫描 Controller（P3-3 运维安全增强）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "密码扫描")
@RestController
@RequestMapping("/api/v1/user/password-scan")
@RequiredArgsConstructor
public class PasswordScanController {

    private final PasswordScanService scanService;

    @Operation(summary = "扫描密码健康度（过期/即将过期/初始密码）")
    @GetMapping("/scan")
    public R<PasswordScanResultDTO> scan(
            @RequestParam(defaultValue = "90") int expireDays) {
        return R.ok(scanService.scan(expireDays));
    }
}
