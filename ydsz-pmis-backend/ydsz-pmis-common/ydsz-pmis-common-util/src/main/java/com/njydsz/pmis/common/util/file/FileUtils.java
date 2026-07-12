package com.njydsz.pmis.common.util.file;

import lombok.extern.slf4j.Slf4j;
import com.njydsz.pmis.common.util.string.StringUtils;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * FileUtils - 增强版文件操作工具类
 * 参考：Apache Commons IO, Google Guava Files, Spring FileSystemResource
 * 
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @desc 支持高性能文件读写、文件安全检测、文件哈希计算、批量操作等
 */
@Slf4j
public class FileUtils {
    
    /**
     * 字符常量：斜杠 {@code '/'}
     */
    public static final char SLASH = '/';

    /**
     * 字符常量：反斜杠 {@code '\\'}
     */
    public static final char BACKSLASH = '\\';

    public static final String FILENAME_PATTERN = "[a-zA-Z0-9_\\-\\|\\.\\u4e00-\\u9fa5]+";
    
    /**
     * 文件大小单位常量
     */
    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;
    private static final long TB = GB * 1024L;

    /**
     * 输出指定文件的 byte 数组 (高性能版本 - 使用 NIO)
     *
     * @param filePath 文件路径
     * @param os       输出流
     * @throws IOException IO 异常
     */
    public static void writeBytes(String filePath, OutputStream os) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(os, "outputStream must not be null");
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException(filePath);
        }
        try (InputStream is = Files.newInputStream(path)) {
            is.transferTo(os);
        }
    }

    /**
     * 读取文件所有字节 (高性能版本)
     */
    public static byte[] readAllBytes(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException(filePath);
        }
        return Files.readAllBytes(path);
    }

    /**
     * 读取文件前 N 个字节 (用于读取文件头)
     */
    public static byte[] readHeadBytes(String filePath, int length) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException(filePath);
        }
        
        try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(length);
            fileChannel.read(buffer);
            buffer.flip();
            
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
    }

    /**
     * 读取文件文本内容 (支持多种编码)
     */
    public static String readString(String filePath) throws IOException {
        return readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 读取文件文本内容 (指定编码)
     */
    public static String readString(String filePath, Charset charset) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException(filePath);
        }
        return Files.readString(path, charset);
    }

    /**
     * 写入字节数组到文件 (高性能版本)
     */
    public static void writeBytes(String filePath, byte[] data) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(data, "data must not be null");
        Path path = Paths.get(filePath);
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * 写入文本到文件
     */
    public static void writeString(String filePath, String content) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(content, "content must not be null");
        writeString(filePath, content, StandardCharsets.UTF_8);
    }

    /**
     * 写入文本到文件 (指定编码)
     */
    public static void writeString(String filePath, String content, Charset charset) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(charset, "charset must not be null");
        Path path = Paths.get(filePath);
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, charset, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * 追加字节到文件
     */
    public static void appendBytes(String filePath, byte[] data) throws IOException {
        Path path = Paths.get(filePath);
        Files.write(path, data, StandardOpenOption.APPEND);
    }

    /**
     * 追加文本到文件
     */
    public static void appendString(String filePath, String content) throws IOException {
        appendString(filePath, content, StandardCharsets.UTF_8);
    }

    /**
     * 追加文本到文件 (指定编码)
     */
    public static void appendString(String filePath, String content, Charset charset) throws IOException {
        Path path = Paths.get(filePath);
        Files.writeString(path, content + System.lineSeparator(), charset, StandardOpenOption.APPEND);
    }

    /**
     * 校验并创建对应的目录
     *
     * @param path 目录路径
     */
    public static void mkdirs(String path) {
        if (StringUtils.isBlank(path)) {
            return;
        }
        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            log.error("FileUtils -> mkdirs error for path {}: {}", path, e.getMessage());
        }
    }

    /**
     * 创建父目录
     */
    public static void mkdirsForFile(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return;
        }
        Path path = Paths.get(filePath);
        Path parent = path.getParent();
        if (parent != null) {
            mkdirs(parent.toString());
        }
    }

    /**
     * 删除文件或目录 (支持递归删除)
     *
     * @param pathString 路径
     * @return 删除成功返回 true，否则返回 false
     */
    public static boolean deleteQuietly(String pathString) {
        if (StringUtils.isBlank(pathString)) {
            return false;
        }
        
        Path path = Paths.get(pathString);
        try {
            if (!Files.exists(path)) {
                return true;
            }
            
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.deleteIfExists(path);
            }
            return true;
        } catch (IOException e) {
            log.warn("FileUtils -> deleteQuietly error for path {}: {}", pathString, e.getMessage());
            return false;
        }
    }

    /**
     * 删除文件
     */
    public static boolean deleteFile(String pathString) {
        if (StringUtils.isBlank(pathString)) {
            return false;
        }
        
        Path path = Paths.get(pathString);
        try {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                Files.delete(path);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.warn("FileUtils -> deleteFile error for path {}: {}", pathString, e.getMessage());
            return false;
        }
    }

    /**
     * 删除目录 (仅删除空目录)
     */
    public static boolean deleteDirectory(String pathString) {
        if (StringUtils.isBlank(pathString)) {
            return false;
        }
        
        Path path = Paths.get(pathString);
        try {
            if (Files.exists(path) && Files.isDirectory(path)) {
                Files.delete(path);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.warn("FileUtils -> deleteDirectory error for path {}: {}", pathString, e.getMessage());
            return false;
        }
    }

    /**
     * 清空目录下的所有文件和子目录 (但不删除目录本身)
     */
    public static boolean cleanDirectory(String pathString) {
        if (StringUtils.isBlank(pathString)) {
            return false;
        }
        
        Path path = Paths.get(pathString);
        try {
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                return false;
            }
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    deleteQuietly(entry.toString());
                }
            }
            return true;
        } catch (IOException e) {
            log.warn("FileUtils -> cleanDirectory error for path {}: {}", pathString, e.getMessage());
            return false;
        }
    }

    /**
     * 复制文件
     */
    public static boolean copyFile(String sourcePath, String targetPath) {
        if (StringUtils.isBlank(sourcePath) || StringUtils.isBlank(targetPath)) {
            return false;
        }
        
        try {
            Path source = Paths.get(sourcePath);
            Path target = Paths.get(targetPath);
            
            if (!Files.exists(source)) {
                log.warn("FileUtils -> copyFile source file not exists: {}", sourcePath);
                return false;
            }
            
            mkdirsForFile(targetPath);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("FileUtils -> copyFile error from {} to {}: {}", sourcePath, targetPath, e.getMessage());
            return false;
        }
    }

    /**
     * 复制目录 (递归复制)
     */
    public static boolean copyDirectory(String sourceDir, String targetDir) {
        if (StringUtils.isBlank(sourceDir) || StringUtils.isBlank(targetDir)) {
            return false;
        }
        
        try {
            Path sourcePath = Paths.get(sourceDir);
            Path targetPath = Paths.get(targetDir);
            
            if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
                log.warn("FileUtils -> copyDirectory source directory not exists: {}", sourceDir);
                return false;
            }
            
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path newDir = targetPath.resolve(sourcePath.relativize(dir));
                    Files.createDirectories(newDir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path targetFile = targetPath.resolve(sourcePath.relativize(file));
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            log.error("FileUtils -> copyDirectory error from {} to {}: {}", sourceDir, targetDir, e.getMessage());
            return false;
        }
    }

    /**
     * 移动文件
     */
    public static boolean moveFile(String sourcePath, String targetPath) {
        if (StringUtils.isBlank(sourcePath) || StringUtils.isBlank(targetPath)) {
            return false;
        }
        
        try {
            Path source = Paths.get(sourcePath);
            Path target = Paths.get(targetPath);
            
            if (!Files.exists(source)) {
                log.warn("FileUtils -> moveFile source file not exists: {}", sourcePath);
                return false;
            }
            
            mkdirsForFile(targetPath);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("FileUtils -> moveFile error from {} to {}: {}", sourcePath, targetPath, e.getMessage());
            return false;
        }
    }

    /**
     * 文件重命名
     */
    public static boolean renameFile(String filePath, String newName) {
        if (StringUtils.isBlank(filePath) || StringUtils.isBlank(newName)) {
            return false;
        }
        
        Path path = Paths.get(filePath);
        Path parent = path.getParent();
        Path newPath = parent.resolve(newName);
        
        try {
            Files.move(path, newPath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("FileUtils -> renameFile error for {} to {}: {}", filePath, newName, e.getMessage());
            return false;
        }
    }

    /**
     * 文件名称验证
     *
     * @param filename 文件名称
     * @return true 正常 false 非法
     */
    public static boolean isValidFilename(String filename) {
        return filename != null && filename.matches(FILENAME_PATTERN);
    }

    /**
     * 检查文件是否可下载
     *
     * @param resource 需要下载的文件
     * @return true 正常 false 非法
     */
    public static boolean checkAllowDownload(String resource) {
        // 禁止目录上跳级别
        return StringUtils.isNotEmpty(resource) && !StringUtils.contains(resource, "..");
    }

    /**
     * 检查文件路径是否安全 (防止路径遍历攻击)
     */
    public static boolean isSafePath(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        
        // 检查是否包含路径遍历特征
        if (path.contains("..") || path.contains("~")) {
            return false;
        }
        
        // 检查是否是绝对路径 (根据业务需求决定)
        if (path.startsWith("/") || path.startsWith("\\") || path.matches("^[a-zA-Z]:[/\\\\].*")) {
            return false;
        }
        
        return true;
    }

    /**
     * 获取文件扩展名 (不含点)
     */
    public static String getExtension(String fileName) {
        if (StringUtils.isEmpty(fileName)) {
            return StringUtils.EMPTY;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot != -1 && dot < fileName.length() - 1) {
            return fileName.substring(dot + 1);
        }
        return StringUtils.EMPTY;
    }

    /**
     * 获取不带扩展名的文件名
     */
    public static String getBaseName(String fileName) {
        if (StringUtils.isEmpty(fileName)) {
            return StringUtils.EMPTY;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot != -1 && dot > 0) {
            return fileName.substring(0, dot);
        }
        return fileName;
    }

    /**
     * 获取文件大小 (字节)
     */
    public static long getFileSize(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return 0L;
        }
        
        Path path = Paths.get(filePath);
        try {
            if (Files.exists(path)) {
                return Files.size(path);
            }
        } catch (IOException e) {
            log.error("FileUtils -> getFileSize error for path {}: {}", filePath, e.getMessage());
        }
        return 0L;
    }

    /**
     * 获取文件大小 (格式化字符串)
     */
    public static String getFormattedFileSize(String filePath) {
        return formatFileSize(getFileSize(filePath));
    }

    /**
     * 格式化文件大小
     */
    public static String formatFileSize(long size) {
        if (size < 0) {
            return "0 B";
        }
        
        if (size < KB) {
            return size + " B";
        } else if (size < MB) {
            return String.format("%.2f KB", size / (double) KB);
        } else if (size < GB) {
            return String.format("%.2f MB", size / (double) MB);
        } else if (size < TB) {
            return String.format("%.2f GB", size / (double) GB);
        } else {
            return String.format("%.2f TB", size / (double) TB);
        }
    }

    /**
     * 获取文件最后修改时间
     */
    public static LocalDateTime getLastModifiedTime(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return null;
        }
        
        Path path = Paths.get(filePath);
        try {
            if (Files.exists(path)) {
                FileTime fileTime = Files.getLastModifiedTime(path);
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(fileTime.toMillis()), ZoneId.systemDefault());
            }
        } catch (IOException e) {
            log.error("FileUtils -> getLastModifiedTime error for path {}: {}", filePath, e.getMessage());
        }
        return null;
    }

    /**
     * 获取文件最后修改时间 (格式化字符串)
     */
    public static String getFormattedLastModifiedTime(String filePath) {
        return getFormattedLastModifiedTime(filePath, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 获取文件最后修改时间 (自定义格式)
     */
    public static String getFormattedLastModifiedTime(String filePath, String pattern) {
        LocalDateTime time = getLastModifiedTime(filePath);
        if (time == null) {
            return StringUtils.EMPTY;
        }
        return time.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 检查文件是否存在
     */
    public static boolean exists(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return false;
        }
        return Files.exists(Paths.get(filePath));
    }

    /**
     * 检查是否是目录
     */
    public static boolean isDirectory(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return false;
        }
        return Files.isDirectory(Paths.get(filePath));
    }

    /**
     * 检查是否是文件
     */
    public static boolean isFile(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return false;
        }
        return Files.isRegularFile(Paths.get(filePath));
    }

    /**
     * 检查文件是否可读
     */
    public static boolean isReadable(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return false;
        }
        return Files.isReadable(Paths.get(filePath));
    }

    /**
     * 检查文件是否可写
     */
    public static boolean isWritable(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return false;
        }
        return Files.isWritable(Paths.get(filePath));
    }

    /**
     * 下载文件名重新编码
     *
     * @param fileName 文件名
     * @return 编码后的文件名
     */
    public static String setFileDownloadHeader(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    }

    /**
     * 计算文件 MD5 值
     */
    public static String md5(String filePath) {
        return calculateHash(filePath, "MD5");
    }

    /**
     * 计算文件 SHA-1 值
     */
    public static String sha1(String filePath) {
        return calculateHash(filePath, "SHA-1");
    }

    /**
     * 计算文件 SHA-256 值
     */
    public static String sha256(String filePath) {
        return calculateHash(filePath, "SHA-256");
    }

    /**
     * 计算文件哈希值
     */
    public static String calculateHash(String filePath, String algorithm) {
        if (StringUtils.isBlank(filePath)) {
            return StringUtils.EMPTY;
        }
        
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return StringUtils.EMPTY;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream is = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("FileUtils -> calculateHash error for path {}: {}", filePath, e.getMessage());
            return StringUtils.EMPTY;
        }
    }

    /**
     * 计算字节数组的 MD5
     */
    public static String md5(byte[] data) {
        return calculateHash(data, "MD5");
    }

    /**
     * 计算字节数组的哈希值
     */
    public static String calculateHash(byte[] data, String algorithm) {
        if (data == null || data.length == 0) {
            return StringUtils.EMPTY;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(data);
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("FileUtils -> calculateHash error: {}", e.getMessage());
            return StringUtils.EMPTY;
        }
    }

    /**
     * 获取目录下所有文件
     */
    public static List<String> listFiles(String directoryPath) {
        return listFiles(directoryPath, null);
    }

    /**
     * 获取目录下所有匹配扩展名的文件
     */
    public static List<String> listFiles(String directoryPath, String extension) {
        if (StringUtils.isBlank(directoryPath)) {
            return Collections.emptyList();
        }
        
        Path directory = Paths.get(directoryPath);
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return Collections.emptyList();
        }
        
        List<String> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    if (extension == null || extension.equalsIgnoreCase(getExtension(entry.toString()))) {
                        files.add(entry.toString());
                    }
                }
            }
        } catch (IOException e) {
            log.error("FileUtils -> listFiles error for path {}: {}", directoryPath, e.getMessage());
        }
        return files;
    }

    /**
     * 递归获取目录下所有文件
     */
    public static List<String> listFilesRecursively(String directoryPath) {
        return listFilesRecursively(directoryPath, null);
    }

    /**
     * 递归获取目录下所有匹配扩展名的文件
     */
    public static List<String> listFilesRecursively(String directoryPath, String extension) {
        if (StringUtils.isBlank(directoryPath)) {
            return Collections.emptyList();
        }
        
        Path directory = Paths.get(directoryPath);
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return Collections.emptyList();
        }
        
        List<String> files = new ArrayList<>();
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (extension == null || extension.equalsIgnoreCase(getExtension(file.toString()))) {
                        files.add(file.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("FileUtils -> listFilesRecursively error for path {}: {}", directoryPath, e.getMessage());
        }
        return files;
    }

    /**
     * 获取目录下的所有子目录
     */
    public static List<String> listDirectories(String directoryPath) {
        if (StringUtils.isBlank(directoryPath)) {
            return Collections.emptyList();
        }
        
        Path directory = Paths.get(directoryPath);
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return Collections.emptyList();
        }
        
        List<String> directories = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, Files::isDirectory)) {
            for (Path entry : stream) {
                directories.add(entry.toString());
            }
        } catch (IOException e) {
            log.error("FileUtils -> listDirectories error for path {}: {}", directoryPath, e.getMessage());
        }
        return directories;
    }

    /**
     * 检查文件是否在指定目录下
     */
    public static boolean isSubDirectory(String parentPath, String childPath) {
        if (StringUtils.isBlank(parentPath) || StringUtils.isBlank(childPath)) {
            return false;
        }
        
        Path parent = Paths.get(parentPath).normalize();
        Path child = Paths.get(childPath).normalize();
        
        return child.startsWith(parent);
    }

    /**
     * 获取临时文件路径
     */
    public static String getTempFilePath(String prefix, String suffix) {
        try {
            Path tempFile = Files.createTempFile(prefix, suffix);
            return tempFile.toString();
        } catch (IOException e) {
            log.error("FileUtils -> getTempFilePath error: {}", e.getMessage());
            return StringUtils.EMPTY;
        }
    }

    /**
     * 获取临时目录路径
     */
    public static String getTempDirectoryPath(String prefix) {
        try {
            Path tempDir = Files.createTempDirectory(prefix);
            return tempDir.toString();
        } catch (IOException e) {
            log.error("FileUtils -> getTempDirectoryPath error: {}", e.getMessage());
            return StringUtils.EMPTY;
        }
    }

    /**
     * 规范化文件路径 (统一分隔符等)
     */
    public static String normalizePath(String path) {
        if (StringUtils.isBlank(path)) {
            return StringUtils.EMPTY;
        }
        
        return Paths.get(path).normalize().toString();
    }

    /**
     * 连接路径片段
     */
    public static String joinPaths(String... paths) {
        if (paths == null || paths.length == 0) {
            return StringUtils.EMPTY;
        }
        
        Path result = Paths.get(paths[0]);
        for (int i = 1; i < paths.length; i++) {
            result = result.resolve(paths[i]);
        }
        return result.normalize().toString();
    }

    /**
     * 获取相对路径
     */
    public static String getRelativePath(String basePath, String targetPath) {
        if (StringUtils.isBlank(basePath) || StringUtils.isBlank(targetPath)) {
            return StringUtils.EMPTY;
        }
        
        Path base = Paths.get(basePath);
        Path target = Paths.get(targetPath);
        
        return base.relativize(target).toString();
    }

    /**
     * 检查文件是否被占用
     */
    public static boolean isFileLocked(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return false;
        }
        
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return false;
        }
        
        try {
            InputStream is = Files.newInputStream(path);
            is.close();
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * 等待文件释放 (轮询检查)
     */
    public static boolean waitForFileRelease(String filePath, long timeoutMillis) {
        if (StringUtils.isBlank(filePath)) {
            return false;
        }
        
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (!isFileLocked(filePath)) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 下载网络文件到客户端（作为附件）
     *
     * @param url      文件网络地址
     * @param path     URL 中用于提取文件名的路径前缀
     * @param response HttpServletResponse，用于向客户端写入文件流
     */
    public static void downloadFile(String url, String path, HttpServletResponse response) {
        HttpURLConnection conn = null;
        try {
            URL httpUrl = URI.create(url).toURL();
            conn = (HttpURLConnection) httpUrl.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(100000);
            conn.setReadTimeout(200000);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.connect();

            byte[] buffer = new byte[4096];
            int len;
            response.reset();
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Cache-Control", "no-cache");
            response.setContentType("application/octet-stream");
            // 从 URL 路径末端提取文件名
            String fileName = url.replaceAll(path + "/", "");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()));
            try (InputStream in = conn.getInputStream();
                 ServletOutputStream out = response.getOutputStream()) {
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
        } catch (Exception e) {
            log.error("FileUtils -> downloadFile failed for url {}: {}", url, e.getMessage(), e);
            throw new RuntimeException("文件下载失败：" + url, e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 从 URL 下载文件到本地
     *
     * @param url 文件 URL
     * @param targetPath 目标文件路径
     * @param timeoutMillis 超时时间（毫秒）
     */
    public static void downloadFileToLocal(String url, String targetPath, long timeoutMillis) throws IOException {
        if (StringUtils.isBlank(url) || StringUtils.isBlank(targetPath)) {
            throw new IllegalArgumentException("URL 和目标路径不能为空");
        }
        
        mkdirsForFile(targetPath);

        URL httpUrl = URI.create(url).toURL();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) httpUrl.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout((int) Math.min(timeoutMillis, Integer.MAX_VALUE));
            conn.setReadTimeout((int) Math.min(timeoutMillis, Integer.MAX_VALUE));
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP 响应码：" + responseCode);
            }

            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(targetPath)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
