package com.njydsz.common.safe.captcha;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 图形验证码生成器（P1-12：从 userinfo CaptchaService 提取，下沉到 common-safe）。
 *
 * <p>提供验证码生成、图片绘制、Redis 存储、校验等能力。 业务模块（如 userinfo）注入此 Bean 即可使用验证码功能，无需各自实现。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 生成验证码（返回 Base64 图片）
 * String base64Image = captchaGenerator.generate(captchaKey);
 *
 * * // 校验验证码
 * boolean valid = captchaGenerator.validate(captchaKey, userInput);
 * }</pre>
 *
 * <h3>与 userinfo CaptchaService 的关系</h3>
 *
 * <p>userinfo 模块的 {@code CaptchaService} 现为此类的薄封装， * 业务特有配置通过 {@link CaptchaProperties} 传递。
 *
 * @since 1.0.0
 * @see CaptchaProperties
 */
@Slf4j
@RequiredArgsConstructor
public class CaptchaGenerator {

  private final RedisStringOps redisStringOps;
  private final CaptchaProperties properties;

  private final SecureRandom random = new SecureRandom();

  /**
   * 生成验证码并存储到 Redis，返回 Base64 编码的 PNG 图片。
   *
   * @param captchaKey 客户端生成的唯一 key（如 UUID）
   * @return Base64 编码的 PNG 图片（data:image/png;base64,... 格式）
   * @throws IllegalArgumentException captchaKey 为 null 或空
   */
  public String generate(String captchaKey) {
    if (captchaKey == null || captchaKey.isBlank()) {
      throw new IllegalArgumentException("captchaKey must not be null or empty");
    }

    String code = generateCode(properties.getCodeLength());
    redisStringOps.set(properties.getKeyPrefix() + captchaKey, code, properties.getTtlSeconds());

    BufferedImage image = drawCaptcha(code);
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      ImageIO.write(image, "PNG", baos);
      byte[] bytes = baos.toByteArray();
      return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    } catch (IOException e) {
      log.error("Failed to generate captcha image, key={}", captchaKey, e);
      throw new IllegalStateException("验证码图片生成失败", e);
    }
  }

  /**
   * 校验验证码（校验后无论成功与否均删除 Redis 记录，一次性使用）。
   *
   * @param captchaKey 客户端 key
   * @param userInput 用户输入的验证码
   * @return true 校验通过；false 验证码错误或已过期
   */
  public boolean validate(String captchaKey, @Nullable String userInput) {
    if (captchaKey == null || captchaKey.isBlank() || userInput == null || userInput.isBlank()) {
      return false;
    }

    String storedCode = redisStringOps.get(properties.getKeyPrefix() + captchaKey, String.class);
    // 无论校验结果如何，删除 Redis 记录（一次性使用）
    redisStringOps.del(properties.getKeyPrefix() + captchaKey);

    if (storedCode == null) {
      return false;
    }

    return storedCode.equalsIgnoreCase(userInput.trim());
  }

  /**
   * 生成随机验证码字符串。
   *
   * @param length 验证码长度
   * @return 随机验证码
   */
  private String generateCode(int length) {
    String chars = properties.getChars();
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(chars.charAt(random.nextInt(chars.length())));
    }
    return sb.toString();
  }

  /**
   * 绘制验证码图片（含干扰线）。
   *
   * @param code 验证码字符串
   * @return BufferedImage 图片对象
   */
  private BufferedImage drawCaptcha(String code) {
    int width = properties.getImageWidth();
    int height = properties.getImageHeight();
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();

    // 白色背景
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, width, height);

    // 绘制验证码字符
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
    for (int i = 0; i < code.length(); i++) {
      g.setColor(new Color(random.nextInt(150), random.nextInt(150), random.nextInt(150)));
      g.drawString(String.valueOf(code.charAt(i)), 20 + i * 25, 30);
    }

    // 绘制干扰线（5 条）
    for (int i = 0; i < 5; i++) {
      g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
      g.setStroke(new BasicStroke(1.5f));
      int x1 = random.nextInt(width);
      int y1 = random.nextInt(height);
      int x2 = random.nextInt(width);
      int y2 = random.nextInt(height);
      g.drawLine(x1, y1, x2, y2);
    }

    g.dispose();
    return image;
  }
}
