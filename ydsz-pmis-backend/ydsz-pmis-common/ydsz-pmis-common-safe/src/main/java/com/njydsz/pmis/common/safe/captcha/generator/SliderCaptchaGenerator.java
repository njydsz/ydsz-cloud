package com.njydsz.pmis.common.safe.captcha.generator;

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

import com.njydsz.pmis.common.safe.captcha.core.CaptchaGenerator;
import com.njydsz.pmis.common.safe.captcha.core.CaptchaResult;
import java.util.UUID;

/**
 * 滑块验证码生成器
 *
 * <p>生成背景图和带缺口的滑块图，前端用户拖动滑块到缺口位置完成验证。
 * 支持随机缺口位置、干扰线、噪点等安全增强。
 *
 * @since 1.0.0
 */
public class SliderCaptchaGenerator implements CaptchaGenerator {

    private static final int BG_WIDTH = 300;
    private static final int BG_HEIGHT = 150;
    private static final int SLIDER_SIZE = 44;
    private static final int TOLERANCE = 5;

    private final Random random = new Random();

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

    @Override
    public String getType() {
        return "slider";
    }

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

    private String imageToBase64(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return "";
        }
    }
}
