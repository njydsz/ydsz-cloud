package com.njydsz.pmis.common.safe.captcha.enums;

/**
 * 验证码类型枚举
 *
 * <p>定义系统支持的验证码类型，包括图形验证码、算术验证码、滑块验证码、
 * 点选验证码、短信验证码和邮件验证码等。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public enum CaptchaType {

    /**
     * 图形验证码(字母数字混合)
     */
    IMAGE,

    /**
     * 算术验证码(加减乘除运算)
     */
    ARITHMETIC,

    /**
     * 滑块验证码(拖动滑块到指定位置)
     */
    SLIDER,

    /**
     * 点选验证码(按顺序点击指定文字)
     */
    CLICK,

    /**
     * 短信验证码
     */
    SMS,

    /**
     * 邮件验证码
     */
    EMAIL
}
