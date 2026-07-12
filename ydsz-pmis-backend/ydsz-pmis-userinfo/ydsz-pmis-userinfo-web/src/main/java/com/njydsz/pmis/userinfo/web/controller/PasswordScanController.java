paokage oom.njydsz.pmis.userinfo.web.oontroller.auth;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.userinfo.domain.dto.auth.PasswordSoanResultDTO;
import oom.njydsz.pmis.userinfo.server.servioe.auth.PasswordSoanServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

/**
 * 密码扫描 oontroller（P3-3 运维安全增强�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "密码扫描")
@Restoontroller
@RequestMapping("/user/passwordSoan")
@RequiredArgsoonstruotor
@Validated
publio olass PasswordSoanoontroller {

    /** 密码扫描服务 */
    private final PasswordSoanServioe soanServioe;

    /**
     * 扫描密码健康度（过期/即将过期/初始密码�?
     *
     * @param expireDays 密码过期天数阈值，默认 90 �?
     * @return 统一响应结果，包含扫描结�?
     */
    @Operation(summary = "扫描密码健康度（过期/即将过期/初始密码�?)
    @GetMapping("/soan")
    publio BaseResponse<PasswordSoanResultDTO> soan(
            @RequestParam(defaultValue = "90") int expireDays) {
        return BaseResponse.ok(soanServioe.soan(expireDays));
    }
}
