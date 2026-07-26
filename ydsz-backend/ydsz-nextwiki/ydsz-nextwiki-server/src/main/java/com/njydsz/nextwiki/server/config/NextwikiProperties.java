package com.njydsz.nextwiki.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * NextWiki 配置属性类（P3-2）
 * <p>
 * 集中管理 nextwiki.* 配置项，替代散落在各 Service 中的 @Value 注解。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "nextwiki")
public class NextwikiProperties {

    /** 文件上传配置 */
    private Upload upload = new Upload();

    /** 文档预览配置 */
    private Preview preview = new Preview();

    /** 缩略图配置 */
    private Thumbnail thumbnail = new Thumbnail();

    /** AI 摘要配置 */
    private Ai ai = new Ai();

    /** CDN 配置 */
    private Cdn cdn = new Cdn();

    /** 病毒扫描配置 */
    private VirusScan virusScan = new VirusScan();

    /** OCR 配置 */
    private Ocr ocr = new Ocr();

    /** 下载限流配置 */
    private Download download = new Download();

    /** WOPI 在线编辑配置 */
    private Wopi wopi = new Wopi();

    @Data
    public static class Upload {
        private long maxFileSize = 524288000L;
        private String allowedTypes = "";
        /** 同名冲突策略 */
        private String conflictStrategy = "KEEP_BOTH";
        /** 分片上传临时目录 */
        private String chunkTempDir;
    }

    @Data
    public static class Preview {
        private String libreofficePath = "soffice";
        private String tempDir;
    }

    @Data
    public static class Thumbnail {
        private String tempDir;
    }

    @Data
    public static class Ai {
        private boolean llmEnabled = false;
        private String llmApiUrl = "";
        private String llmApiKey = "";
        private String llmModel = "gpt-3.5-turbo";
    }

    @Data
    public static class Cdn {
        private boolean enabled = false;
        private String provider = "aliyun";
        private String domain = "";
        private String accessKey = "";
        private String secretKey = "";
    }

    @Data
    public static class VirusScan {
        private boolean enabled = false;
        private String host = "localhost";
        @Min(1)
        @Max(65535)
        private int port = 3310;
    }

    @Data
    public static class Ocr {
        private boolean enabled = false;
        private String provider = "tesseract";
        private String tesseractPath = "tesseract";
        private String language = "chi_sim+eng";
    }

    @Data
    public static class Download {
        @Min(1)
        private int rateLimitPerMinute = 30;
        @Min(1)
        private int ipRateLimitPerMinute = 100;
        @Min(1)
        private long signedUrlExpireSeconds = 3600;
    }

    @Data
    public static class Wopi {
        private String editorUrl = "";
    }
}
