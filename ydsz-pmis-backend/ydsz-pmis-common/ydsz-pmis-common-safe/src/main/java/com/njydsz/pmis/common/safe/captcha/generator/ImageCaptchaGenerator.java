package com.njydsz.pmis.common.safe.captcha.generator;

import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.exception.custom.YdszSecurityException;
import com.njydsz.pmis.common.safe.captcha.core.CaptchaGenerator;
import com.njydsz.pmis.common.safe.captcha.core.CaptchaResult;

/**
 * 图形验证码生成器。
 * 生成包含字母数字混合的验证码图片，支持多种安全增强措施。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>随机噪点干扰</li>
 *   <li>字符随机旋转扭曲</li>
 *   <li>背景随机曲线干扰</li>
 *   <li>安全随机数生成器</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class ImageCaptchaGenerator implements CaptchaGenerator {

    private static final Logger log = LoggerFactory.getLogger(ImageCaptchaGenerator.class);

    /**
     * 验证码长度
     */
    private final int length;

    /**
     * 图片宽度
     */
    private final int width;

    /**
     * 图片高度
     */
    private final int height;

    /**
     * 安全随机数生成器
     */
    private final SecureRandom random;

    /**
     * 验证码字符集(排除易混淆字符)
     */
    private static final String CHAR_SET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /**
     * 字体缓存，避免重复创建
     */
    private static final Font CAPTCHA_FONT = new Font("Arial", Font.BOLD, 28);

    /**
     * 构建图形验证码生成器
     *
     * @param length 验证码长度
     * @param width 图片宽度
     * @param height 图片高度
     */
    public ImageCaptchaGenerator(int length, int width, int height) {
        this.length = length;
        this.width = width;
        this.height = height;
        this.random = initSecureRandom();
    }

    /**
     * 构建默认图形验证码生成器(4位,120x40)
     */
    public ImageCaptchaGenerator() {
        this(4, 120, 40);
    }

    /**
     * 初始化安全随机数生成器。
     *
     * <p>优先使用 SHA1PRNG 算法，失败时回退到最强算法。
     *
     * @return SecureRandom 实例
     */
    private SecureRandom initSecureRandom() {
        try {
            return SecureRandom.getInstance("SHA1PRNG");
        } catch (Exception e) {
            try {
                return SecureRandom.getInstanceStrong();
            } catch (Exception ex) {
                log.warn("无法初始化安全随机数生成器，使用默认实现", ex);
                return new SecureRandom();
            }
        }
    }

    @Override
    public CaptchaResult generate() {
        String captchaCode = generateCaptchaCode();
        BufferedImage image = createImage(captchaCode);
        String imageBase64 = imageToBase64(image);

        CaptchaResult result = new CaptchaResult();
        result.setCaptchaCode(captchaCode);
        result.setImageBase64(imageBase64);
        result.setExpireTime(120L);

        log.debug("生成图形验证码: 长度={}, 尺寸={}x{}", length, width, height);
        return result;
    }

    @Override
    public String getType() {
        return "IMAGE";
    }

    /**
     * 生成随机验证码字符串
     *
     * @return 验证码字符串
     */
    private String generateCaptchaCode() {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHAR_SET.charAt(random.nextInt(CHAR_SET.length())));
        }
        return sb.toString();
    }

    /**
     * 创建验证码图片
     *
     * @param captchaCode 验证码字符串
     * @return BufferedImage 对象
     */
    private BufferedImage createImage(String captchaCode) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        drawInterferenceLines(g);

        drawNoisePoints(g);

        drawCaptchaCharacters(g, captchaCode);

        g.dispose();
        return image;
    }

    /**
     * 绘制背景干扰线。
     *
     * <p>绘制 3-5 条随机颜色的曲线，增加 OCR 识别难度。
     *
     * @param g Graphics2D 对象
     */
    private void drawInterferenceLines(Graphics2D g) {
        int lineCount = 3 + random.nextInt(3);
        for (int i = 0; i < lineCount; i++) {
            g.setColor(getRandomColor());
            int x1 = random.nextInt(width);
            int y1 = random.nextInt(height);
            int x2 = random.nextInt(width);
            int y2 = random.nextInt(height);
            int ctrlX = random.nextInt(width);
            int ctrlY = random.nextInt(height);

            if (random.nextBoolean()) {
                QuadCurve2D curve = new QuadCurve2D.Float(x1, y1, ctrlX, ctrlY, x2, y2);
                g.draw(curve);
            } else {
                int ctrlX2 = random.nextInt(width);
                int ctrlY2 = random.nextInt(height);
                CubicCurve2D curve = new CubicCurve2D.Float(x1, y1, ctrlX, ctrlY, ctrlX2, ctrlY2, x2, y2);
                g.draw(curve);
            }
        }
    }

    /**
     * 绘制噪点。
     *
     * <p>随机分布的像素点，颜色与背景相近，数量 = 宽度 * 高度 * 0.01。
     *
     * @param g Graphics2D 对象
     */
    private void drawNoisePoints(Graphics2D g) {
        int noiseCount = (int) (width * height * 0.01);
        for (int i = 0; i < noiseCount; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            Color noiseColor = new Color(
                    200 + random.nextInt(56),
                    200 + random.nextInt(56),
                    200 + random.nextInt(56)
            );
            g.setColor(noiseColor);
            g.fillRect(x, y, 1, 1);
        }
    }

    /**
     * 绘制验证码字符。
     *
     * <p>每个字符进行随机旋转（-15° 到 +15°）和随机偏移。
     *
     * @param g           Graphics2D 对象
     * @param captchaCode 验证码字符串
     */
    private void drawCaptchaCharacters(Graphics2D g, String captchaCode) {
        Font font = CAPTCHA_FONT.deriveFont((float) (height - 5));
        g.setFont(font);

        int charWidth = (width - 30) / length;

        for (int i = 0; i < captchaCode.length(); i++) {
            g.setColor(getRandomColor());

            int x = 15 + i * charWidth;
            int y = height / 2 + 5;

            double angle = (random.nextDouble() - 0.5) * 30 * Math.PI / 180;
            int offsetX = random.nextInt(3) - 1;
            int offsetY = random.nextInt(3) - 1;

            g.rotate(angle, x + charWidth / 2, y);
            g.drawString(String.valueOf(captchaCode.charAt(i)), x + offsetX, y + offsetY);
            g.rotate(-angle, x + charWidth / 2, y);
        }
    }

    /**
     * 获取随机颜色
     *
     * @return Color 对象
     */
    private Color getRandomColor() {
        return new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200));
    }

    /**
     * 将图片转换为 Base64 字符串
     *
     * @param image BufferedImage 对象
     * @return Base64 编码的图片字符串
     */
    private String imageToBase64(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new YdszSecurityException("图片转换失败", e);
        }
    }
}
