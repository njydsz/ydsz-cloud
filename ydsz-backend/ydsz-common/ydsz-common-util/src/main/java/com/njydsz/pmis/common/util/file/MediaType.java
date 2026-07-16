package com.njydsz.common.util.file;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MediaType - 增强版媒体类型工具类
 * 参考：Spring MediaType, Apache Tika, IANA Media Types
 * 
 * @author ydsz-team
 * @since 1.0.0
 * 
 *
 */
public class MediaType {
    
    // ==================== 图片类型 ====================
    public static final String IMAGE_PNG = "image/png";
    public static final String IMAGE_JPG = "image/jpg";
    public static final String IMAGE_JPEG = "image/jpeg";
    public static final String IMAGE_BMP = "image/bmp";
    public static final String IMAGE_GIF = "image/gif";
    public static final String IMAGE_WEBP = "image/webp";
    public static final String IMAGE_SVG = "image/svg+xml";
    public static final String IMAGE_SVG_XML = "image/svg+xml";
    public static final String IMAGE_ICON = "image/x-icon";
    public static final String IMAGE_TIFF = "image/tiff";
    
    // ==================== 文本类型 ====================
    public static final String TEXT_PLAIN = "text/plain";
    public static final String TEXT_HTML = "text/html";
    public static final String TEXT_XML = "text/xml";
    public static final String TEXT_CSS = "text/css";
    public static final String TEXT_JAVASCRIPT = "text/javascript";
    public static final String TEXT_MARKDOWN = "text/markdown";
    public static final String TEXT_CSV = "text/csv";
    
    // ==================== 应用类型 ====================
    public static final String APPLICATION_JSON = "application/json";
    public static final String APPLICATION_XML = "application/xml";
    public static final String APPLICATION_PDF = "application/pdf";
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    public static final String APPLICATION_ZIP = "application/zip";
    public static final String APPLICATION_GZIP = "application/gzip";
    public static final String APPLICATION_X_TAR = "application/x-tar";
    public static final String APPLICATION_X_RAR_COMPRESSED = "application/x-rar-compressed";
    public static final String APPLICATION_X_7Z_COMPRESSED = "application/x-7z-compressed";
    public static final String APPLICATION_MSWORD = "application/msword";
    public static final String APPLICATION_X_EXCEL = "application/vnd.ms-excel";
    public static final String APPLICATION_X_POWERPOINT = "application/vnd.ms-powerpoint";
    public static final String APPLICATION_OPENXMLFORMATS_WORD = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String APPLICATION_OPENXMLFORMATS_EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String APPLICATION_OPENXMLFORMATS_POWERPOINT = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    public static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    public static final String APPLICATION_SOAP_XML = "application/soap+xml";
    public static final String APPLICATION_JAVASCRIPT = "application/javascript";
    public static final String APPLICATION_TYPESCRIPT = "application/typescript";
    public static final String APPLICATION_X_YAML = "application/x-yaml";
    public static final String APPLICATION_X_TOML = "application/x-toml";
    
    // ==================== 音频类型 ====================
    public static final String AUDIO_MPEG = "audio/mpeg";
    public static final String AUDIO_WAV = "audio/wav";
    public static final String AUDIO_OGG = "audio/ogg";
    public static final String AUDIO_MP4 = "audio/mp4";
    public static final String AUDIO_WEBM = "audio/webm";
    public static final String AUDIO_AAC = "audio/aac";
    public static final String AUDIO_FLAC = "audio/flac";
    public static final String AUDIO_MIDI = "audio/midi";
    public static final String AUDIO_X_M4A = "audio/x-m4a";
    
    // ==================== 视频类型 ====================
    public static final String VIDEO_MP4 = "video/mp4";
    public static final String VIDEO_WEBM = "video/webm";
    public static final String VIDEO_OGG = "video/ogg";
    public static final String VIDEO_QUICKTIME = "video/quicktime";
    public static final String VIDEO_X_MSVIDEO = "video/x-msvideo";
    public static final String VIDEO_X_FLV = "video/x-flv";
    public static final String VIDEO_X_MATROSKA = "video/x-matroska";
    public static final String VIDEO_X_M4V = "video/x-m4v";
    public static final String VIDEO_3GPP = "video/3gpp";
    public static final String VIDEO_3GPP2 = "video/3gpp2";
    
    // ==================== 字体类型 ====================
    public static final String FONT_TTF = "font/ttf";
    public static final String FONT_OTF = "font/otf";
    public static final String FONT_WOFF = "font/woff";
    public static final String FONT_WOFF2 = "font/woff2";
    public static final String FONT_EOT = "application/vnd.ms-fontobject";
    
    // ==================== 扩展名数组 ====================
    public static final String[] IMAGE_EXTENSION = {"bmp", "gif", "jpg", "jpeg", "png", "webp", "svg", "ico", "tiff"};
    
    public static final String[] FLASH_EXTENSION = {"swf", "flv"};
    
    public static final String[] MEDIA_EXTENSION = {"swf", "flv", "mp3", "wav", "wma", "wmv", "mid", "avi", "mpg",
            "asf", "rm", "rmvb"};
    
    public static final String[] VIDEO_EXTENSION = {"mp4", "avi", "rmvb", "mkv", "webm", "mov", "wmv", "3gp", "flv"};
    
    public static final String[] AUDIO_EXTENSION = {"mp3", "wav", "wma", "ogg", "aac", "flac", "m4a", "mid", "midi"};
    
    public static final String[] DOCUMENT_EXTENSION = {"doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "txt", 
            "html", "htm", "xml", "csv", "md"};
    
    public static final String[] ARCHIVE_EXTENSION = {"zip", "rar", "gz", "tar", "7z", "bz2", "xz", "jar"};
    
    public static final String[] DEFAULT_ALLOWED_EXTENSION = {
            // 图片
            "bmp", "gif", "jpg", "jpeg", "png", "webp",
            // word excel powerpoint
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "html", "htm", "txt",
            // 压缩文件
            "rar", "zip", "gz", "bz2",
            // 视频格式
            "mp4", "avi", "rmvb",
            // pdf
            "pdf"};

    // MIME 类型到扩展名的映射
    private static final Map<String, String> MIME_TO_EXTENSION_MAP = new HashMap<>();
    
    // 扩展名到 MIME 类型的映射
    private static final Map<String, String> EXTENSION_TO_MIME_MAP = new HashMap<>();
    
    static {
        initMimeTypeMappings();
    }
    
    private static void initMimeTypeMappings() {
        // 图片类型
        addMapping(IMAGE_PNG, "png");
        addMapping(IMAGE_JPG, "jpg");
        addMapping(IMAGE_JPEG, "jpeg");
        addMapping(IMAGE_BMP, "bmp");
        addMapping(IMAGE_GIF, "gif");
        addMapping(IMAGE_WEBP, "webp");
        addMapping(IMAGE_SVG, "svg");
        addMapping(IMAGE_ICON, "ico");
        addMapping(IMAGE_TIFF, "tiff");
        
        // 文本类型
        addMapping(TEXT_PLAIN, "txt");
        addMapping(TEXT_HTML, "html");
        addMapping(TEXT_HTML, "htm");
        addMapping(TEXT_XML, "xml");
        addMapping(TEXT_CSS, "css");
        addMapping(TEXT_JAVASCRIPT, "js");
        addMapping(TEXT_MARKDOWN, "md");
        addMapping(TEXT_CSV, "csv");
        
        // 应用类型
        addMapping(APPLICATION_JSON, "json");
        addMapping(APPLICATION_XML, "xml");
        addMapping(APPLICATION_PDF, "pdf");
        addMapping(APPLICATION_OCTET_STREAM, "bin");
        addMapping(APPLICATION_ZIP, "zip");
        addMapping(APPLICATION_GZIP, "gz");
        addMapping(APPLICATION_X_TAR, "tar");
        addMapping(APPLICATION_X_RAR_COMPRESSED, "rar");
        addMapping(APPLICATION_X_7Z_COMPRESSED, "7z");
        addMapping(APPLICATION_MSWORD, "doc");
        addMapping(APPLICATION_X_EXCEL, "xls");
        addMapping(APPLICATION_X_POWERPOINT, "ppt");
        addMapping(APPLICATION_OPENXMLFORMATS_WORD, "docx");
        addMapping(APPLICATION_OPENXMLFORMATS_EXCEL, "xlsx");
        addMapping(APPLICATION_OPENXMLFORMATS_POWERPOINT, "pptx");
        addMapping(APPLICATION_JAVASCRIPT, "js");
        addMapping(APPLICATION_TYPESCRIPT, "ts");
        addMapping(APPLICATION_X_YAML, "yaml");
        addMapping(APPLICATION_X_YAML, "yml");
        addMapping(APPLICATION_X_TOML, "toml");
        
        // 音频类型
        addMapping(AUDIO_MPEG, "mp3");
        addMapping(AUDIO_WAV, "wav");
        addMapping(AUDIO_OGG, "ogg");
        addMapping(AUDIO_MP4, "m4a");
        addMapping(AUDIO_WEBM, "webm");
        addMapping(AUDIO_AAC, "aac");
        addMapping(AUDIO_FLAC, "flac");
        addMapping(AUDIO_MIDI, "mid");
        addMapping(AUDIO_MIDI, "midi");
        addMapping(AUDIO_X_M4A, "m4a");
        
        // 视频类型
        addMapping(VIDEO_MP4, "mp4");
        addMapping(VIDEO_WEBM, "webm");
        addMapping(VIDEO_OGG, "ogv");
        addMapping(VIDEO_QUICKTIME, "mov");
        addMapping(VIDEO_X_MSVIDEO, "avi");
        addMapping(VIDEO_X_FLV, "flv");
        addMapping(VIDEO_X_MATROSKA, "mkv");
        addMapping(VIDEO_X_M4V, "m4v");
        addMapping(VIDEO_3GPP, "3gp");
        addMapping(VIDEO_3GPP2, "3g2");
        
        // 字体类型
        addMapping(FONT_TTF, "ttf");
        addMapping(FONT_OTF, "otf");
        addMapping(FONT_WOFF, "woff");
        addMapping(FONT_WOFF2, "woff2");
        addMapping(FONT_EOT, "eot");
    }
    
    private static void addMapping(String mimeType, String extension) {
        MIME_TO_EXTENSION_MAP.put(mimeType.toLowerCase(), extension.toLowerCase());
        EXTENSION_TO_MIME_MAP.put(extension.toLowerCase(), mimeType);
    }
    
    /**
     * 根据 MIME 类型获取扩展名
     */
    public static String getExtension(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        return MIME_TO_EXTENSION_MAP.getOrDefault(mimeType.toLowerCase(), "");
    }
    
    /**
     * 根据扩展名获取 MIME 类型
     */
    public static String getMimeType(String extension) {
        if (extension == null) {
            return APPLICATION_OCTET_STREAM;
        }
        return EXTENSION_TO_MIME_MAP.getOrDefault(extension.toLowerCase(), APPLICATION_OCTET_STREAM);
    }
    
    /**
     * 判断是否是图片类型
     */
    public static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("image/");
    }
    
    /**
     * 判断是否是视频类型
     */
    public static boolean isVideo(String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("video/");
    }
    
    /**
     * 判断是否是音频类型
     */
    public static boolean isAudio(String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("audio/");
    }
    
    /**
     * 判断是否是文本类型
     */
    public static boolean isText(String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("text/");
    }
    
    /**
     * 判断是否是应用类型
     */
    public static boolean isApplication(String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("application/");
    }
    
    /**
     * 判断是否是字体类型
     */
    public static boolean isFont(String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("font/");
    }
    
    /**
     * 判断是否是压缩文件
     */
    public static boolean isArchive(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String lowerMimeType = mimeType.toLowerCase();
        return lowerMimeType.contains("zip") || 
               lowerMimeType.contains("rar") || 
               lowerMimeType.contains("gzip") || 
               lowerMimeType.contains("tar") || 
               lowerMimeType.contains("7z");
    }
    
    /**
     * 判断是否是文档类型
     */
    public static boolean isDocument(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String lowerMimeType = mimeType.toLowerCase();
        return lowerMimeType.contains("word") || 
               lowerMimeType.contains("excel") || 
               lowerMimeType.contains("powerpoint") ||
               lowerMimeType.contains("pdf") ||
               lowerMimeType.equals(TEXT_PLAIN);
    }
    
    /**
     * 获取所有图片扩展名
     */
    public static Set<String> getImageExtensions() {
        Set<String> extensions = new HashSet<>();
        Collections.addAll(extensions, IMAGE_EXTENSION);
        return Collections.unmodifiableSet(extensions);
    }
    
    /**
     * 获取所有视频扩展名
     */
    public static Set<String> getVideoExtensions() {
        Set<String> extensions = new HashSet<>();
        Collections.addAll(extensions, VIDEO_EXTENSION);
        return Collections.unmodifiableSet(extensions);
    }
    
    /**
     * 获取所有音频扩展名
     */
    public static Set<String> getAudioExtensions() {
        Set<String> extensions = new HashSet<>();
        Collections.addAll(extensions, AUDIO_EXTENSION);
        return Collections.unmodifiableSet(extensions);
    }
    
    /**
     * 获取所有文档扩展名
     */
    public static Set<String> getDocumentExtensions() {
        Set<String> extensions = new HashSet<>();
        Collections.addAll(extensions, DOCUMENT_EXTENSION);
        return Collections.unmodifiableSet(extensions);
    }
    
    /**
     * 获取所有压缩文件扩展名
     */
    public static Set<String> getArchiveExtensions() {
        Set<String> extensions = new HashSet<>();
        Collections.addAll(extensions, ARCHIVE_EXTENSION);
        return Collections.unmodifiableSet(extensions);
    }
    
    /**
     * 获取所有支持的 MIME 类型
     */
    public static Set<String> getAllSupportedMimeTypes() {
        return Collections.unmodifiableSet(new HashSet<>(EXTENSION_TO_MIME_MAP.values()));
    }
    
    /**
     * 获取所有支持的扩展名
     */
    public static Set<String> getAllSupportedExtensions() {
        return Collections.unmodifiableSet(EXTENSION_TO_MIME_MAP.keySet());
    }
    
    /**
     * 检查扩展名是否支持
     */
    public static boolean isSupportedExtension(String extension) {
        return extension != null && EXTENSION_TO_MIME_MAP.containsKey(extension.toLowerCase());
    }
    
    /**
     * 检查 MIME 类型是否支持
     */
    public static boolean isSupportedMimeType(String mimeType) {
        return mimeType != null && MIME_TO_EXTENSION_MAP.containsKey(mimeType.toLowerCase());
    }
}
