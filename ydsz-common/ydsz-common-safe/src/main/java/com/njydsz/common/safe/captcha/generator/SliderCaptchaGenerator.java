package com.njydsz.common.safe.captcha.generator;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;

import com.njydsz.common.safe.captcha.core.CaptchaGenerator;
import com.njydsz.common.safe.captcha.core.CaptchaResult;
import java.util.UUID;

/**
 * 滑块验证码生成器。
 *
 * <p>生成背景图和带缺口的滑块图，前端用户拖动滑块到缺口位置完成验证。
 * 支持随机缺口位置、干扰线、噪点等安全增强。
 *
 * <h3>验证流程</h3>
 * <ol>
 *   <li>后端生成随机缺口位置的背景图 + 对应滑块图，返回 Base64 编码图片</li>
 *   <li>前端展示背景图，用户拖动滑块到缺口位置</li>
 *   <li>前端将用户拖动的最终 X 坐标提交到后端</li>
 *   <li>后端校验 X 坐标与实际缺口位置的距离差是否在容差范围内</li>
 * </ol>
 *
 * <h3>安全增强</h3>
 * <ul>
 *   <li>随机缺口位置（X/Y 轴双重随机）</li>
 *   <li>50 个随机噪点干扰图像识别</li>
 *   <li>5 像素容差范围，平衡安全性与用户体验</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CaptchaGenerator
 * @see CaptchaResult
 */
public class SliderCaptchaGenerator implements CaptchaGenerator {

    /** 背景图宽度（像素） */
    private static final int BG_WIDTH = 300;
    /** 背景图高度（像素） */
    private static final int BG_HEIGHT = 150;
    /** 滑块直径（像素），必须小于背景图宽度的一半 */
    private static final int SLIDER_SIZE = 44;
    /** 拖动容差（像素），用户拖动位置与实际缺口位置的距离差在 ±5 像素内视为通过 */
    private static final int TOLERANCE = 5;

    /** 随机数生成器，用于缺口位置和噪点分布 */
    private final Random random = new Random();

    /**
     * 生成滑块验证码。
     *
     * <p>随机确定缺口位置后，分别绘制背景图（含缺口）和滑块图，
     * 将两张图以 Base64 编码返回。验证码答案为缺口的 X 坐标值。
     *
     * @return 包含背景图 Base64、滑块图 Base64 和答案（缺口 X 坐标）的验证码结果
     */
    @Override
    public CaptchaResult generate() {
        int sliderX = SLIDER_SIZE + random.nextInt(BG_WIDTH - SLIDER_SIZE * 3);
        int sliderY = random.nextInt(BG_HEIGHT - SLIDER_SIZE - 10) + 5;

        BufferedImage bgImage = createBackgroundImage(sliderX, sliderY);
        BufferedImage sliderImage = createSliderImage();

        String bgBase64 = imageToBase64(bgImage);
        String sliderBase64 = imageToBase64(sliderImage);

        String captchaId = UUID.randomUUID().toString();

        CaptchaResult result = new CaptchaResult(captchaId, String.valueOf(sliderX));
        result.setBgImageBase64(bgBase64);
        result.setImageBase64(sliderBase64);

        return result;
    }

    /**
     * 获取验证码类型标识。
     *
     * @return 固定返回 {@code "slider"}
     */
    @Override
    public String getType() {
        return "slider";
    }

    /**
     * 创建背景图（含缺口）。
     *
     * <p>绘制流程：
     * <ol>
     *   <li>填充随机浅色背景</li>
     *   <li>绘制 50 个半透明噪点干扰图像识别</li>
     *   <li>在指定位置绘制半透明黑色圆形缺口</li>
     * </ol>
     *
     * @param sliderX 缺口左上角 X 坐标
     * @param sliderY 缺口左上角 Y 坐标
     * @return 绘制完成的背景图
     */
    private BufferedImage createBackgroundImage(int sliderX, int sliderY) {
        BufferedImage image = new BufferedImage(BG_WIDTH, BG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(random.nextInt(200) + 50, random.nextInt(200) + 50, random.nextInt(200) + 50));
        g.fillRect(0, 0, BG_WIDTH, BG_HEIGHT);

        for (int i = 0; i < 50; i++) {
            int x = random.nextInt(BG_WIDTH);
            int y = random.nextInt(BG_HEIGHT);
            g.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256), 100));
            g.fillOval(x, y, random.nextInt(5) + 2, random.nextInt(5) + 2);
        }

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(sliderX, sliderY, SLIDER_SIZE, SLIDER_SIZE);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillOval(sliderX, sliderY, SLIDER_SIZE, SLIDER_SIZE);

        g.dispose();
        return image;
    }

    /**
     * 创建滑块图。
     *
     * <p>绘制一个半透明白色填充 + 蓝色边框的圆形滑块，
     * 尺寸与背景图中的缺口一致。
     *
     * @return 绘制完成的滑块图（透明背景）
     */
    private BufferedImage createSliderImage() {
        BufferedImage image = new BufferedImage(SLIDER_SIZE, SLIDER_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(255, 255, 255, 200));
        g.fillOval(0, 0, SLIDER_SIZE, SLIDER_SIZE);

        g.setColor(Color.BLUE);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(0, 0, SLIDER_SIZE, SLIDER_SIZE);

        g.dispose();
        return image;
    }

    /**
     * 将图片编码为 Base64 Data URL。
     *
     * @param image 待编码的图片
     * @return Base64 编码的 Data URL（格式：{@code data:image/png;base64,...}）
     */
    private String imageToBase64(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return "";
        }
    }
}
