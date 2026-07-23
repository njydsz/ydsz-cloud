package com.njydsz.userinfo.web.controller.auth;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.dto.auth.PasswordScanResultDTO;
import com.njydsz.userinfo.server.service.auth.PasswordScanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 密码扫描 Controller（P3-3 运维安全增强）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "密码扫描")
@RestController
@RequestMapping("/user/passwordScan")
@RequiredArgsConstructor
@Validated
public class PasswordScanController {

    /** 密码扫描服务 */
    private final PasswordScanService scanService;

    /**
     * 扫描密码健康度（过期/即将过期/初始密码）
     *
     * @param expireDays 密码过期天数阈值，默认 90 天
     * @return 统一响应结果，包含扫描结果
     */
    @Operation(summary = "扫描密码健康度（过期/即将过期/初始密码）")
    @GetMapping("/scan")
    public BaseResponse<PasswordScanResultDTO> scan(
            @RequestParam(defaultValue = "90") int expireDays) {
        return BaseResponse.success(scanService.scan(expireDays));
    }
}
