package com.njydsz.pmis.common.file.storage;

import java.time.Duration;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.njydsz.pmis.common.file.callback.UploadProgressListener;
import com.njydsz.pmis.common.file.domain.ChunkedUploadResult;
import com.njydsz.pmis.common.file.domain.FileStorage;
import com.njydsz.pmis.common.file.domain.PolicyResult;
import com.njydsz.pmis.common.file.domain.UploadCheckpoint;

/**
 * 文件上传器接口
 * <p>
 * 定义文件上传相关的核心操作，包括单文件上传、分片上传、断点续传、前端直传 Policy 等能力。
 * 该接口按 SRP 拆分自 {@link IFileStorage}，便于单独 Mock 与替换。
 * </p>
 *
 * <p><b>分片上传流程：</b></p>
 * <ol>
 *   <li>{@link #initiateChunkedUpload(String, String)} - 初始化分片任务，获取 uploadId</li>
 *   <li>{@link #uploadChunk(String, String, String, int, MultipartFile)} - 上传单个分片（可并行）</li>
 *   <li>{@link #completeChunkedUpload(String, String, String, List)} - 完成分片合并</li>
 * </ol>
 *
 * <p><b>分片大小：</b>默认 5MB（与 S3 协议对齐，可在 {@code FileProperties.chunkSize} 调整）。
 * 分片过小会显著增加请求数量，过大则降低并发度与失败恢复效率。</p>
 *
 * <p><b>断点续传：</b>{@link #initChunkedUploadWithCheckpoint} 在初始化时记录
 * 检查点（Redis 或本地），中断后可调用 {@link #resumeChunkedUpload} 仅上传未完成分片。</p>
 *
 * <p><b>前端直传 Policy：</b>{@link #generateUploadPolicy} 生成临时上传凭证，
 * 前端可绕过服务端直接上传至云存储，减轻服务端带宽压力。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
public interface FileUploader {

    /**
     * 普通文件上传
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName  对象路径，即文件在存储层中的键
     * @param file        待上传的 Spring MultipartFile
     * @return 包含文件元信息的 FileStorage 对象
     */
    FileStorage upload(String bucketName, String objectName, MultipartFile file);

    /**
     * 带上传进度回调的文件上传
     * <p>适用于大文件或弱网络环境，业务层可借此实现上传进度条等功能。
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName  对象路径
     * @param file        待上传的 MultipartFile
     * @param listener    上传进度回调，可传入 null 表示不需要回调
     * @return 包含文件元信息的 FileStorage 对象
     */
    FileStorage upload(String bucketName, String objectName, MultipartFile file, UploadProgressListener listener);

    /**
     * 分片上传第一步：初始化分片任务
     * <p>调用成功后返回 uploadId，业务层需保存此 uploadId 并在后续 uploadChunk / completeChunkedUpload 时传入。
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName 分片合并后的目标对象路径
     * @return 包含 uploadId 等分片任务信息的结果对象
     */
    ChunkedUploadResult initiateChunkedUpload(String bucketName, String objectName);

    /**
     * 分片上传第二步：上传单个分片
     * <p>分片编号 partNumber 必须为正整数且在同一次分片任务内保持唯一。
     * 不同分片可以并行上传，各实现类内部自行处理并发写入。
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName  分片合并后的目标对象路径（必须与 initiateChunkedUpload 时一致）
     * @param uploadId    初始化分片任务时返回的 uploadId
     * @param partNumber  分片编号，必须大于 0
     * @param file        当前分片的文件内容
     */
    void uploadChunk(String bucketName, String objectName, String uploadId, int partNumber, MultipartFile file);

    /**
     * 分片上传第三步：完成分片合并
     * <p>将已上传的所有分片合并为最终对象。
     * 传入的 partNumbers 必须与实际上传的分片编号一致，缺片将导致合并失败。
     * 合并成功后云端会自动清理分片中间数据。
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName 分片合并后的目标对象路径（必须与 initiateChunkedUpload 时一致）
     * @param uploadId    初始化分片任务时返回的 uploadId
     * @param partNumbers 已上传的分片编号列表，编号必须为正整数且连续（允许跳号但不建议）
     */
    void completeChunkedUpload(String bucketName, String objectName, String uploadId, List<Integer> partNumbers);

    /**
     * 生成前端直传 Policy 签名
     * <p>服务端生成上传凭证，前端可凭此凭证直接上传文件到云存储（无需经过服务端中转）。
     * 适用于大文件上传场景，可减轻服务端压力。
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectNamePrefix 对象路径前缀（如 "uploads/"），实际路径由前端生成
     * @param expires    签名过期时间（秒），默认使用配置中的 temporarySignatureExpiry
     * @return 前端直传签名结果，包含 Policy、Signature 等凭证
     */
    PolicyResult generateUploadPolicy(String bucketName, String objectNamePrefix, Integer expires);

    /**
     * 初始化带断点续传的分片上传
     * <p>与 initiateChunkedUpload 类似，但额外记录检查点信息到本地文件。
     * 上传过程中断后，可通过 resumeChunkedUpload 恢复进度。
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName 分片合并后的目标对象路径
     * @param file       待上传的文件（用于计算分片和记录元信息）
     * @return 分片上传检查点信息
     */
    UploadCheckpoint initChunkedUploadWithCheckpoint(String bucketName, String objectName, MultipartFile file);

    /**
     * 断点续传：从检查点恢复分片上传
     * <p>读取检查点信息，自动跳过已上传的分片，只上传剩余分片。
     *
     * @param checkpoint 检查点信息（通常从本地文件读取）
     * @param listener   上传进度回调
     * @return 包含文件元信息的 FileStorage 对象
     */
    FileStorage resumeChunkedUpload(UploadCheckpoint checkpoint, UploadProgressListener listener);

    /**
     * 查询分片上传进度
     * <p>从检查点文件读取上传进度，返回已上传的分片信息。
     *
     * @param bucketName  存储桶名称，传 null 时使用配置默认值
     * @param objectName 对象路径
     * @return 分片上传检查点信息，若不存在则返回 null
     */
    UploadCheckpoint getCheckpoint(String bucketName, String objectName);

    /**
     * 删除分片上传检查点
     * <p>上传成功后调用，清理本地检查点文件。
     *
     * @param checkpoint 检查点信息
     */
    void deleteCheckpoint(UploadCheckpoint checkpoint);

    /**
     * 复制对象
     * <p>在存储桶内复制对象，常用于备份、版本管理、归档等场景。
     *
     * @param srcBucketName  源存储桶名称，传 null 时使用配置默认值
     * @param srcObjectName  源对象路径
     * @param destBucketName 目标存储桶名称，传 null 时使用配置默认值
     * @param destObjectName 目标对象路径
     */
    void copyObject(String srcBucketName, String srcObjectName, String destBucketName, String destObjectName);

    /**
     * 移动/重命名对象
     * <p>本质是复制后删除源对象，不保证原子性。
     *
     * @param srcBucketName  源存储桶名称，传 null 时使用配置默认值
     * @param srcObjectName  源对象路径
     * @param destBucketName 目标存储桶名称，传 null 时使用配置默认值
     * @param destObjectName 目标对象路径
     */
    void moveObject(String srcBucketName, String srcObjectName, String destBucketName, String destObjectName);

    /**
     * 生成上传预签名 URL（用于临时授权上传）
     * <p>默认实现抛出 UnsupportedOperationException，各云存储实现类按需覆盖。
     *
     * @param bucketName     存储桶名称（为 null 时使用默认配置）
     * @param objectName     对象存储键
     * @param expiryDuration 过期时长
     * @return 预签名上传 URL
     * @throws UnsupportedOperationException 如果当前存储后端不支持
     */
    default String generatePresignedUploadUrl(String bucketName, String objectName, Duration expiryDuration) {
        throw new UnsupportedOperationException("Presigned upload URL not supported by this storage backend");
    }
}
