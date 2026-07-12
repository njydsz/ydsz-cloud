package com.njydsz.pmis.userinfo.web.controller.auth;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.userinfo.domain.entity.user.UserAccountDO;
import com.njydsz.pmis.userinfo.infra.mapper.user.UserAccountMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 密码过期状态 Controller（P2-11 安全闭环）
 *
 * <p>提供当前登录用户的密码过期状态查询，前端据此展示密码过期预警横幅。
 *
 * <p>过期策略：
 * <ul>
 *   <li>密码有效期：90 天</li>
 *   <li>即将过期阈值：30 天内（含已过期）</li>
 *   <li>初始密码（pwdChangeCount = 0）：标记为 INITIAL</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Tag(name = "密码过期状态")
@RestController
@RequestMapping("/user/passwordStatus")
@RequiredArgsConstructor
public class PasswordStatusController {

    /** 密码有效期（天） */
    private static final int PASSWORD_EXPIRE_DAYS = 90;
    /** 即将过期阈值（天） */
    private static final int EXPIRING_SOON_DAYS = 30;

    private final UserAccountMapper userAccountMapper;

    /**
     * 查询当前用户密码过期状态
     *
     * @return 密码状态信息
     */
    @Operation(summary = "查询当前用户密码过期状态")
    @GetMapping
    public BaseResponse<Map<String, Object>> getPasswordStatus() {
        String userId = AuthContext.getUserId();
        if (userId == null || userId.isEmpty()) {
            return BaseResponse.ok(Map.of("status", "UNKNOWN"));
        }

        UserAccountDO account = userAccountMapper.selectById(userId);
        if (account == null) {
            return BaseResponse.ok(Map.of("status", "UNKNOWN"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();

        // 初始密码
        boolean isInitial = account.getPwdChangeCount() == null || account.getPwdChangeCount() == 0;
        LocalDateTime lastChange = account.getLastPwdChangeAt();

        if (isInitial) {
            BaseResponse.put("status", "INITIAL");
            BaseResponse.put("message", "您使用的是初始密码，请尽快修改");
            BaseResponse.put("daysRemaining", 0);
            BaseResponse.put("mustChange", true);
        } else if (lastChange == null) {
            BaseResponse.put("status", "EXPIRED");
            BaseResponse.put("message", "密码已过期，请立即修改");
            BaseResponse.put("daysRemaining", 0);
            BaseResponse.put("mustChange", true);
        } else {
            long daysSinceChange = ChronoUnit.DAYS.between(lastChange, now);
            long daysRemaining = PASSWORD_EXPIRE_DAYS - daysSinceChange;

            if (daysRemaining <= 0) {
                BaseResponse.put("status", "EXPIRED");
                BaseResponse.put("message", "密码已过期 " + Math.abs(daysRemaining) + " 天，请立即修改");
                BaseResponse.put("daysRemaining", 0);
                BaseResponse.put("mustChange", true);
            } else if (daysRemaining <= EXPIRING_SOON_DAYS) {
                BaseResponse.put("status", "EXPIRING_SOON");
                BaseResponse.put("message", "密码将在 " + daysRemaining + " 天后过期，建议尽快修改");
                BaseResponse.put("daysRemaining", daysRemaining);
                BaseResponse.put("mustChange", false);
            } else {
                BaseResponse.put("status", "HEALTHY");
                BaseResponse.put("message", "");
                BaseResponse.put("daysRemaining", daysRemaining);
                BaseResponse.put("mustChange", false);
            }
        }

        BaseResponse.put("lastPwdChangeAt", lastChange);
        BaseResponse.put("pwdChangeCount", account.getPwdChangeCount());
        BaseResponse.put("expireDays", PASSWORD_EXPIRE_DAYS);

        return BaseResponse.ok(result);
    }
}
