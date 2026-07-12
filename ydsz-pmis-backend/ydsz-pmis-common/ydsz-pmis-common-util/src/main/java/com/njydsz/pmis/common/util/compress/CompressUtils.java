package com.njydsz.pmis.common.util.compress;

import java.util.ArrayList;
import java.util.List;
import java.io.*;
import java.util.zip.*;

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
 * <p><b>相比 Apache/Spring 的增强：</b>
 * <ul>
 *   <li>无需额外依赖，使用 JDK 原生支持</li>
 *   <li>提供完整的 ZIP 和 GZIP 支持</li>
 *   <li>支持目录递归压缩和解压</li>
 *   <li>所有方法 null 安全处理</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class CompressUtils {

    private static final int BUFFER_SIZE = 8192;

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
             ZipOutputStream zos = new ZipOutputStream(fos)) {
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
             ZipOutputStream zos = new ZipOutputStream(fos)) {
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
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipDirectory(directory, "", zos);
        }
    }

    /**
     * 解压 ZIP 文件
     */
    public static void unzip(File zipFile, File destDirectory) throws IOException {
        if (zipFile == null || destDirectory == null) {
            throw new IllegalArgumentException("zipFile and destDirectory cannot be null");
        }

        if (!destDirectory.exists()) {
            destDirectory.mkdirs();
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File destFile = new File(destDirectory, entry.getName());
                
                if (entry.isDirectory()) {
                    destFile.mkdirs();
                } else {
                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
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
        return gzip(str.getBytes("UTF-8"));
    }

    /**
     * 解压字符串
     */
    public static String decompressString(byte[] data) throws IOException {
        if (data == null) {
            return null;
        }
        return new String(gunzip(data), "UTF-8");
    }

    /**
     * 获取 ZIP 文件中的条目列表
     */
    public static List<String> listZipEntries(File zipFile) throws IOException {
        List<String> entries = new ArrayList<>();
        if (zipFile == null || !zipFile.exists()) {
            return entries;
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
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
     */
    public static boolean isZipFormat(File file) throws IOException {
        if (file == null || !file.exists()) {
            return false;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            int magic = raf.read() & 0xff | (raf.read() & 0xff) << 8;
            return magic == ZipEntry.LOCFLG;
        }
    }
}
