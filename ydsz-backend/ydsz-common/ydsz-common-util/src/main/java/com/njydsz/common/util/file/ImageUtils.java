package com.njydsz.common.util.file;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.IIOImage;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.njydsz.common.util.concurrent.ExecutorUtils;
import com.njydsz.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * ImageUtils - 增强版图片处理工具类
 * 参考：Apache Commons Imaging, Thumbnailator, imgscalr
 * 
 * @author ydsz-team
 * @since 1.0.0
 * 
 *
 */
@Slf4j
public class ImageUtils {

    /**
     * 图片处理线程池（4 线程，固定大小，命名 image-）。
     *
     * <p>类加载时注册 JVM ShutdownHook 自动关闭，避免应用关闭时线程泄漏；
     * 业务也可显式调用 {@link #shutdown()} 提前关闭。
     */
    private static final ExecutorService EXECUTOR_SERVICE = ExecutorUtils.newFixedThreadPool(4, "image-");

    static {
        // JVM 关闭时自动优雅关闭线程池，避免遗忘 shutdown 导致 JVM 挂起
        Runtime.getRuntime().addShutdownHook(new Thread(ImageUtils::shutdown, "image-utils-shutdown-hook"));
    }

    private static final int DEFAULT_CONNECT_TIMEOUT = 10000;
    private static final int DEFAULT_READ_TIMEOUT = 30000;
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * 关闭线程池
     */
    public static void shutdown() {
        EXECUTOR_SERVICE.shutdown();
        try {
            if (!EXECUTOR_SERVICE.awaitTermination(60, TimeUnit.SECONDS)) {
                EXECUTOR_SERVICE.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR_SERVICE.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取图片的字节数组 (支持 URL 和本地路径)
     */
    public static byte[] getImage(String imagePath) {
        if (imagePath == null) {
            return null;
        }

        if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            return readRemoteFile(imagePath);
        } else {
            return readLocalFile(imagePath);
        }
    }

    /**
     * 读取远程文件 (增强版 - 使用 NIO)
     */
    public static byte[] readRemoteFile(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        
        try {
            URI uri = URI.create(url);
            URLConnection urlConnection = uri.toURL().openConnection();
            urlConnection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
            urlConnection.setReadTimeout(DEFAULT_READ_TIMEOUT);
            urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            try (InputStream in = urlConnection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                return out.toByteArray();
            }
        } catch (Exception e) {
            log.error("ImageUtils -> 远程访问文件异常 {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 读取远程文件 (异步版本)
     */
    public static CompletableFuture<byte[]> readRemoteFileAsync(String url) {
        return CompletableFuture.supplyAsync(() -> readRemoteFile(url), EXECUTOR_SERVICE);
    }

    /**
     * 读取本地文件 (高性能版本)
     */
    public static byte[] readLocalFile(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            log.error("ImageUtils -> 读取本地文件异常 {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 读取本地文件 (使用 FileChannel - 更高性能)
     */
    public static byte[] readLocalFileFast(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            log.error("ImageUtils -> 文件不存在：{}", path);
            return null;
        }
        
        try (FileInputStream fis = new FileInputStream(path);
             ReadableByteChannel channel = Channels.newChannel(fis);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            ByteBuffer buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
            while (channel.read(buffer) != -1) {
                buffer.flip();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                baos.write(data);
                buffer.clear();
            }
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("ImageUtils -> 读取本地文件异常 {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 从 URL 加载 BufferedImage
     */
    public static BufferedImage loadBufferedImage(String imageUrl) {
        if (StringUtils.isBlank(imageUrl)) {
            return null;
        }
        
        try {
            byte[] imageData = getImage(imageUrl);
            if (imageData == null) {
                return null;
            }
            
            try (ByteArrayInputStream bais = new ByteArrayInputStream(imageData)) {
                return ImageIO.read(bais);
            }
        } catch (Exception e) {
            log.error("ImageUtils -> 加载 BufferedImage 异常 {}: {}", imageUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 从本地文件加载 BufferedImage
     */
    public static BufferedImage loadBufferedImageFromFile(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return null;
        }
        
        try {
            return ImageIO.read(new File(filePath));
        } catch (Exception e) {
            log.error("ImageUtils -> 加载 BufferedImage 异常 {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    /**
     * 保存图片到文件
     */
    public static boolean saveImage(BufferedImage image, String filePath, String format) {
        if (image == null || StringUtils.isBlank(filePath) || StringUtils.isBlank(format)) {
            return false;
        }
        
        try {
            File outputFile = new File(filePath);
            FileUtils.mkdirsForFile(filePath);
            return ImageIO.write(image, format, outputFile);
        } catch (Exception e) {
            log.error("ImageUtils -> 保存图片异常 {}: {}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * 保存图片为 JPG 格式
     */
    public static boolean saveAsJpeg(BufferedImage image, String filePath) {
        return saveImage(image, filePath, "jpg");
    }

    /**
     * 保存图片为 PNG 格式
     */
    public static boolean saveAsPng(BufferedImage image, String filePath) {
        return saveImage(image, filePath, "png");
    }

    /**
     * 保存图片为 GIF 格式
     */
    public static boolean saveAsGif(BufferedImage image, String filePath) {
        return saveImage(image, filePath, "gif");
    }

    /**
     * 缩放图片 (指定目标尺寸)
     *
     * <p>根据源图是否含 alpha 通道自动选择 {@code TYPE_INT_ARGB} 或 {@code TYPE_INT_RGB}，
     * 避免 PNG 透明背景变黑。
     */
    public static BufferedImage scaleImage(BufferedImage source, int targetWidth, int targetHeight) {
        if (source == null || targetWidth <= 0 || targetHeight <= 0) {
            return null;
        }

        Image scaledImage = source.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
        int imageType = source.getTransparency() != BufferedImage.OPAQUE
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D g2d = null;
        try {
            g2d = result.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.drawImage(scaledImage, 0, 0, null);
            return result;
        } catch (Exception e) {
            log.error("ImageUtils -> 缩放图片异常：{}", e.getMessage());
            return null;
        } finally {
            if (g2d != null) {
                g2d.dispose();
            }
        }
    }

    /**
     * 缩放图片 (按比例)
     */
    public static BufferedImage scaleImageByRatio(BufferedImage source, double ratio) {
        if (source == null || ratio <= 0) {
            return null;
        }
        
        int targetWidth = (int) (source.getWidth() * ratio);
        int targetHeight = (int) (source.getHeight() * ratio);
        return scaleImage(source, targetWidth, targetHeight);
    }

    /**
     * 缩放图片 (保持宽高比，限制最大尺寸)
     */
    public static BufferedImage scaleImageToFit(BufferedImage source, int maxWidth, int maxHeight) {
        if (source == null || maxWidth <= 0 || maxHeight <= 0) {
            return null;
        }
        
        int originalWidth = source.getWidth();
        int originalHeight = source.getHeight();
        
        if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
            return source;
        }
        
        double widthRatio = (double) maxWidth / originalWidth;
        double heightRatio = (double) maxHeight / originalHeight;
        double ratio = Math.min(widthRatio, heightRatio);
        
        return scaleImageByRatio(source, ratio);
    }

    /**
     * 裁剪图片 (中心裁剪)
     */
    public static BufferedImage cropCenter(BufferedImage source, int width, int height) {
        if (source == null || width <= 0 || height <= 0) {
            return null;
        }
        
        int x = (source.getWidth() - width) / 2;
        int y = (source.getHeight() - height) / 2;
        
        return cropImage(source, Math.max(0, x), Math.max(0, y), 
                        Math.min(width, source.getWidth()), Math.min(height, source.getHeight()));
    }

    /**
     * 裁剪图片 (指定区域)
     */
    public static BufferedImage cropImage(BufferedImage source, int x, int y, int width, int height) {
        if (source == null || width <= 0 || height <= 0) {
            return null;
        }
        
        try {
            return source.getSubimage(x, y, width, height);
        } catch (Exception e) {
            log.error("ImageUtils -> 裁剪图片异常：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 旋转图片
     */
    public static BufferedImage rotateImage(BufferedImage source, double angle) {
        if (source == null) {
            return null;
        }

        BufferedImage result = null;
        Graphics2D g2d = null;
        try {
            double radians = Math.toRadians(angle);
            int width = source.getWidth();
            int height = source.getHeight();

            AffineTransform transform = new AffineTransform();
            transform.rotate(radians, width / 2.0, height / 2.0);

            result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            g2d = result.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.transform(transform);
            g2d.drawImage(source, 0, 0, null);

            return result;
        } catch (Exception e) {
            log.error("ImageUtils -> 旋转图片异常：{}", e.getMessage());
            return null;
        } finally {
            if (g2d != null) {
                g2d.dispose();
            }
        }
    }

    /**
     * 旋转图片 90 度 (顺时针)
     */
    public static BufferedImage rotate90(BufferedImage source) {
        if (source == null) {
            return null;
        }

        BufferedImage result = null;
        Graphics2D g2d = null;
        try {
            int width = source.getWidth();
            int height = source.getHeight();

            int imageType = source.getTransparency() != BufferedImage.OPAQUE
                    ? BufferedImage.TYPE_INT_ARGB
                    : source.getType();
            result = new BufferedImage(height, width, imageType);
            g2d = result.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            AffineTransform transform = new AffineTransform();
            transform.translate(height, 0);
            transform.rotate(Math.toRadians(90));

            g2d.transform(transform);
            g2d.drawImage(source, 0, 0, null);

            return result;
        } catch (Exception e) {
            log.error("ImageUtils -> 旋转图片 90 度异常：{}", e.getMessage());
            return null;
        } finally {
            if (g2d != null) {
                g2d.dispose();
            }
        }
    }

    /**
     * 添加文字水印
     */
    public static BufferedImage addTextWatermark(BufferedImage source, String text, Color color,
                                                 float fontSize, float alpha, int position) {
        if (source == null || StringUtils.isBlank(text)) {
            return null;
        }

        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = null;
        try {
            g2d = result.createGraphics();

            g2d.drawImage(source, 0, 0, null);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alpha));
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(color);
            g2d.setFont(new Font("Arial", Font.BOLD, (int) fontSize));

            FontMetrics metrics = g2d.getFontMetrics();
            int textWidth = metrics.stringWidth(text);
            int textHeight = metrics.getHeight();

            int x, y;
            switch (position) {
                case 1: // 左上角
                    x = 10;
                    y = textHeight + 5;
                    break;
                case 2: // 顶部居中
                    x = (source.getWidth() - textWidth) / 2;
                    y = textHeight + 5;
                    break;
                case 3: // 右上角
                    x = source.getWidth() - textWidth - 10;
                    y = textHeight + 5;
                    break;
                case 4: // 左侧居中
                    x = 10;
                    y = (source.getHeight() + textHeight) / 2;
                    break;
                case 5: // 正中间
                    x = (source.getWidth() - textWidth) / 2;
                    y = (source.getHeight() + textHeight) / 2;
                    break;
                case 6: // 右侧居中
                    x = source.getWidth() - textWidth - 10;
                    y = (source.getHeight() + textHeight) / 2;
                    break;
                case 7: // 左下角
                    x = 10;
                    y = source.getHeight() - 10;
                    break;
                case 8: // 底部居中
                    x = (source.getWidth() - textWidth) / 2;
                    y = source.getHeight() - 10;
                    break;
                case 9: // 右下角 (默认)
                default:
                    x = source.getWidth() - textWidth - 10;
                    y = source.getHeight() - 10;
                    break;
            }

            g2d.drawString(text, x, y);
            return result;
        } catch (Exception e) {
            log.error("ImageUtils -> 添加文字水印异常：{}", e.getMessage());
            return null;
        } finally {
            if (g2d != null) {
                g2d.dispose();
            }
        }
    }

    /**
     * 添加图片水印
     */
    public static BufferedImage addImageWatermark(BufferedImage source, BufferedImage watermark,
                                                  int position, float alpha) {
        if (source == null || watermark == null) {
            return null;
        }

        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = null;
        try {
            g2d = result.createGraphics();

            g2d.drawImage(source, 0, 0, null);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alpha));

            int watermarkWidth = watermark.getWidth();
            int watermarkHeight = watermark.getHeight();

            int x, y;
            switch (position) {
                case 1: // 左上角
                    x = 10;
                    y = 10;
                    break;
                case 2: // 顶部居中
                    x = (source.getWidth() - watermarkWidth) / 2;
                    y = 10;
                    break;
                case 3: // 右上角
                    x = source.getWidth() - watermarkWidth - 10;
                    y = 10;
                    break;
                case 4: // 左侧居中
                    x = 10;
                    y = (source.getHeight() - watermarkHeight) / 2;
                    break;
                case 5: // 正中间
                    x = (source.getWidth() - watermarkWidth) / 2;
                    y = (source.getHeight() - watermarkHeight) / 2;
                    break;
                case 6: // 右侧居中
                    x = source.getWidth() - watermarkWidth - 10;
                    y = (source.getHeight() - watermarkHeight) / 2;
                    break;
                case 7: // 左下角
                    x = 10;
                    y = source.getHeight() - watermarkHeight - 10;
                    break;
                case 8: // 底部居中
                    x = (source.getWidth() - watermarkWidth) / 2;
                    y = source.getHeight() - watermarkHeight - 10;
                    break;
                case 9: // 右下角 (默认)
                default:
                    x = source.getWidth() - watermarkWidth - 10;
                    y = source.getHeight() - watermarkHeight - 10;
                    break;
            }

            g2d.drawImage(watermark, x, y, null);
            return result;
        } catch (Exception e) {
            log.error("ImageUtils -> 添加图片水印异常：{}", e.getMessage());
            return null;
        } finally {
            if (g2d != null) {
                g2d.dispose();
            }
        }
    }

    /**
     * 获取图片信息 (宽度、高度、格式等)
     */
    public static ImageInfo getImageInfo(String imagePath) {
        if (StringUtils.isBlank(imagePath)) {
            return null;
        }
        
        try (InputStream is = new FileInputStream(imagePath);
             ImageInputStream iis = ImageIO.createImageInputStream(is)) {
            
            if (iis == null) {
                return null;
            }
            
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            
            ImageReader reader = readers.next();
            reader.setInput(iis);
            
            ImageInfo info = new ImageInfo();
            info.setWidth(reader.getWidth(0));
            info.setHeight(reader.getHeight(0));
            info.setFormat(reader.getFormatName());
            info.setMimeType(getMimeTypeFromFormat(reader.getFormatName()));
            
            reader.dispose();
            return info;
        } catch (Exception e) {
            log.error("ImageUtils -> 获取图片信息异常 {}: {}", imagePath, e.getMessage());
            return null;
        }
    }

    /**
     * 从图片格式名称获取 MIME 类型
     */
    private static String getMimeTypeFromFormat(String format) {
        if (format == null) {
            return "application/octet-stream";
        }
        String lowerFormat = format.toLowerCase();
        switch (lowerFormat) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "webp":
                return "image/webp";
            case "svg":
                return "image/svg+xml";
            case "ico":
                return "image/x-icon";
            case "tiff":
            case "tif":
                return "image/tiff";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * 转换图片格式
     */
    public static boolean convertImageFormat(String sourcePath, String targetPath, String format) {
        if (StringUtils.isBlank(sourcePath) || StringUtils.isBlank(targetPath) || StringUtils.isBlank(format)) {
            return false;
        }
        
        try {
            BufferedImage image = ImageIO.read(new File(sourcePath));
            if (image == null) {
                return false;
            }
            
            FileUtils.mkdirsForFile(targetPath);
            return ImageIO.write(image, format, new File(targetPath));
        } catch (Exception e) {
            log.error("ImageUtils -> 转换图片格式异常 {}: {}", sourcePath, e.getMessage());
            return false;
        }
    }

    /**
     * 图片转 Base64
     */
    public static String imageToBase64(String imagePath) {
        if (StringUtils.isBlank(imagePath)) {
            return StringUtils.EMPTY;
        }
        
        try {
            byte[] imageBytes = readLocalFile(imagePath);
            if (imageBytes == null) {
                return StringUtils.EMPTY;
            }
            
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            log.error("ImageUtils -> 图片转 Base64 异常 {}: {}", imagePath, e.getMessage());
            return StringUtils.EMPTY;
        }
    }

    /**
     * Base64 转图片
     */
    public static boolean base64ToImage(String base64, String outputPath) {
        if (StringUtils.isBlank(base64) || StringUtils.isBlank(outputPath)) {
            return false;
        }
        
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64);
            FileUtils.mkdirsForFile(outputPath);
            Files.write(Paths.get(outputPath), imageBytes);
            return true;
        } catch (Exception e) {
            log.error("ImageUtils -> Base64 转图片异常：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 压缩图片 (质量压缩)
     *
     * @param sourcePath 源图片路径
     * @param targetPath 目标图片路径
     * @param quality    压缩质量 (0 < quality <= 1)
     * @return 压缩成功返回 true
     */
    public static boolean compressImage(String sourcePath, String targetPath, float quality) {
        if (StringUtils.isBlank(sourcePath) || StringUtils.isBlank(targetPath) || quality <= 0 || quality > 1) {
            return false;
        }

        try {
            BufferedImage image = ImageIO.read(new File(sourcePath));
            if (image == null) {
                return false;
            }

            FileUtils.mkdirsForFile(targetPath);
            File outputFile = new File(targetPath);

            // 根据扩展名确定格式
            String format = FileTypeUtils.getFileType(targetPath);
            if (StringUtils.isEmpty(format)) {
                format = "jpg";
            }

            // 通过 ImageWriter 实现质量压缩
            ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(quality);
            }
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), writeParam);
            } finally {
                writer.dispose();
            }
            return true;
        } catch (Exception e) {
            log.error("ImageUtils -> 压缩图片异常 {}: {}", sourcePath, e.getMessage());
            return false;
        }
    }

    /**
     * 批量下载图片
     *
     * <p>任意一张图片下载失败将通过 {@link CompletableFuture#completeExceptionally(Throwable)}
     * 传播异常，调用方可感知失败；全部成功才返回 true。
     */
    public static CompletableFuture<Boolean> downloadImagesAsync(String[] imageUrls, String saveDirectory) {
        return CompletableFuture.supplyAsync(() -> {
            if (imageUrls == null || imageUrls.length == 0 || StringUtils.isBlank(saveDirectory)) {
                return false;
            }

            FileUtils.mkdirs(saveDirectory);

            RuntimeException firstError = null;
            for (String imageUrl : imageUrls) {
                try {
                    byte[] imageData = readRemoteFile(imageUrl);
                    if (imageData != null) {
                        String fileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
                        if (StringUtils.isBlank(fileName)) {
                            fileName = System.currentTimeMillis() + ".jpg";
                        }
                        String savePath = Paths.get(saveDirectory, fileName).toString();
                        Files.write(Paths.get(savePath), imageData);
                    }
                } catch (Exception e) {
                    log.error("ImageUtils -> 下载图片失败 {}: {}", imageUrl, e.getMessage());
                    if (firstError == null) {
                        firstError = new RuntimeException("Download failed for " + imageUrl + ": " + e.getMessage(), e);
                    }
                }
            }
            if (firstError != null) {
                throw firstError;
            }
            return true;
        }, EXECUTOR_SERVICE);
    }

    /**
     * 图片信息类
     */
    public static class ImageInfo {
        private int width;
        private int height;
        private String format;
        private String mimeType;

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        @Override
        public String toString() {
            return "ImageInfo{" +
                    "width=" + width +
                    ", height=" + height +
                    ", format='" + format + '\'' +
                    ", mimeType='" + mimeType + '\'' +
                    '}';
        }
    }
}
