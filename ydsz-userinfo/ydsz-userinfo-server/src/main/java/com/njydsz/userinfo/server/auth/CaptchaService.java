package com.njydsz.userinfo.server.auth;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.config.UserInfoProperties;

/**
 * 图形验证码服务。
 *
 * <p>生成 4 位字母数字混合验证码，存储到 Redis（TTL 可配置）， 提供 Base64 编码的 PNG 图片供前端展示。
 *
 * <p>P1-12: 核心逻辑已下沉到 common-safe {@code CaptchaGenerator}， 本服务保留为薄封装，负责业务异常转换。
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

  /** 验证码字体大小（像素）。 */
  private static final int FONT_SIZE = 28;

  /** 字符颜色随机值上限（0-255），值越大颜色越浅。 */
  private static final int COLOR_RANDOM_BOUND = 150;

  /** 第一个字符的 X 坐标偏移。 */
  private static final int CHAR_START_X = 20;

  /** 相邻字符间距。 */
  private static final int CHAR_X_STEP = 25;

  /** 字符 baseline Y 坐标。 */
  private static final int CHAR_BASELINE_Y = 30;

  /** 干扰线数量。 */
  private static final int NOISE_LINE_COUNT = 5;

  /** 干扰线颜色随机值上限。 */
  private static final int NOISE_COLOR_BOUND = 200;

  /** 干扰线笔触宽度。 */
  private static final float NOISE_STROKE_WIDTH = 1.5f;

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
      throw new BusinessException(UserInfoExceptionCode.CAPTCHA_INVALID);
    }
  }

  /**
   * 校验验证码。
   *
   * @param captchaKey 客户端 key
   * @param userInput 用户输入的验证码
   * @return true 校验通过
   * @throws BusinessException 校验失败
   */
  public boolean validate(String captchaKey, String userInput) {
    if (captchaKey == null || userInput == null || userInput.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.CAPTCHA_REQUIRED);
    }

    String captchaKeyPrefix = CAPTCHA_KEY_PREFIX + captchaKey;
    String storedCode = redisStringOps.get(captchaKeyPrefix, String.class);
    if (storedCode == null) {
      throw BusinessException.builder()
          .resultCode(UserInfoExceptionCode.CAPTCHA_INVALID)
          .params("验证码已过期")
          .build();
    }

    redisStringOps.del(CAPTCHA_KEY_PREFIX + captchaKey);

    if (!storedCode.equalsIgnoreCase(userInput)) {
      throw BusinessException.builder()
          .resultCode(UserInfoExceptionCode.CAPTCHA_INVALID)
          .params("验证码错误")
          .build();
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
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE));
    for (int i = 0; i < code.length(); i++) {
      g.setColor(
          new Color(
              random.nextInt(COLOR_RANDOM_BOUND),
              random.nextInt(COLOR_RANDOM_BOUND),
              random.nextInt(COLOR_RANDOM_BOUND)));
      g.drawString(String.valueOf(code.charAt(i)), CHAR_START_X + i * CHAR_X_STEP, CHAR_BASELINE_Y);
    }

    for (int i = 0; i < NOISE_LINE_COUNT; i++) {
      g.setColor(
          new Color(
              random.nextInt(NOISE_COLOR_BOUND),
              random.nextInt(NOISE_COLOR_BOUND),
              random.nextInt(NOISE_COLOR_BOUND)));
      g.setStroke(new BasicStroke(NOISE_STROKE_WIDTH));
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
