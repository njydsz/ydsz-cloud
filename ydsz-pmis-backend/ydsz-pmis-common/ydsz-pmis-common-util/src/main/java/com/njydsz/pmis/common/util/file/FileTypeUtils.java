package com.njydsz.pmis.common.util.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

import com.njydsz.pmis.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * FileTypeUtils - 增强版文件类型检测工具类
 * 参考：Apache Tika, Google File Type Detection
 * 
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 *
 */
@Slf4j
public class FileTypeUtils {

    private static final Map<String, String> FILE_TYPE_MAP = new HashMap<>();
    private static final Map<String, String> MIME_TYPE_MAP = new HashMap<>();

    static {
        initFileTypeMap();
        initMimeTypeMap();
    }

    private static void initFileTypeMap() {
        // 图片类型
        FILE_TYPE_MAP.put("FFD8FF", "jpg");
        FILE_TYPE_MAP.put("89504E47", "png");
        FILE_TYPE_MAP.put("47494638", "gif");
        FILE_TYPE_MAP.put("424D", "bmp");
        FILE_TYPE_MAP.put("00000100", "ico");
        FILE_TYPE_MAP.put("52494646", "webp"); // RIFF 容器
        
        // PDF
        FILE_TYPE_MAP.put("25504446", "pdf");
        
        // 压缩文件
        FILE_TYPE_MAP.put("504B0304", "zip");
        FILE_TYPE_MAP.put("52617221", "rar");
        FILE_TYPE_MAP.put("1F8B08", "gz");
        FILE_TYPE_MAP.put("FD377A585A00", "xz");
        FILE_TYPE_MAP.put("377ABCAF271C", "7z");
        
        // 文档
        FILE_TYPE_MAP.put("D0CF11E0", "xls"); // OLE 格式
        FILE_TYPE_MAP.put("504B030414000600", "docx");
        FILE_TYPE_MAP.put("504B030414000600", "xlsx");
        FILE_TYPE_MAP.put("504B030414000600", "pptx");
        
        // 视频
        FILE_TYPE_MAP.put("0000001866747970", "mp4");
        FILE_TYPE_MAP.put("0000002066747970", "mp4");
        FILE_TYPE_MAP.put("1A45DFA3", "mkv");
        FILE_TYPE_MAP.put("0000001C66747970", "3gp");
        FILE_TYPE_MAP.put("464C5601", "flv");
        FILE_TYPE_MAP.put("52494646", "avi"); // RIFF 容器
        
        // 音频
        FILE_TYPE_MAP.put("494433", "mp3"); // ID3 tag
        FILE_TYPE_MAP.put("FFF1", "aac");
        FILE_TYPE_MAP.put("664C6143", "flac");
        FILE_TYPE_MAP.put("61696666", "aiff");
        FILE_TYPE_MAP.put("52494646", "wav"); // RIFF 容器
        
        // 可执行文件
        FILE_TYPE_MAP.put("4D5A", "exe");
        FILE_TYPE_MAP.put("7F454C46", "elf"); // Linux 可执行文件
        FILE_TYPE_MAP.put("CAFEBABE", "class"); // Java class
    }

    private static void initMimeTypeMap() {
        MIME_TYPE_MAP.put("jpg", "image/jpeg");
        MIME_TYPE_MAP.put("jpeg", "image/jpeg");
        MIME_TYPE_MAP.put("png", "image/png");
        MIME_TYPE_MAP.put("gif", "image/gif");
        MIME_TYPE_MAP.put("bmp", "image/bmp");
        MIME_TYPE_MAP.put("ico", "image/x-icon");
        MIME_TYPE_MAP.put("webp", "image/webp");
        MIME_TYPE_MAP.put("pdf", "application/pdf");
        MIME_TYPE_MAP.put("zip", "application/zip");
        MIME_TYPE_MAP.put("rar", "application/x-rar-compressed");
        MIME_TYPE_MAP.put("gz", "application/gzip");
        MIME_TYPE_MAP.put("7z", "application/x-7z-compressed");
        MIME_TYPE_MAP.put("doc", "application/msword");
        MIME_TYPE_MAP.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        MIME_TYPE_MAP.put("xls", "application/vnd.ms-excel");
        MIME_TYPE_MAP.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        MIME_TYPE_MAP.put("ppt", "application/vnd.ms-powerpoint");
        MIME_TYPE_MAP.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        MIME_TYPE_MAP.put("txt", "text/plain");
        MIME_TYPE_MAP.put("html", "text/html");
        MIME_TYPE_MAP.put("htm", "text/html");
        MIME_TYPE_MAP.put("xml", "text/xml");
        MIME_TYPE_MAP.put("json", "application/json");
        MIME_TYPE_MAP.put("csv", "text/csv");
        MIME_TYPE_MAP.put("mp4", "video/mp4");
        MIME_TYPE_MAP.put("avi", "video/x-msvideo");
        MIME_TYPE_MAP.put("mkv", "video/x-matroska");
        MIME_TYPE_MAP.put("flv", "video/x-flv");
        MIME_TYPE_MAP.put("mp3", "audio/mpeg");
        MIME_TYPE_MAP.put("wav", "audio/wav");
        MIME_TYPE_MAP.put("flac", "audio/flac");
        MIME_TYPE_MAP.put("aac", "audio/aac");
    }

    /**
     * 获取文件扩展名 (不含点，统一转小写)
     */
    public static String getFileType(File file) {
        if (file == null) {
            return StringUtils.EMPTY;
        }
        return getFileType(file.getName());
    }

    /**
     * 获取文件扩展名 (不含点，统一转小写)
     */
    public static String getFileType(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return StringUtils.EMPTY;
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return StringUtils.EMPTY;
        }
        return StringUtils.lowerCase(fileName.substring(lastDotIndex + 1));
    }

    /**
     * 根据文件字节头判断文件类型 (支持更多文件类型)
     */
    public static String getFileExtendName(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < 4) {
            return "UNKNOWN";
        }
        String hex = bytesToHexString(fileBytes, Math.min(fileBytes.length, 12));
        
        for (Map.Entry<String, String> entry : FILE_TYPE_MAP.entrySet()) {
            String magicNumber = entry.getKey();
            if (hex.startsWith(magicNumber)) {
                return entry.getValue();
            }
        }
        
        return "UNKNOWN";
    }

    /**
     * 根据文件路径检测文件类型 (读取文件头)
     */
    public static String detectFileType(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return "FILE_NOT_FOUND";
        }
        
        try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(12);
            fileChannel.read(buffer);
            buffer.flip();
            
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            
            return getFileExtendName(bytes);
        } catch (IOException e) {
            log.error("FileTypeUtils -> detectFileType error for path {}: {}", filePath, e.getMessage());
            return "DETECT_ERROR";
        }
    }

    /**
     * 根据文件路径检测 MIME 类型
     */
    public static String detectMimeType(String filePath) {
        String extension = getFileType(filePath);
        String typeByContent = detectFileType(filePath);
        
        if (!"UNKNOWN".equals(typeByContent) && !"FILE_NOT_FOUND".equals(typeByContent)) {
            return MIME_TYPE_MAP.getOrDefault(typeByContent, "application/octet-stream");
        }
        
        if (StringUtils.isNotEmpty(extension)) {
            return MIME_TYPE_MAP.getOrDefault(extension, "application/octet-stream");
        }
        
        return "application/octet-stream";
    }

    /**
     * 获取 MultipartFile 的扩展名 (增强版)
     */
    public static String getExtension(MultipartFile file) {
        if (file == null) {
            return StringUtils.EMPTY;
        }
        
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isNotEmpty(originalFilename)) {
            String extension = getFileType(originalFilename);
            if (StringUtils.isNotEmpty(extension)) {
                return extension;
            }
        }
        
        if (file.getContentType() != null) {
            String extension = getExtensionByMimeType(file.getContentType());
            if (StringUtils.isNotEmpty(extension)) {
                return extension;
            }
        }
        
        return StringUtils.EMPTY;
    }

    /**
     * 根据 MIME 类型获取扩展名
     */
    public static String getExtensionByMimeType(String mimeType) {
        if (StringUtils.isBlank(mimeType)) {
            return StringUtils.EMPTY;
        }
        
        for (Map.Entry<String, String> entry : MIME_TYPE_MAP.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(mimeType)) {
                return entry.getKey();
            }
        }
        
        return StringUtils.EMPTY;
    }

    /**
     * 获取 MIME 类型
     */
    public static String getMimeType(String extension) {
        if (StringUtils.isBlank(extension)) {
            return "application/octet-stream";
        }
        return MIME_TYPE_MAP.getOrDefault(StringUtils.lowerCase(extension), "application/octet-stream");
    }

    /**
     * 获取所有支持的图片扩展名
     */
    public static Set<String> getImageExtensions() {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "ico", "webp"
        )));
    }

    /**
     * 获取所有支持的视频扩展名
     */
    public static Set<String> getVideoExtensions() {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "mp4", "avi", "mkv", "flv", "3gp", "mov", "wmv"
        )));
    }

    /**
     * 获取所有支持的音频扩展名
     */
    public static Set<String> getAudioExtensions() {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "mp3", "wav", "flac", "aac", "wma", "ogg"
        )));
    }

    /**
     * 获取所有支持的文档扩展名
     */
    public static Set<String> getDocumentExtensions() {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "html", "htm"
        )));
    }

    /**
     * 获取所有支持的压缩文件扩展名
     */
    public static Set<String> getArchiveExtensions() {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "zip", "rar", "gz", "7z", "xz", "tar", "bz2"
        )));
    }

    /**
     * 检查是否为图片文件
     */
    public static boolean isImage(String filePath) {
        String detectedType = detectFileType(filePath);
        return getImageExtensions().contains(StringUtils.lowerCase(detectedType));
    }

    /**
     * 检查是否为图片文件 (通过字节数组)
     */
    public static boolean isImage(byte[] fileBytes) {
        String detectedType = getFileExtendName(fileBytes);
        return getImageExtensions().contains(StringUtils.lowerCase(detectedType));
    }

    /**
     * 检查是否为视频文件
     */
    public static boolean isVideo(String filePath) {
        String detectedType = detectFileType(filePath);
        return getVideoExtensions().contains(StringUtils.lowerCase(detectedType));
    }

    /**
     * 检查是否为音频文件
     */
    public static boolean isAudio(String filePath) {
        String detectedType = detectFileType(filePath);
        return getAudioExtensions().contains(StringUtils.lowerCase(detectedType));
    }

    /**
     * 检查是否为文档文件
     */
    public static boolean isDocument(String filePath) {
        String detectedType = detectFileType(filePath);
        return getDocumentExtensions().contains(StringUtils.lowerCase(detectedType));
    }

    /**
     * 检查是否为压缩文件
     */
    public static boolean isArchive(String filePath) {
        String detectedType = detectFileType(filePath);
        return getArchiveExtensions().contains(StringUtils.lowerCase(detectedType));
    }

    /**
     * 验证文件扩展名是否在白名单中
     */
    public static boolean isValidExtension(String filePath, Set<String> allowedExtensions) {
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            return true;
        }
        String extension = getFileType(filePath);
        return allowedExtensions.contains(StringUtils.lowerCase(extension));
    }

    /**
     * 验证文件类型是否通过魔法值检测
     */
    public static boolean validateFileContent(String filePath, Set<String> allowedTypes) {
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            return true;
        }
        String detectedType = detectFileType(filePath);
        return allowedTypes.contains(StringUtils.lowerCase(detectedType));
    }

    /**
     * 字节数组转十六进制字符串 (优化版)
     */
    private static String bytesToHexString(byte[] src, int length) {
        if (src == null || src.length == 0) {
            return StringUtils.EMPTY;
        }
        
        StringBuilder hexBuilder = new StringBuilder(length * 2);
        for (int i = 0; i < length && i < src.length; i++) {
            String hex = Integer.toHexString(src[i] & 0xFF);
            if (hex.length() == 1) {
                hexBuilder.append('0');
            }
            hexBuilder.append(hex.toUpperCase());
        }
        return hexBuilder.toString();
    }

    /**
     * 从输入流读取文件头并检测类型
     */
    public static String detectFromStream(InputStream inputStream) {
        if (inputStream == null) {
            return "UNKNOWN";
        }
        
        try {
            byte[] header = new byte[12];
            int bytesRead = inputStream.read(header);
            
            if (bytesRead < 4) {
                return "UNKNOWN";
            }
            
            return getFileExtendName(header);
        } catch (IOException e) {
            log.error("FileTypeUtils -> detectFromStream error: {}", e.getMessage());
            return "DETECT_ERROR";
        }
    }

    /**
     * 判断是否为图片文件（通过扩展名）
     */
    public static boolean isImageExtension(String suffix) {
        if (StringUtils.isBlank(suffix)) {
            return false;
        }
        String lowerSuffix = StringUtils.lowerCase(suffix);
        return "png".equals(lowerSuffix) || "bmp".equals(lowerSuffix) || 
               "jpg".equals(lowerSuffix) || "jpeg".equals(lowerSuffix) || 
               "gif".equals(lowerSuffix) || "svg".equals(lowerSuffix) || 
               "ico".equals(lowerSuffix) || "webp".equals(lowerSuffix);
    }

    /**
     * 判断是否为视频文件（通过扩展名）
     */
    public static boolean isVideoExtension(String suffix) {
        if (StringUtils.isBlank(suffix)) {
            return false;
        }
        String lowerSuffix = StringUtils.lowerCase(suffix);
        return "mp4".equals(lowerSuffix) || "flv".equals(lowerSuffix) || 
               "avi".equals(lowerSuffix) || "mkv".equals(lowerSuffix) || 
               "mov".equals(lowerSuffix) || "wmv".equals(lowerSuffix) || 
               "3gp".equals(lowerSuffix);
    }

    /**
     * 判断是否为音频文件（通过扩展名）
     */
    public static boolean isAudioExtension(String suffix) {
        if (StringUtils.isBlank(suffix)) {
            return false;
        }
        String lowerSuffix = StringUtils.lowerCase(suffix);
        return "mp3".equals(lowerSuffix) || "wma".equals(lowerSuffix) || 
               "wav".equals(lowerSuffix) || "flac".equals(lowerSuffix) || 
               "aac".equals(lowerSuffix);
    }

    /**
     * 判断是否为办公文档文件（通过扩展名）
     */
    public static boolean isOfficeExtension(String suffix) {
        if (StringUtils.isBlank(suffix)) {
            return false;
        }
        String lowerSuffix = StringUtils.lowerCase(suffix);
        return "txt".equals(lowerSuffix) || "md".equals(lowerSuffix) || 
               "doc".equals(lowerSuffix) || "docx".equals(lowerSuffix) || 
               "xls".equals(lowerSuffix) || "xlsx".equals(lowerSuffix) || 
               "ppt".equals(lowerSuffix) || "pptx".equals(lowerSuffix) || 
               "pdf".equals(lowerSuffix) || "csv".equals(lowerSuffix);
    }

    /**
     * 判断是否为代码文件（通过扩展名）
     */
    public static boolean isCodeExtension(String suffix) {
        if (StringUtils.isBlank(suffix)) {
            return false;
        }
        String lowerSuffix = StringUtils.lowerCase(suffix);
        return "java".equals(lowerSuffix) || "sql".equals(lowerSuffix) || 
               "js".equals(lowerSuffix) || "py".equals(lowerSuffix) || 
               "py3".equals(lowerSuffix) || "php".equals(lowerSuffix) || 
               "vue".equals(lowerSuffix) || "sh".equals(lowerSuffix) || 
               "cmd".equals(lowerSuffix) || "css".equals(lowerSuffix) || 
               "html".equals(lowerSuffix) || "htm".equals(lowerSuffix) || 
               "xml".equals(lowerSuffix) || "json".equals(lowerSuffix);
    }

    /**
     * 判断文件后缀是否在默认白名单中
     */
    public static boolean isAllowedExtension(String suffix) {
        if (StringUtils.isBlank(suffix)) {
            return false;
        }
        String lowerSuffix = StringUtils.lowerCase(suffix);
        return getImageExtensions().contains(lowerSuffix) || 
               getVideoExtensions().contains(lowerSuffix) || 
               getAudioExtensions().contains(lowerSuffix) || 
               getDocumentExtensions().contains(lowerSuffix) || 
               getArchiveExtensions().contains(lowerSuffix) || 
               isCodeExtension(lowerSuffix);
    }
}
