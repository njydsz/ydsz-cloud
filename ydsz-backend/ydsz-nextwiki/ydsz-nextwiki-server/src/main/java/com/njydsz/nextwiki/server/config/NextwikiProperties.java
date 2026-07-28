package com.njydsz.nextwiki.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * 网盘知识库（NextWiki）全局配置（prefix = {@code nextwiki}）。
 *
 * <p>绑定 {@code application.yml} 中 {@code nextwiki.*} 配置项，
 * 涵盖文件上传、缩略图、OCR、CDN、AI 摘要、病毒扫描、预览、下载限流、分片上传、WOPI 等子模块。
 *
 * <p>所有配置项均提供默认值，未配置时降级为安全默认（关闭高级能力，保证基础文件管理可用）。
 *
 * <h3>子配置分组</h3>
 * <ul>
 *   <li>{@link UploadConfig} — 文件上传限制（大小 / 类型 / 冲突策略 / 分片临时目录）</li>
 *   <li>{@link ThumbnailConfig} — 缩略图生成（临时目录）</li>
 *   <li>{@link OcrConfig} — OCR 文字识别（开关 / 服务商 / 语言）</li>
 *   <li>{@link CdnConfig} — CDN 加速（开关 / 服务商 / 域名 / 凭证）</li>
 *   <li>{@link AiConfig} — AI 摘要（开关 / LLM API 配置）</li>
 *   <li>{@link VirusScanConfig} — 病毒扫描（开关 / ClamAV 连接）</li>
 *   <li>{@link PreviewConfig} — 文档预览（LibreOffice 路径 / 临时目录）</li>
 *   <li>{@link DownloadConfig} — 下载限流（单用户 / IP 维度 / 签名 URL 有效期）</li>
 *   <li>{@link WopiConfig} — WOPI 在线编辑（编辑器 URL / 访问令牌）</li>
 * </ul>
 *
 * <p>使用方式：在需要读取配置的 Service 中注入 {@link NextwikiProperties}，
 * 通过 {@code properties.getUpload().getMaxFileSize()} 等方法访问，替代散落的 {@code @Value} 注入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "nextwiki")
public class NextwikiProperties {

    /** 文件上传配置 */
    private UploadConfig upload = new UploadConfig();

    /** 缩略图配置 */
    private ThumbnailConfig thumbnail = new ThumbnailConfig();

    /** OCR 配置 */
    private OcrConfig ocr = new OcrConfig();

    /** CDN 配置 */
    private CdnConfig cdn = new CdnConfig();

    /** AI 摘要配置 */
    private AiConfig ai = new AiConfig();

    /** 病毒扫描配置 */
    private VirusScanConfig virusScan = new VirusScanConfig();

    /** 文档预览配置 */
    private PreviewConfig preview = new PreviewConfig();

    /** 下载限流配置 */
    private DownloadConfig download = new DownloadConfig();

    /** WOPI 在线编辑配置 */
    private WopiConfig wopi = new WopiConfig();

    /**
     * 文件上传配置。
     *
     * <p>控制文件大小上限、允许的文件类型、同名冲突处理策略、分片上传临时目录。
     */
    @Data
    public static class UploadConfig {
        /** 最大文件大小（字节），默认 500MB */
        private long maxFileSize = 524288000L;

        /** 允许的文件类型（逗号分隔 MIME/扩展名，空表示不限） */
        private String allowedTypes = "";

        /** 同名冲突策略：KEEP_BOTH / OVERWRITE / REJECT，默认 KEEP_BOTH */
        private String conflictStrategy = "KEEP_BOTH";

        /** 分片上传临时目录（默认系统临时目录下的 nextwiki-chunk 子目录） */
        private String chunkTempDir = System.getProperty("java.io.tmpdir") + "/nextwiki-chunk";
    }

    /**
     * 缩略图配置。
     */
    @Data
    public static class ThumbnailConfig {
        /** 缩略图临时目录 */
        private String tempDir = "/tmp/nextwiki-thumbnail";
    }

    /**
     * OCR 文字识别配置。
     *
     * <p>通过 {@code nextwiki.ocr.provider} 选择服务商（tesseract），
     * 未启用时降级为跳过 OCR 提取。
     */
    @Data
    public static class OcrConfig {
        /** OCR 开关（关闭后不提取文字内容） */
        private boolean enabled = false;

        /** 服务商：tesseract（默认） */
        private String provider = "tesseract";

        /** Tesseract 可执行文件路径 */
        private String tesseractPath = "tesseract";

        /** OCR 识别语言（默认简体中文+英文） */
        private String language = "chi_sim+eng";
    }

    /**
     * CDN 加速配置。
     *
     * <p>通过 {@code nextwiki.cdn.provider} 选择服务商（aliyun），
     * 未配置凭证时降级为直接访问源站。
     */
    @Data
    public static class CdnConfig {
        /** CDN 开关（关闭后文件 URL 指向源站） */
        private boolean enabled = false;

        /** 服务商：aliyun（默认） */
        private String provider = "aliyun";

        /** CDN 域名 */
        private String domain = "";

        /** AccessKey ID */
        private String accessKey = "";

        /** AccessKey Secret */
        private String secretKey = "";
    }

    /**
     * AI 摘要配置。
     *
     * <p>通过 {@code nextwiki.ai.llm-enabled} 控制是否启用 AI 摘要，
     * 未配置 API 凭证时降级为跳过 AI 摘要。
     */
    @Data
    public static class AiConfig {
        /** LLM 摘要开关 */
        private boolean llmEnabled = false;

        /** LLM API 地址 */
        private String llmApiUrl = "";

        /** LLM API Key */
        private String llmApiKey = "";

        /** LLM 模型名称 */
        private String llmModel = "gpt-3.5-turbo";
    }

    /**
     * 病毒扫描配置。
     *
     * <p>基于 ClamAV 守护进程（C/S 模式），默认关闭。
     * 启用后文件上传前自动扫描，检测到病毒时拒绝上传。
     */
    @Data
    public static class VirusScanConfig {
        /** 病毒扫描开关 */
        private boolean enabled = false;

        /** ClamAV 守护进程地址 */
        private String host = "localhost";

        /** ClamAV 守护进程端口 */
        private int port = 3310;
    }

    /**
     * 文档预览配置。
     *
     * <p>基于 LibreOffice ({@code soffice}) 进行 Office 文档转 PDF 预览。
     */
    @Data
    public static class PreviewConfig {
        /** LibreOffice 可执行文件路径 */
        private String libreofficePath = "soffice";

        /** 预览临时目录 */
        private String tempDir = "/tmp/nextwiki-preview";
    }

    /**
     * 下载限流配置。
     *
     * <p>支持单用户维度和 IP 维度的下载频率限制，防止恶意批量下载。
     */
    @Data
    public static class DownloadConfig {
        /** 单用户每分钟下载次数上限 */
        private int rateLimitPerMinute = 30;

        /** 单 IP 每分钟下载次数上限 */
        private int ipRateLimitPerMinute = 100;

        /** 签名 URL 有效期（秒），默认 1 小时 */
        private long signedUrlExpireSeconds = 3600L;
    }

    /**
     * WOPI 在线编辑配置。
     *
     * <p>WOPI（Web Application Open Platform Interface）协议用于与 Office Online /
     * Collabora / OnlyOffice 等在线编辑器对接。
     */
    @Data
    public static class WopiConfig {
        /** 在线编辑器 URL */
        private String editorUrl = "";

        /** WOPI 访问令牌（用于校验编辑器回调请求） */
        private String accessToken = "";
    }
}
