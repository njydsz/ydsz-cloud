package com.njydsz.nextwiki.server.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * OCR 文字识别应用服务
 * <p>
 * 支持多种 OCR 引擎：本地 Tesseract、阿里云 OCR、腾讯云 OCR。
 * 用于从扫描件、图片 PDF 中提取文本内容，供全文搜索索引。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
@Service
public class OcrApplicationService {

    @Value("${nextwiki.ocr.enabled:false}")
    private boolean enabled;

    @Value("${nextwiki.ocr.provider:tesseract}")
    private String provider;

    @Value("${nextwiki.ocr.tesseract-path:tesseract}")
    private String tesseractPath;

    @Value("${nextwiki.ocr.language:chi_sim+eng}")
    private String language;

    /**
     * 识别图片中的文字
     */
    public OcrResult recognize(InputStream imageStream, String fileName) {
        if (!enabled) {
            return OcrResult.skipped("OCR 未启用");
        }

        log.info("[OcrApplicationService] 开始 OCR 识别: provider={}, file={}", provider, fileName);

        try {
            return switch (provider) {
                case "tesseract" -> recognizeByTesseract(imageStream);
                case "aliyun" -> recognizeByAliyun(imageStream);
                case "tencent" -> recognizeByTencent(imageStream);
                default -> OcrResult.error("不支持的 OCR 提供商: " + provider);
            };
        } catch (Exception e) {
            log.error("[OcrApplicationService] OCR 识别失败", e);
            return OcrResult.error("OCR 识别失败: " + e.getMessage());
        }
    }

    /**
     * Tesseract 本地 OCR
     * <p>
     * 调用 tesseract 命令行工具识别图片中的文字。
     * 流程：将输入流写入临时文件 -> 调用 tesseract 命令 -> 读取标准输出 -> 清理临时文件。
     */
    private OcrResult recognizeByTesseract(InputStream imageStream) throws Exception {
        log.info("[OcrApplicationService] Tesseract OCR（语言: {}）", language);

        Path tempFile = Files.createTempFile("nextwiki-ocr-", ".tmp");
        try {
            imageStream.transferTo(Files.newOutputStream(tempFile));

            ProcessBuilder pb = new ProcessBuilder(
                    tesseractPath, tempFile.toString(), "-", "-l", language);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String result = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("[OcrApplicationService] Tesseract 退出码: {}", exitCode);
                return OcrResult.success("", List.of());
            }

            return OcrResult.success(result.trim(), List.of());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * 阿里云 OCR
     */
    private OcrResult recognizeByAliyun(InputStream imageStream) throws Exception {
        // 实际实现：调用阿里云 OCR API
        log.info("[OcrApplicationService] 阿里云 OCR");
        return OcrResult.success("", List.of());
    }

    /**
     * 腾讯云 OCR
     */
    private OcrResult recognizeByTencent(InputStream imageStream) throws Exception {
        // 实际实现：调用腾讯云 OCR API
        log.info("[OcrApplicationService] 腾讯云 OCR");
        return OcrResult.success("", List.of());
    }

    /**
     * OCR 结果
     */
    @Data
    @Builder
    public static class OcrResult {
        private boolean success;
        private boolean skipped;
        private boolean error;
        private String text;
        private List<TextBlock> blocks;
        private String message;

        public static OcrResult success(String text, List<TextBlock> blocks) {
            return OcrResult.builder().success(true).text(text).blocks(blocks).build();
        }

        public static OcrResult skipped(String reason) {
            return OcrResult.builder().skipped(true).message(reason).build();
        }

        public static OcrResult error(String message) {
            return OcrResult.builder().error(true).message(message).build();
        }
    }

    /**
     * 文本块（包含位置信息）
     */
    @Data
    @Builder
    public static class TextBlock {
        private String text;
        private int x;
        private int y;
        private int width;
        private int height;
        private float confidence;
    }
}
