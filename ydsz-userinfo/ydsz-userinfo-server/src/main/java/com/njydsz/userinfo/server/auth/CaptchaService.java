package com.njydsz.userinfo.server.auth;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;

import javax.imageio.ImageIO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.server.config.UserInfoProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 图形验证码服务。
 *
 * <p>生成 4 位字母数字混合验证码，存储到 Redis（TTL 可配置），
 * 提供 Base64 编码的 PNG 图片供前端展示。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final RedisStringOps redisStringOps;
    private final UserInfoProperties properties;

    private static final String CAPTCHA_KEY_PREFIX = "auth:captcha:";
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_LENGTH = 4;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private final SecureRandom random = new SecureRandom();

    /**
     * 生成验证码并返回 Base64 图片。
     *
     * @param captchaKey 客户端生成的唯一 key
     * @return Base64 编码的 PNG 图片
     */
    public String generateCaptcha(String captchaKey) {
        String code = generateCode();
        redisStringOps.set(CAPTCHA_KEY_PREFIX + captchaKey, code, properties.getCaptchaTtlSeconds());

        BufferedImage image = drawCaptcha(code);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            byte[] bytes = baos.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            log.error("Failed to generate captcha image", e);
            throw new BusinessException(UserInfoExceptionCode.CAPTCHA_INVALID,
                    "验证码生成失败");
        }
    }

    /**
     * 校验验证码。
     *
     * @param captchaKey  客户端 key
     * @param userInput   用户输入的验证码
     * @return true 校验通过
     * @throws BusinessException 校验失败
     */
    public boolean validate(String captchaKey, String userInput) {
        if (captchaKey == null || userInput == null || userInput.isBlank()) {
            throw new BusinessException(UserInfoExceptionCode.CAPTCHA_REQUIRED);
        }

        String storedCode = redisStringOps.get(CAPTCHA_KEY_PREFIX + captchaKey, String.class);
        if (storedCode == null) {
            throw new BusinessException(UserInfoExceptionCode.CAPTCHA_INVALID, new Object[]{"验证码已过期"});
        }

        redisStringOps.del(CAPTCHA_KEY_PREFIX + captchaKey);

        if (!storedCode.equalsIgnoreCase(userInput)) {
            throw new BusinessException(UserInfoExceptionCode.CAPTCHA_INVALID, new Object[]{"验证码错误"});
        }

        return true;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private BufferedImage drawCaptcha(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 字体回退：优先 SansSerif（跨平台），避免 Linux 无 Arial 导致渲染异常
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(random.nextInt(150), random.nextInt(150), random.nextInt(150)));
            g.drawString(String.valueOf(code.charAt(i)), 20 + i * 25, 30);
        }

        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g.setStroke(new BasicStroke(1.5f));
            int x1 = random.nextInt(WIDTH);
            int y1 = random.nextInt(HEIGHT);
            int x2 = random.nextInt(WIDTH);
            int y2 = random.nextInt(HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }

        g.dispose();
        return image;
    }
}
