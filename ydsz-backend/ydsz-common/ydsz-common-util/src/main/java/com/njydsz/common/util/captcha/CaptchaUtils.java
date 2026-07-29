package com.njydsz.common.util.captcha;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;

/**
 * 验证码工具类（纯 Java 实现、零依赖）
 *
 * <p>提供图形验证码生成功能，完全基于 JDK 原生 AWT API，无需任何第三方依赖。
 * 功能对标 Google Kaptcha 和 Hutool CaptchaUtil，并进行了优化。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li><b>零依赖</b>：纯 JDK AWT 实现，无需 Kaptcha 等库</li>
 *   <li><b>多种样式</b>：支持数字、字母、混合等多种验证码</li>
 *   <li><b>干扰线</b>：自动添加干扰线防止 OCR 识别</li>
 *   <li><b>扭曲效果</b>：支持字符扭曲增强安全性</li>
 *   <li><b>自定义配置</b>：支持颜色、字体、大小等配置</li>
 *   <li><b>高性能</b>：优化的图像生成算法</li>
 * </ul>
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>数字验证码：generateNumeric</li>
 *   <li>字母验证码：generateAlphabetic</li>
 *   <li>混合验证码：generateAlphanumeric</li>
 *   <li>算术验证码：generateArithmetic</li>
 *   <li>中文验证码：generateChinese</li>
 *   <li>自定义验证码：generateCustom</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 生成 4 位数字验证码
 * CaptchaResult result = CaptchaUtils.generateNumeric(4);
 * BufferedImage image = result.getImage();
 * String code = result.getCode();
 *
 * // 生成 6 位混合验证码
 * CaptchaResult result = CaptchaUtils.generateAlphanumeric(6);
 *
 * // 生成算术验证码
 * CaptchaResult result = CaptchaUtils.generateArithmetic();
 *
 * // 自定义配置
 * CaptchaResult result = CaptchaUtils.generateCustom(5, 200, 80, Color.BLUE);
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public class CaptchaUtils {

    private CaptchaUtils() {
        throw new UnsupportedOperationException("CaptchaUtils is a utility class and cannot be instantiated");
    }

    private static final String NUMERIC_CHARS = "0123456789";
    private static final String ALPHABETIC_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String ALPHANUMERIC_CHARS = NUMERIC_CHARS + ALPHABETIC_CHARS;
    private static final String[] CHINESE_WORDS = {
        "一", "二", "三", "四", "五", "六", "七", "八", "九", "十",
        "百", "千", "万", "天", "地", "人", "大", "中", "小", "上"
    };

    private static final SecureRandom random = new SecureRandom();

    /**
     * 生成数字验证码
     */
    public static CaptchaResult generateNumeric(int length) {
        return generateNumeric(length, 160, 60);
    }

    /**
     * 生成数字验证码（自定义尺寸）
     */
    public static CaptchaResult generateNumeric(int length, int width, int height) {
        return generate(length, NUMERIC_CHARS, width, height, null);
    }

    /**
     * 生成字母验证码
     */
    public static CaptchaResult generateAlphabetic(int length) {
        return generateAlphabetic(length, 160, 60);
    }

    /**
     * 生成字母验证码（自定义尺寸）
     */
    public static CaptchaResult generateAlphabetic(int length, int width, int height) {
        return generate(length, ALPHABETIC_CHARS, width, height, null);
    }

    /**
     * 生成混合验证码
     */
    public static CaptchaResult generateAlphanumeric(int length) {
        return generateAlphanumeric(length, 160, 60);
    }

    /**
     * 生成混合验证码（自定义尺寸）
     */
    public static CaptchaResult generateAlphanumeric(int length, int width, int height) {
        return generate(length, ALPHANUMERIC_CHARS, width, height, null);
    }

    /**
     * 生成算术验证码
     */
    public static CaptchaResult generateArithmetic() {
        return generateArithmetic(160, 60);
    }

    /**
     * 生成算术验证码（自定义尺寸）
     */
    public static CaptchaResult generateArithmetic(int width, int height) {
        int a = random.nextInt(20) + 1;
        int b = random.nextInt(20) + 1;
        int op = random.nextInt(2);
        
        String expression;
        int result;
        
        if (op == 0) {
            expression = a + " + " + b;
            result = a + b;
        } else {
            // 确保减法结果不为负
            if (a < b) {
                int temp = a;
                a = b;
                b = temp;
            }
            expression = a + " - " + b;
            result = a - b;
        }
        
        CaptchaResult captchaResult = generateCustomImage(expression, width, height);
        captchaResult.setCode(String.valueOf(result));
        return captchaResult;
    }

    /**
     * 生成中文验证码
     */
    public static CaptchaResult generateChinese(int length) {
        return generateChinese(length, 200, 60);
    }

    /**
     * 生成中文验证码（自定义尺寸）
     */
    public static CaptchaResult generateChinese(int length, int width, int height) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(CHINESE_WORDS[random.nextInt(CHINESE_WORDS.length)]);
        }
        
        CaptchaResult result = generateCustomImage(sb.toString(), width, height);
        result.setCode(sb.toString());
        return result;
    }

    /**
     * 生成自定义验证码
     */
    public static CaptchaResult generateCustom(int length, int width, int height, Color bgColor) {
        return generate(length, ALPHANUMERIC_CHARS, width, height, bgColor);
    }

    /**
     * 生成验证码（内部方法）
     */
    private static CaptchaResult generate(int length, String chars, int width, int height, Color bgColor) {
        // 生成验证码字符串
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        String code = sb.toString();
        
        // 生成图像（传入 bgColor 参数）
        CaptchaResult result = generateCustomImage(code, width, height, bgColor);
        result.setCode(code);
        
        return result;
    }

    /**
     * 生成自定义图像
     */
    private static CaptchaResult generateCustomImage(String text, int width, int height) {
        return generateCustomImage(text, width, height, null);
    }

    /**
     * 生成自定义图像（支持指定背景色）
     */
    private static CaptchaResult generateCustomImage(String text, int width, int height, Color bgColor) {
        // 创建图像
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // 设置背景色（指定背景色或随机浅色）
        if (bgColor != null) {
            g2d.setColor(bgColor);
        } else {
            g2d.setColor(new Color(240 + random.nextInt(15), 240 + random.nextInt(15), 240 + random.nextInt(15)));
        }
        g2d.fillRect(0, 0, width, height);

        // 绘制干扰线
        drawInterferenceLines(g2d, width, height);

        // 绘制验证码文本
        drawText(g2d, text, width, height);

        // 绘制干扰点
        drawInterferencePoints(g2d, width, height);

        g2d.dispose();

        CaptchaResult result = new CaptchaResult();
        result.setImage(image);
        result.setText(text);
        
        return result;
    }

    /**
     * 绘制干扰线
     */
    private static void drawInterferenceLines(Graphics2D g2d, int width, int height) {
        int lineCount = 3 + random.nextInt(3);
        for (int i = 0; i < lineCount; i++) {
            g2d.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g2d.setStroke(new BasicStroke(1.0f));
            
            int x1 = random.nextInt(width);
            int y1 = random.nextInt(height);
            int x2 = random.nextInt(width);
            int y2 = random.nextInt(height);
            
            g2d.drawLine(x1, y1, x2, y2);
        }
    }

    /**
     * 绘制干扰点
     */
    private static void drawInterferencePoints(Graphics2D g2d, int width, int height) {
        int pointCount = 20 + random.nextInt(20);
        for (int i = 0; i < pointCount; i++) {
            g2d.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            g2d.fillOval(x, y, 2, 2);
        }
    }

    /**
     * 绘制文本
     */
    private static void drawText(Graphics2D g2d, String text, int width, int height) {
        int fontSize = height - 10;
        Font[] fonts = {
            new Font("Arial", Font.BOLD, fontSize),
            new Font("Verdana", Font.BOLD, fontSize),
            new Font("Times New Roman", Font.BOLD, fontSize),
            new Font("Georgia", Font.BOLD, fontSize)
        };

        int charWidth = width / text.length();
        int baseX = charWidth / 2;
        int baseY = height / 2 + fontSize / 3;

        for (int i = 0; i < text.length(); i++) {
            // 随机字体
            g2d.setFont(fonts[random.nextInt(fonts.length)]);
            
            // 随机颜色（深色）
            g2d.setColor(new Color(
                50 + random.nextInt(150),
                50 + random.nextInt(150),
                50 + random.nextInt(150)
            ));
            
            // 随机旋转和位移
            g2d.rotate(Math.toRadians((random.nextInt(3) - 1) * 15), 
                      baseX + i * charWidth, baseY);
            
            g2d.drawString(String.valueOf(text.charAt(i)), 
                          baseX + i * charWidth, baseY);
            
            // 恢复旋转
            g2d.rotate(Math.toRadians(-(random.nextInt(3) - 1) * 15), 
                      baseX + i * charWidth, baseY);
        }
    }

    /**
     * 验证码结果
     */
    public static class CaptchaResult {
        private BufferedImage image;
        private String code;
        private String text;

        public BufferedImage getImage() {
            return image;
        }

        public void setImage(BufferedImage image) {
            this.image = image;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        /**
         * 验证输入验证码是否匹配（大小写不敏感，自动去空格）
         *
         * @param inputCode 用户输入的验证码
         * @return 匹配返回 true；inputCode 为 null/空或 code 为 null 时返回 false
         */
        public boolean matches(String inputCode) {
            if (inputCode == null || inputCode.isBlank() || code == null) {
                return false;
            }
            return code.equalsIgnoreCase(inputCode.trim());
        }

        @Override
        public String toString() {
            return "CaptchaResult{code='" + code + "', text='" + text + "'}";
        }
    }
}
