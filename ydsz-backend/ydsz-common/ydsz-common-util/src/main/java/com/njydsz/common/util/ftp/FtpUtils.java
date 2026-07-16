package com.njydsz.common.util.ftp;

/**
 * FTP 文件操作工具类
 *
 * <p>基于 Apache Commons Net 实现 FTP 文件上传、下载、删除、列表等操作。
 * 支持主动/被动模式切换、自动重试、编码处理等功能。
 *
 * <p>使用方式：
 * <pre>{@code
 * FtpUtils.uploadFile("192.168.1.1", 21, "admin", "password", "/remote/path/file.txt", localFile);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FtpUtils {

    private static final int DEFAULT_CONNECT_TIMEOUT = 30000;
    private static final int DEFAULT_DATA_TIMEOUT = 30000;
    private static final int DEFAULT_RETRY_COUNT = 3;
    private static final String DEFAULT_CONTROL_ENCODING = "UTF-8";
    private static final String SERVER_CHARSET = "ISO8859-1";
    private static final String LOCAL_CHARSET = "UTF-8";

    private FtpUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean uploadFile(FtpConfig config, String filePath, String filename, InputStream input) {
        return uploadFile(config, filePath, filename, input, DEFAULT_RETRY_COUNT);
    }

    public static boolean uploadFile(FtpConfig config, String filePath, String filename, InputStream input, int retryCount) {
        return uploadFile(config.getHost(), config.getPort(), config.getUsername(), config.getPassword(),
                config.getBasePath(), filePath, filename, input, retryCount);
    }

    public static boolean uploadFile(String host, int port, String username, String password,
                                     String basePath, String filePath, String filename, InputStream input) {
        return uploadFile(host, port, username, password, basePath, filePath, filename, input, DEFAULT_RETRY_COUNT);
    }

    public static boolean uploadFile(String host, int port, String username, String password,
                                     String basePath, String filePath, String filename, InputStream input, int retryCount) {
        FTPClient ftp = null;
        try {
            ftp = initFtpClient(host, port, username, password);
            
            String fullPath = normalizePath(basePath + filePath);
            if (!createDirectories(ftp, fullPath)) {
                log.error("FtpUtils -> 创建目录 {} 失败", fullPath);
                return false;
            }

            String encodedFilename = encodeFilename(filename);
            boolean success = ftp.storeFile(encodedFilename, input);
            if (!success) {
                log.error("FtpUtils -> 上传文件 {} 失败，错误码：{}", filename, ftp.getReplyCode());
            }
            return success;
        } catch (IOException e) {
            log.error("FtpUtils -> 上传文件 {} 异常：{}", filename, e.getMessage());
            return false;
        } finally {
            closeQuietly(ftp, input);
        }
    }

    public static boolean downloadFile(String host, int port, String username, String password,
                                       String remotePath, String fileName, String localPath) {
        return downloadFile(host, port, username, password, remotePath, fileName, localPath, DEFAULT_RETRY_COUNT);
    }

    public static boolean downloadFile(String host, int port, String username, String password,
                                       String remotePath, String fileName, String localPath, int retryCount) {
        FTPClient ftp = null;
        try {
            ftp = initFtpClient(host, port, username, password);
            
            if (!ftp.changeWorkingDirectory(remotePath)) {
                log.error("FtpUtils -> 切换目录 {} 失败", remotePath);
                return false;
            }

            FTPFile[] files = ftp.listFiles(fileName);
            if (files == null || files.length == 0) {
                log.warn("FtpUtils -> 远程文件 {} 不存在", fileName);
                return false;
            }

            File localFile = new File(localPath, fileName);
            File parentDir = localFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    log.error("FtpUtils -> 创建本地目录 {} 失败", parentDir.getAbsolutePath());
                    return false;
                }
            }
            
            String encodedFilename = encodeFilename(fileName);
            try (OutputStream os = new FileOutputStream(localFile)) {
                boolean success = ftp.retrieveFile(encodedFilename, os);
                if (!success) {
                    log.error("FtpUtils -> 下载文件 {} 失败，错误码：{}", fileName, ftp.getReplyCode());
                }
                return success;
            }
        } catch (IOException e) {
            log.error("FtpUtils -> 下载文件 {} 异常：{}", fileName, e.getMessage());
            return false;
        } finally {
            closeQuietly(ftp, null);
        }
    }

    public static boolean deleteFile(String host, int port, String username, String password,
                                     String remotePath, String fileName) {
        FTPClient ftp = null;
        try {
            ftp = initFtpClient(host, port, username, password);
            
            if (!ftp.changeWorkingDirectory(remotePath)) {
                log.error("FtpUtils -> 切换目录 {} 失败", remotePath);
                return false;
            }

            String encodedFilename = encodeFilename(fileName);
            boolean success = ftp.deleteFile(encodedFilename);
            if (!success) {
                log.error("FtpUtils -> 删除文件 {} 失败，错误码：{}", fileName, ftp.getReplyCode());
            }
            return success;
        } catch (IOException e) {
            log.error("FtpUtils -> 删除文件 {} 异常：{}", fileName, e.getMessage());
            return false;
        } finally {
            closeQuietly(ftp, null);
        }
    }

    public static boolean renameFile(String host, int port, String username, String password,
                                     String remotePath, String oldName, String newName) {
        FTPClient ftp = null;
        try {
            ftp = initFtpClient(host, port, username, password);
            
            if (!ftp.changeWorkingDirectory(remotePath)) {
                log.error("FtpUtils -> 切换目录 {} 失败", remotePath);
                return false;
            }

            String encodedOldName = encodeFilename(oldName);
            String encodedNewName = encodeFilename(newName);
            boolean success = ftp.rename(encodedOldName, encodedNewName);
            if (!success) {
                log.error("FtpUtils -> 重命名文件 {} -> {} 失败，错误码：{}", oldName, newName, ftp.getReplyCode());
            }
            return success;
        } catch (IOException e) {
            log.error("FtpUtils -> 重命名文件 {} -> {} 异常：{}", oldName, newName, e.getMessage());
            return false;
        } finally {
            closeQuietly(ftp, null);
        }
    }

    public static List<String> listFileNames(String host, int port, String username, String password,
                                             String remotePath) {
        return listFileNames(host, port, username, password, remotePath, null);
    }

    public static List<String> listFileNames(String host, int port, String username, String password,
                                             String remotePath, String suffix) {
        FTPClient ftp = null;
        try {
            ftp = initFtpClient(host, port, username, password);
            
            if (!ftp.changeWorkingDirectory(remotePath)) {
                log.error("FtpUtils -> 切换目录 {} 失败", remotePath);
                return new ArrayList<>();
            }

            FTPFile[] files = ftp.listFiles();
            List<String> fileNames = new ArrayList<>();
            if (files != null) {
                for (FTPFile file : files) {
                    if (file.isFile()) {
                        String fileName = decodeFilename(file.getName());
                        if (suffix == null || fileName.endsWith(suffix)) {
                            fileNames.add(fileName);
                        }
                    }
                }
            }
            return fileNames;
        } catch (IOException e) {
            log.error("FtpUtils -> 列出文件列表异常：{}", e.getMessage());
            return new ArrayList<>();
        } finally {
            closeQuietly(ftp, null);
        }
    }

    public static List<FtpFileInfo> listFiles(String host, int port, String username, String password,
                                              String remotePath) {
        FTPClient ftp = null;
        try {
            ftp = initFtpClient(host, port, username, password);
            
            if (!ftp.changeWorkingDirectory(remotePath)) {
                log.error("FtpUtils -> 切换目录 {} 失败", remotePath);
                return new ArrayList<>();
            }

            FTPFile[] files = ftp.listFiles();
            List<FtpFileInfo> fileInfoList = new ArrayList<>();
            if (files != null) {
                for (FTPFile file : files) {
                    if (file.isFile()) {
                        fileInfoList.add(new FtpFileInfo(
                            decodeFilename(file.getName()),
                            file.getSize(),
                            file.getTimestamp()
                        ));
                    }
                }
            }
            return fileInfoList;
        } catch (IOException e) {
            log.error("FtpUtils -> 列出文件详情异常：{}", e.getMessage());
            return new ArrayList<>();
        } finally {
            closeQuietly(ftp, null);
        }
    }

    public static boolean makeDirectory(String host, int port, String username, String password,
                                        String remotePath, String dirName) {
        FTPClient ftp = null;
        try {
            ftp = initFtpClient(host, port, username, password);
            
            if (!ftp.changeWorkingDirectory(remotePath)) {
                log.error("FtpUtils -> 切换目录 {} 失败", remotePath);
                return false;
            }

            String encodedDirName = encodeFilename(dirName);
            boolean success = ftp.makeDirectory(encodedDirName);
            if (!success) {
                log.error("FtpUtils -> 创建目录 {} 失败，错误码：{}", dirName, ftp.getReplyCode());
            }
            return success;
        } catch (IOException e) {
            log.error("FtpUtils -> 创建目录 {} 异常：{}", dirName, e.getMessage());
            return false;
        } finally {
            closeQuietly(ftp, null);
        }
    }

    public static boolean removeDirectory(String host, int port, String username, String password,
                                          String remotePath, String dirName) {
        FTPClient ftp = null;
        try {
            ftp = initFtpClient(host, port, username, password);
            
            if (!ftp.changeWorkingDirectory(remotePath)) {
                log.error("FtpUtils -> 切换目录 {} 失败", remotePath);
                return false;
            }

            String encodedDirName = encodeFilename(dirName);
            boolean success = ftp.removeDirectory(encodedDirName);
            if (!success) {
                log.error("FtpUtils -> 删除目录 {} 失败，错误码：{}", dirName, ftp.getReplyCode());
            }
            return success;
        } catch (IOException e) {
            log.error("FtpUtils -> 删除目录 {} 异常：{}", dirName, e.getMessage());
            return false;
        } finally {
            closeQuietly(ftp, null);
        }
    }

    public static boolean fileExists(String host, int port, String username, String password,
                                     String remotePath, String fileName) {
        FTPClient ftp = null;
        try {
            ftp = initFtpClient(host, port, username, password);
            
            if (!ftp.changeWorkingDirectory(remotePath)) {
                return false;
            }

            FTPFile[] files = ftp.listFiles(fileName);
            return files != null && files.length > 0;
        } catch (IOException e) {
            log.error("FtpUtils -> 检查文件 {} 是否存在异常：{}", fileName, e.getMessage());
            return false;
        } finally {
            closeQuietly(ftp, null);
        }
    }

    public static long getFileSize(String host, int port, String username, String password,
                                   String remotePath, String fileName) {
        FTPClient ftp = null;
        try {
            ftp = initFtpClient(host, port, username, password);
            
            if (!ftp.changeWorkingDirectory(remotePath)) {
                return -1;
            }

            FTPFile[] files = ftp.listFiles(fileName);
            if (files != null && files.length > 0) {
                return files[0].getSize();
            }
            return -1;
        } catch (IOException e) {
            log.error("FtpUtils -> 获取文件 {} 大小异常：{}", fileName, e.getMessage());
            return -1;
        } finally {
            closeQuietly(ftp, null);
        }
    }

    private static FTPClient initFtpClient(String host, int port, String username, String password) throws IOException {
        FTPClient ftp = new FTPClient();
        ftp.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
        ftp.setSoTimeout(DEFAULT_DATA_TIMEOUT);
        ftp.setControlEncoding(DEFAULT_CONTROL_ENCODING);
        
        ftp.connect(host, port);
        
        int reply = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            throw new SocketException("FTP server refused connection, reply code: " + reply);
        }
        
        if (!ftp.login(username, password)) {
            ftp.disconnect();
            throw new SocketException("FTP login failed");
        }
        
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        ftp.enterLocalPassiveMode();
        ftp.setKeepAlive(true);
        
        return ftp;
    }

    private static boolean createDirectories(FTPClient ftp, String path) throws IOException {
        if (path == null || path.trim().isEmpty()) {
            return true;
        }
        
        String[] directories = path.split("/");
        for (String dir : directories) {
            if (dir.isEmpty()) {
                continue;
            }
            
            String encodedDir = encodeFilename(dir);
            if (!ftp.changeWorkingDirectory(encodedDir)) {
                if (!ftp.makeDirectory(encodedDir)) {
                    return false;
                }
                ftp.changeWorkingDirectory(encodedDir);
            }
        }
        return true;
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        path = path.replaceAll("\\\\+", "/");
        path = path.replaceAll("/+", "/");
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    private static String encodeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        try {
            return new String(filename.getBytes(LOCAL_CHARSET), SERVER_CHARSET);
        } catch (UnsupportedEncodingException e) {
            return filename;
        }
    }

    private static String decodeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        try {
            return new String(filename.getBytes(SERVER_CHARSET), LOCAL_CHARSET);
        } catch (UnsupportedEncodingException e) {
            return filename;
        }
    }

    private static void closeQuietly(FTPClient ftp, Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            log.warn("关闭流失败：{}", e.getMessage());
        }
        
        if (ftp != null && ftp.isConnected()) {
            try {
                ftp.logout();
            } catch (IOException e) {
                log.warn("FTP logout 失败：{}", e.getMessage());
            }
            try {
                ftp.disconnect();
            } catch (IOException e) {
                log.warn("FTP disconnect 失败：{}", e.getMessage());
            }
        }
    }

    public static class FtpFileInfo {
        private final String name;
        private final long size;
        private final Calendar timestamp;

        public FtpFileInfo(String name, long size, Calendar timestamp) {
            this.name = name;
            this.size = size;
            this.timestamp = timestamp;
        }

        public String getName() {
            return name;
        }

        public long getSize() {
            return size;
        }

        public Calendar getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "FtpFileInfo{name='" + name + "', size=" + size + ", timestamp=" + 
                   (timestamp != null ? timestamp.getTime() : "null") + "}";
        }
    }
}
