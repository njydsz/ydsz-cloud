package com.njydsz.pmis.common.safe.captcha.validator;

import com.njydsz.pmis.common.safe.captcha.core.CaptchaStore;
import com.njydsz.pmis.common.safe.captcha.exception.CaptchaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 验证码验证器
 * 负责验证码的校验逻辑,支持大小写不敏感匹配
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class CaptchaValidator {

    private static final Logger log = LoggerFactory.getLogger(CaptchaValidator.class);

    /**
     * 验证码存储器
     */
    private final CaptchaStore captchaStore;

    /**
     * 构建验证码验证器
     *
     * @param captchaStore 验证码存储器
     */
    public CaptchaValidator(CaptchaStore captchaStore) {
        this.captchaStore = captchaStore;
    }

    /**
     * 验证验证码
     * 验证成功后会自动删除验证码,防止重放攻击
     *
     * @param captchaId 验证码 ID
     * @param inputCode 用户输入的验证码
     * @return true 表示验证成功
     * @throws CaptchaException 验证失败时抛出
     */
    public boolean validate(String captchaId, String inputCode) {
        if (captchaId == null || captchaId.isEmpty()) {
            throw new CaptchaException("验证码 ID 不能为空");
        }
        if (inputCode == null || inputCode.isEmpty()) {
            throw new CaptchaException("验证码不能为空", captchaId);
        }

        String storedCode = captchaStore.getAndRemove(captchaId);
        if (storedCode == null) {
            throw new CaptchaException("验证码不存在或已过期", captchaId);
        }

        boolean isValid = storedCode.equalsIgnoreCase(inputCode.trim());
        if (!isValid) {
            log.warn("验证码验证失败, captchaId: [{}]", captchaId);
            throw new CaptchaException("验证码错误", captchaId);
        }

        log.debug("验证码验证成功, captchaId: [{}]", captchaId);
        return true;
    }

    /**
     * 验证滑块验证码
     *
     * @param captchaId 验证码 ID
     * @param sliderX 用户拖动的 X 坐标
     * @param tolerance 容差范围
     * @return true 表示验证成功
     * @throws CaptchaException 验证失败时抛出
     */
    public boolean validateSlider(String captchaId, int sliderX, int tolerance) {
        String storedXStr = captchaStore.getAndRemove(captchaId);
        if (storedXStr == null) {
            throw new CaptchaException("验证码不存在或已过期", captchaId);
        }

        int storedX = Integer.parseInt(storedXStr);
        boolean isValid = Math.abs(sliderX - storedX) <= tolerance;
        if (!isValid) {
            log.warn("滑块验证码验证失败, captchaId: [{}]", captchaId);
            throw new CaptchaException("滑块位置错误", captchaId);
        }

        log.debug("滑块验证码验证成功, captchaId: [{}]", captchaId);
        return true;
    }
}
