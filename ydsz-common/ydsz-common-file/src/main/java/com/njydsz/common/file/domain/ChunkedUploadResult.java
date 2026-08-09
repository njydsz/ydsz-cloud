package com.njydsz.common.file.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分片上传初始化结果
 * <p>在 initiateChunkedUpload 调用成功后返回，携带分片任务标识与元数据。
 *
 * <p>业务层需要保存 uploadId，并在后续 uploadChunk / completeChunkedUpload 时传入。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkedUploadResult {

    /**
     * 分片合并后的目标对象路径
     */
    private String objectName;

    /**
     * 目标存储桶名称
     */
    private String bucketName;

    /**
     * 分片上传任务唯一标识
     * <p>每次调用 initiateChunkedUpload 生成新的 uploadId，
     * 用于关联同一次分片任务中的所有分片。
     */
    private String uploadId;

    /**
     * 预估总分片数（供进度计算参考，实际以完成时为准）
     */
    private int totalParts;

    /**
     * 文件总字节数
     */
    private long totalBytes;
}
