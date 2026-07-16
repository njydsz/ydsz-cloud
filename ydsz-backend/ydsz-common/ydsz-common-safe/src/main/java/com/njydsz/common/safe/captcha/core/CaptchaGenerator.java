package com.njydsz.common.safe.captcha.core;

/**
 * 验证码生成器接口
 *
 * <p>定义不同类型验证码的生成行为，实现类可根据具体验证码类型
 * （图形验证码、算术验证码、滑块验证码等）生成相应的验证码数据。
 *
 * @since 1.0.0
 * 
 * @see CaptchaResult
 */
public interface CaptchaGenerator {

    /**
     * 生成验证码
     *
     * @return 验证码对象,包含验证码文本和图片/数据
     */
    CaptchaResult generate();

    /**
     * 获取验证码类型
     *
     * @return 验证码类型
     */
    String getType();
}
