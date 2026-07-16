package com.njydsz.common.file.domain;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.common.json.annotation.JsonField;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分片上传检查点信息
 * <p>记录分片上传的进度，支持断点续传。
 * 当上传中断后，可通过读取检查点信息恢复上传进度。
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadCheckpoint {

    /**
     * 上传任务唯一标识
     */
    private String taskId;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 对象路径
     */
    private String objectName;

    /**
     * 分片上传ID（云存储返回）
     */
    private String uploadId;

    /**
     * 文件总大小（字节）
     */
    private Long totalSize;

    /**
     * 文件原始名称
     */
    private String fileName;

    /**
     * 文件内容类型
     */
    private String contentType;

    /**
     * 分片大小（字节）
     */
    private Long partSize;

    /**
     * 已上传的分片信息
     */
    private List<UploadedPart> uploadedParts;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 最后修改时间
     */
    private LocalDateTime lastModifyTime;

    /**
     * 检查点文件路径
     */
    private String checkpointFilePath;

    /**
     * 已上传字节数
     */
    private Long uploadedBytes;

    /**
     * 已上传分片数
     */
    private Integer uploadedPartsCount;

    /**
     * 整个文件的 MD5 校验值（用于合并完成后校验文件完整性）
     */
    private String fileMd5;

    /**
     * 分片上传过程中累积的文件 MD5（十六进制字符串）
     * <p>每上传一个分片就通过 MessageDigest.update 累积计算，
     * 合并完成后直接使用该值与 fileMd5 对比，无需重新下载整个文件。
     * 若为 null 则回退到重新下载的旧逻辑（兼容旧版本）。
     */
    private String accumulatedMd5Hex;

    /**
     * 计算上传进度百分比
     * <p>该方法为计算属性，不参与序列化</p>
     *
     * @return 进度百分比（0-100）
     */
    @JsonField(ignore = true)
    public int getProgressPercent() {
        if (totalSize == null || totalSize == 0) {
            return 0;
        }
        return (int) ((uploadedBytes != null ? uploadedBytes : 0) * 100 / totalSize);
    }

    /**
     * 已上传分片信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadedPart {

        /**
         * 分片编号（从1开始）
         */
        private Integer partNumber;

        /**
         * 分片大小（字节）
         */
        private Long size;

        /**
         * 分片 ETag（云存储返回）
         */
        private String eTag;

        /**
         * 本地分片文件路径
         */
        private String localFilePath;

        /**
         * 是否已上传完成
         */
        private Boolean uploaded;

        /**
         * 分片数据的 MD5 校验值
         */
        private String chunkMd5;
    }

    /**
     * 校验数据的 MD5 是否与预期值一致
     *
     * @param data         数据字节数组
     * @param expectedMd5  预期的 MD5 值（十六进制字符串，不区分大小写）
     * @return true 表示校验通过，false 表示不匹配
     */
    public static boolean validateMd5(byte[] data, String expectedMd5) {
        if (expectedMd5 == null || expectedMd5.isEmpty()) {
            return true;
        }
        if (data == null || data.length == 0) {
            return false;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            String actualMd5 = bytesToHex(digest);
            return actualMd5.equalsIgnoreCase(expectedMd5);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 计算字节数组的 MD5（十六进制小写字符串）
     *
     * @param data 数据字节数组
     * @return MD5 字符串
     */
    public static String calculateMd5(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return bytesToHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}