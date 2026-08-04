package com.njydsz.nextwiki.server.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.nextwiki.server.config.NextwikiProperties;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OCR 识别服务。
 * <p>从图片中提取文字。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Service
@RequiredArgsConstructor
public class OcrApplicationService {

    private final NextwikiProperties properties;

    /**
     * 识别图片中的文字（按配置服务商路由）。
     * <p>OCR 未启用时直接返回 skipped；按 {@code nextwiki.ocr.provider} 路由到
     * tesseract/aliyun/tencent，当前仅 tesseract 本地实现可用，云厂商为 TODO 占位。
     * 整个识别过程异常被捕获并转为 {@code error} 结果，不会向上抛出。
     *
     * @param imageStream 图片输入流（方法内读尽，调用方不必复用）
     * @param fileName    文件名（仅用于日志，不参与识别）
     * @return OCR 结果 {@link OcrResult}（含 success/skipped/error 状态与文本）
     * @complexity O(imageSize)（tesseract 为本地进程调用，受图片分辨率影响）
     * @note 无事务边界；异常不抛出，调用方需通过 {@link OcrResult#isSuccess()} 等判断结果
     * @concurrency 无共享可变状态，线程安全；tesseract 为进程调用，并发受系统资源约束
     */
    public OcrResult recognize(InputStream imageStream, String fileName) {
        if (!properties.getOcr().isEnabled()) {
            return OcrResult.skipped("OCR 未启用");
        }

        String provider = properties.getOcr().getProvider();
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
        String language = properties.getOcr().getLanguage();
        String tesseractPath = properties.getOcr().getTesseractPath();
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
     * <p>
     * TODO: 集成阿里云 OCR API（需要 aliyun-ocr-sdk 依赖）
     */
    private OcrResult recognizeByAliyun(InputStream imageStream) throws Exception {
        log.info("[OcrApplicationService] 阿里云 OCR（TODO: 待集成阿里云 OCR SDK）");
        return OcrResult.skipped("阿里云 OCR 尚未集成");
    }

    /**
     * 腾讯云 OCR
     * <p>
     * TODO: 集成腾讯云 OCR API（需要 tencentcloud-sdk-java 依赖）
     */
    private OcrResult recognizeByTencent(InputStream imageStream) throws Exception {
        log.info("[OcrApplicationService] 腾讯云 OCR（TODO: 待集成腾讯云 OCR SDK）");
        return OcrResult.skipped("腾讯云 OCR 尚未集成");
    }

    /**
     * OCR 结果
     */
    @Data
    @Builder
    public static class OcrResult {
        /** 是否识别成功（含有效文本或空文本） */
        private boolean success;
        /** 是否被跳过（如 OCR 未启用或云厂商未集成） */
        private boolean skipped;
        /** 是否出错（异常或非法配置） */
        private boolean error;
        /** 识别出的纯文本（skipped/error 时为空） */
        private String text;
        /** 文本块列表（含位置/置信度，当前实现未填充，恒为单块或空） */
        private List<TextBlock> blocks;
        /** 跳过/错误原因描述 */
        private String message;

        /**
         * 构造「识别成功」结果。
         *
         * <p>注意：<b>空文本也算成功</b>。Tesseract 退出码非 0 或图片无文字时，
         * 会以空串调用本方法，语义是「OCR 流程正常跑完但没提取到内容」，
         * 与 {@link #error(String)} 的「流程失败」严格区分，调用方勿把空文本当异常处理。
         *
         * @param text   识别出的纯文本，可能为空串，不为 {@code null}
         * @param blocks 带坐标的文本块列表；当前 tesseract 实现不回填，恒为空列表
         * @return 成功结果，{@code success=true}，其余状态位为 false
         */
        public static OcrResult success(String text, List<TextBlock> blocks) {
            return OcrResult.builder().success(true).text(text).blocks(blocks).build();
        }

        /**
         * 构造「跳过识别」结果。
         *
         * <p>用于 OCR 开关关闭、或所选服务商尚未集成等<b>非故障</b>场景。
         * 与 {@link #error(String)} 分开表达，避免把「有意不做」误报为告警。
         * 此时 {@code text} 为 {@code null}，调用方须先判 {@code skipped} 再取文本。
         *
         * @param reason 跳过原因（如 {@code "OCR 未启用"}）
         * @return 跳过结果，{@code skipped=true}
         */
        public static OcrResult skipped(String reason) {
            return OcrResult.builder().skipped(true).message(reason).build();
        }

        /**
         * 构造「识别失败」结果。
         *
         * <p>OCR 属附加增强能力，其失败不应中断文件上传主流程，因此异常在
         * {@link OcrApplicationService#recognize} 内被捕获并转成本结果返回，
         * <b>不向上抛出</b>；此时 {@code text} 为 {@code null}。
         *
         * @param message 失败描述，通常携带底层异常摘要，供排查与日志留痕
         * @return 失败结果，{@code error=true}
         */
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
        /** 该文本块识别出的文字 */
        private String text;
        /** 文本块左上角 x 坐标（像素） */
        private int x;
        /** 文本块左上角 y 坐标（像素） */
        private int y;
        /** 文本块宽度（像素） */
        private int width;
        /** 文本块高度（像素） */
        private int height;
        /** 识别置信度（0~1，tesseract 当前未回填，默认 0） */
        private float confidence;
    }
}
