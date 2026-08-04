package com.njydsz.common.safe.captcha.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.safe.captcha.core.CaptchaGenerator;
import com.njydsz.common.safe.captcha.core.CaptchaRateLimiter;
import com.njydsz.common.safe.captcha.core.CaptchaStore;
import com.njydsz.common.safe.captcha.generator.ArithmeticCaptchaGenerator;
import com.njydsz.common.safe.captcha.generator.ImageCaptchaGenerator;
import com.njydsz.common.safe.captcha.properties.CaptchaProperties;
import com.njydsz.common.safe.captcha.store.LocalCaptchaStore;
import com.njydsz.common.safe.captcha.validator.CaptchaValidator;

/**
 * 图形验证码自动配置。
 *
 * <p>注册图形验证码（Captcha）生成器与校验器 Bean，支持字母数字混合、算术运算、滑动拼图三种类型。
 *
 * <p>通过 {@code ydsz.safe.captcha.*} 配置类型、长度、有效期、Redis 存储 Key 前缀。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
@EnableConfigurationProperties(CaptchaProperties.class)
@ConditionalOnProperty(prefix = "ydsz.safe.captcha", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CaptchaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CaptchaAutoConfiguration.class);

    /**
     * 注册本地验证码存储器
     *
     * @return 本地验证码存储器实例
     */
    @Bean
    @ConditionalOnMissingBean(CaptchaStore.class)
    public CaptchaStore localCaptchaStore() {
        log.info("注册本地验证码存储器");
        return new LocalCaptchaStore();
    }

    /**
     * 注册图形验证码生成器
     *
     * @param captchaProperties 验证码配置属性
     * @return 图形验证码生成器实例
     */
    @Bean
    @ConditionalOnMissingBean(name = "imageCaptchaGenerator")
    public CaptchaGenerator imageCaptchaGenerator(CaptchaProperties captchaProperties) {
        log.info("注册图形验证码生成器, 长度: {}, 尺寸: {}x{}",
                captchaProperties.getLength(), captchaProperties.getWidth(), captchaProperties.getHeight());
        return new ImageCaptchaGenerator(
                captchaProperties.getLength(),
                captchaProperties.getWidth(),
                captchaProperties.getHeight()
        );
    }

    /**
     * 注册算术验证码生成器
     *
     * @return 算术验证码生成器实例
     */
    @Bean
    @ConditionalOnMissingBean(name = "arithmeticCaptchaGenerator")
    public CaptchaGenerator arithmeticCaptchaGenerator() {
        log.info("注册算术验证码生成器");
        return new ArithmeticCaptchaGenerator();
    }

    /**
     * 注册验证码验证器
     *
     * @param captchaStore 验证码存储器
     * @return 验证码验证器实例
     */
    @Bean
    @ConditionalOnMissingBean(CaptchaValidator.class)
    public CaptchaValidator captchaValidator(CaptchaStore captchaStore) {
        log.info("注册验证码验证器");
        return new CaptchaValidator(captchaStore);
    }

    /**
     * 注册验证码频率限制器
     *
     * @param stringRedisTemplate Redis 模板
     * @return 验证码频率限制器实例
     */
    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(CaptchaRateLimiter.class)
    public CaptchaRateLimiter captchaRateLimiter(StringRedisTemplate stringRedisTemplate) {
        log.info("注册验证码频率限制器");
        return new CaptchaRateLimiter(stringRedisTemplate);
    }
}
