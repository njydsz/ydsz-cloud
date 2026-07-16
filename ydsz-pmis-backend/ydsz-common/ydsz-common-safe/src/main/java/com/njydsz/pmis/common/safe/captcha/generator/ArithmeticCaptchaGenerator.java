package com.njydsz.common.safe.captcha.generator;

import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.safe.captcha.core.CaptchaGenerator;
import com.njydsz.common.safe.captcha.core.CaptchaResult;

/**
 * 算术验证码生成器
 * 生成简单的加减法运算验证码,如 "3 + 5 = ?"
 *
 * @since 1.0.0
 * 
 */
public class ArithmeticCaptchaGenerator implements CaptchaGenerator {

    private static final Logger log = LoggerFactory.getLogger(ArithmeticCaptchaGenerator.class);

    /**
     * 随机数生成器
     */
    private final SecureRandom random = new SecureRandom();

    /**
     * 最大操作数
     */
    private final int maxNumber;

    /**
     * 构建算术验证码生成器
     *
     * @param maxNumber 最大操作数
     */
    public ArithmeticCaptchaGenerator(int maxNumber) {
        this.maxNumber = maxNumber;
    }

    /**
     * 构建默认算术验证码生成器(最大操作数 10)
     */
    public ArithmeticCaptchaGenerator() {
        this(10);
    }

    @Override
    public CaptchaResult generate() {
        int a = random.nextInt(maxNumber) + 1;
        int b = random.nextInt(maxNumber) + 1;
        int result = a + b;
        String question = a + " + " + b + " = ?";

        CaptchaResult captchaResult = new CaptchaResult();
        captchaResult.setCaptchaCode(String.valueOf(result));
        log.debug("生成算术验证码: {}", question);
        return captchaResult;
    }

    @Override
    public String getType() {
        return "ARITHMETIC";
    }
}
