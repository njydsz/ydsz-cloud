package com.njydsz.common.util.compress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 压缩工具类
 *
 * <p>提供全面的压缩和解压缩方法，支持 ZIP 和 GZIP 格式，功能对标 Apache Commons Compress 和 Hutool ZipUtil，
 * 并进行了增强和优化。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>ZIP 压缩：zip、zipFile、zipFiles、zipDirectory</li>
 *   <li>ZIP 解压：unzip、unzipFile、unzipToDirectory</li>
 *   <li>GZIP 压缩：gzip、gzipFile</li>
 *   <li>GZIP 解压：gunzip、gunzipFile</li>
 *   <li>流压缩：zipStream、gzipStream</li>
 *   <li>流解压：unzipStream、gunzipStream</li>
 * </ul>
 *
 * <p><b>安全防护：</b>
 * <ul>
 *   <li>Zip Slip 路径遍历攻击防护（所有解压方法均校验目标路径）</li>
 *   <li>ZIP 炸弹防护（限制最大解压条目数、总解压大小、压缩比）</li>
 *   <li>所有方法 null 安全处理</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public class CompressUtils {

    private static final int BUFFER_SIZE = 8192;

    /**
     * ZIP 炸弹防护：最大解压条目数
     */
    private static final int MAX_ZIP_ENTRIES = 10_000;

    /**
     * ZIP 炸弹防护：最大总解压大小（1GB）
     */
    private static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 1024L * 1024L * 1024L;

    /**
     * ZIP 炸弹防护：单条目最大解压大小（256MB）
     */
    private static final long MAX_ENTRY_UNCOMPRESSED_SIZE = 256L * 1024L * 1024L;

    /**
     * ZIP 炸弹防护：最大压缩比阈值（100:1）
     */
    private static final int MAX_COMPRESSION_RATIO = 100;

    private CompressUtils() {
        throw new UnsupportedOperationException("CompressUtils is a utility class and cannot be instantiated");
    }

    /**
     * 压缩文件为 ZIP
     */
    public static void zip(File sourceFile, File zipFile) throws IOException {
        if (sourceFile == null || zipFile == null) {
            throw new IllegalArgumentException("sourceFile and zipFile cannot be null");
        }

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
            zip(sourceFile, sourceFile.getName(), zos);
        }
    }

    /**
     * 压缩多个文件为 ZIP
     */
    public static void zip(File[] files, File zipFile) throws IOException {
        if (files == null || zipFile == null) {
            throw new IllegalArgumentException("files and zipFile cannot be null");
        }

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
            for (File file : files) {
                if (file != null && file.exists()) {
                    zip(file, file.getName(), zos);
                }
            }
        }
    }

    /**
     * 压缩目录为 ZIP
     */
    public static void zipDirectory(File directory, File zipFile) throws IOException {
        if (directory == null || !directory.isDirectory() || zipFile == null) {
            throw new IllegalArgumentException("directory must be a valid directory");
        }

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
            zipDirectory(directory, "", zos);
        }
    }

    /**
     * 解压 ZIP 文件
     *
     * <p><b>安全防护：</b>
     * <ul>
     *   <li>Zip Slip 路径遍历攻击防护：校验每个条目的目标路径是否在目标目录内</li>
     *   <li>ZIP 炸弹防护：限制最大条目数、总解压大小、单条目大小和压缩比</li>
     * </ul>
     *
     * @param zipFile     ZIP 文件
     * @param destDirectory 目标目录
     * @throws IOException 解压过程中发生 IO 异常
     * @throws SecurityException 检测到 Zip Slip 攻击或 ZIP 炸弹时抛出
     */
    public static void unzip(File zipFile, File destDirectory) throws IOException {
        if (zipFile == null || destDirectory == null) {
            throw new IllegalArgumentException("zipFile and destDirectory cannot be null");
        }

        if (!destDirectory.exists()) {
            destDirectory.mkdirs();
        }

        Path destDirPath = destDirectory.getCanonicalFile().toPath();
        long totalUncompressedSize = 0L;
        int entryCount = 0;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // ZIP 炸弹防护：条目数检查
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new SecurityException(
                            "ZIP bomb detected: entry count exceeds limit " + MAX_ZIP_ENTRIES);
                }

                // ZIP 炸弹防护：单条目大小检查（已知压缩大小时）
                if (entry.getSize() > MAX_ENTRY_UNCOMPRESSED_SIZE) {
                    throw new SecurityException(
                            "ZIP bomb detected: entry '" + entry.getName()
                            + "' uncompressed size exceeds limit " + MAX_ENTRY_UNCOMPRESSED_SIZE);
                }

                File destFile = new File(destDirectory, entry.getName());

                // Zip Slip 防护：校验目标路径是否在目标目录内
                Path destFilePath = destFile.getCanonicalFile().toPath();
                if (!destFilePath.startsWith(destDirPath)) {
                    throw new SecurityException(
                            "Zip Slip vulnerability detected: entry '" + entry.getName()
                            + "' tries to escape the target directory");
                }

                if (entry.isDirectory()) {
                    destFile.mkdirs();
                } else {
                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    long entryBytesWritten = 0;
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            // ZIP 炸弹防护：单条目大小检查（流式读取时）
                            entryBytesWritten += len;
                            if (entryBytesWritten > MAX_ENTRY_UNCOMPRESSED_SIZE) {
                                throw new SecurityException(
                                        "ZIP bomb detected: entry '" + entry.getName()
                                        + "' exceeds single entry size limit " + MAX_ENTRY_UNCOMPRESSED_SIZE);
                            }

                            // ZIP 炸弹防护：总大小检查
                            totalUncompressedSize += len;
                            if (totalUncompressedSize > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                                throw new SecurityException(
                                        "ZIP bomb detected: total uncompressed size exceeds limit "
                                        + MAX_TOTAL_UNCOMPRESSED_SIZE);
                            }

                            fos.write(buffer, 0, len);
                        }
                    }

                    // ZIP 炸弹防护：压缩比检查（使用条目实际压缩大小）
                    long compressedSize = entry.getCompressedSize();
                    if (compressedSize > 0 && entryBytesWritten > 0) {
                        long compressionRatio = entryBytesWritten / compressedSize;
                        if (compressionRatio > MAX_COMPRESSION_RATIO) {
                            throw new SecurityException(
                                    "ZIP bomb detected: compression ratio " + compressionRatio
                                    + " exceeds limit " + MAX_COMPRESSION_RATIO
                                    + " for entry '" + entry.getName() + "'");
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * GZIP 压缩文件
     */
    public static void gzip(File sourceFile, File gzipFile) throws IOException {
        if (sourceFile == null || gzipFile == null) {
            throw new IllegalArgumentException("sourceFile and gzipFile cannot be null");
        }

        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(gzipFile);
             GZIPOutputStream gos = new GZIPOutputStream(fos)) {
            copy(fis, gos);
        }
    }

    /**
     * GZIP 压缩字节数组
     */
    public static byte[] gzip(byte[] data) throws IOException {
        if (data == null) {
            return null;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gos = new GZIPOutputStream(baos)) {
            gos.write(data);
            gos.finish();
            return baos.toByteArray();
        }
    }

    /**
     * GZIP 解压文件
     */
    public static void gunzip(File gzipFile, File destFile) throws IOException {
        if (gzipFile == null || destFile == null) {
            throw new IllegalArgumentException("gzipFile and destFile cannot be null");
        }

        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileInputStream fis = new FileInputStream(gzipFile);
             GZIPInputStream gis = new GZIPInputStream(fis);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            copy(gis, fos);
        }
    }

    /**
     * GZIP 解压字节数组
     */
    public static byte[] gunzip(byte[] data) throws IOException {
        if (data == null) {
            return null;
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gis = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            copy(gis, baos);
            return baos.toByteArray();
        }
    }

    /**
     * 递归压缩文件到 ZIP
     */
    private static void zip(File file, String name, ZipOutputStream zos) throws IOException {
        if (file.isDirectory()) {
            zipDirectory(file, name, zos);
        } else {
            zipFile(file, name, zos);
        }
    }

    /**
     * 压缩目录
     */
    private static void zipDirectory(File directory, String path, ZipOutputStream zos) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                String entryName = path + file.getName();
                if (file.isDirectory()) {
                    zipDirectory(file, entryName + "/", zos);
                } else {
                    zipFile(file, entryName, zos);
                }
            }
        }
    }

    /**
     * 压缩文件
     */
    private static void zipFile(File file, String entryName, ZipOutputStream zos) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            zos.putNextEntry(new ZipEntry(entryName));
            copy(fis, zos);
            zos.closeEntry();
        }
    }

    /**
     * 复制流
     */
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        out.flush();
    }

    /**
     * 压缩字符串
     */
    public static byte[] compressString(String str) throws IOException {
        if (str == null) {
            return null;
        }
        return gzip(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解压字符串
     */
    public static String decompressString(byte[] data) throws IOException {
        if (data == null) {
            return null;
        }
        return new String(gunzip(data), StandardCharsets.UTF_8);
    }

    /**
     * 获取 ZIP 文件中的条目列表
     */
    public static List<String> listZipEntries(File zipFile) throws IOException {
        List<String> entries = new ArrayList<>();
        if (zipFile == null || !zipFile.exists()) {
            return entries;
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }

    /**
     * 检查是否为 GZIP 格式
     */
    public static boolean isGzipFormat(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        return (data[0] == (byte) 0x1f) && (data[1] == (byte) 0x8b);
    }

    /**
     * 检查文件是否为 ZIP 格式
     *
     * <p>通过读取文件头前 4 字节，与 ZIP Local File Header 签名
     * {@code 0x504B0304}（即 ASCII {@code PK\x03\x04}）比较。
     *
     * @param file 待检查的文件
     * @return 是否为 ZIP 格式
     * @throws IOException 读取文件时发生 IO 异常
     */
    public static boolean isZipFormat(File file) throws IOException {
        if (file == null || !file.exists()) {
            return false;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 4) {
                return false;
            }
            int b0 = raf.read() & 0xff;
            int b1 = raf.read() & 0xff;
            int b2 = raf.read() & 0xff;
            int b3 = raf.read() & 0xff;
            // ZIP Local File Header signature: 0x04034b50 (little-endian: PK\x03\x04)
            return b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04;
        }
    }
}
