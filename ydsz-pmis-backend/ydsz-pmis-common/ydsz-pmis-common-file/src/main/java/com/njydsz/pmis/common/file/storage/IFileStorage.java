package com.njydsz.pmis.common.file.storage;

import com.njydsz.pmis.common.file.storage.platform.CosStorage;
import com.njydsz.pmis.common.file.storage.platform.LocalStorage;
import com.njydsz.pmis.common.file.storage.platform.MinioStorage;
import com.njydsz.pmis.common.file.storage.platform.ObsStorage;
import com.njydsz.pmis.common.file.storage.platform.OssStorage;
import com.njydsz.pmis.common.file.storage.platform.QiniuStorage;
import com.njydsz.pmis.common.file.storage.platform.S3Storage;

/**
 * 文件存储统一抽象接口
 * <p>
 * 抽象所有存储后端（local / minio / s3 / oss / cos / qiniu / obs）的同一操作语义，
 * 各实现类负责将本接口调用翻译为对应云厂商 SDK 的原生 API。业务层只需面向
 * {@link IFileStorage} 编程，切换存储后端无需修改业务代码。
 * </p>
 *
 * <p><b>核心语义约定：</b></p>
 * <ul>
 *   <li>{@code bucketName} 传 {@code null} 时使用配置默认值（由 {@code FileProperties.bucket} 决定）</li>
 *   <li>{@code objectName} 即存储键（Key），其格式由业务层自行规划，建议使用
 *       "业务前缀/日期/文件名" 结构（如 {@code user/202601/upload/abc.jpg}）</li>
 *   <li>分片上传三步曲：{@code initiateChunkedUpload} → {@code uploadChunk}（可并行）→ {@code completeChunkedUpload}</li>
 *   <li>分片大小：默认 5MB（与 S3 协议对齐），可在 {@code FileProperties.chunkSize} 调整</li>
 * </ul>
 *
 * <p>本接口继承三个职责单一的子接口，按 SRP 拆分：</p>
 * <ul>
 *   <li>{@link FileUploader} - 上传相关方法</li>
 *   <li>{@link FileDownloader} - 下载相关方法</li>
 *   <li>{@link FileManager} - 管理相关方法（删除、重命名等）</li>
 * </ul>
 *
 * <p><b>异常体系：</b>所有实现层异常均封装为 {@code BusinessException}，错误码参见 {@link FileExceptionCode}。
 * 上传/下载失败时不应直接抛出 SDK 异常，需转换为稳定的业务错误码。</p>
 *
 * <p><b>并发一致性：</b>分片上传需保证 partNumber 唯一性；complete 时需校验所有分片
 * ETag 与云端一致，避免出现 "孤儿分片"。</p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see IFileStorageProvider
 * @see LocalStorage
 * @see MinioStorage
 * @see S3Storage
 * @see OssStorage
 * @see CosStorage
 * @see QiniuStorage
 * @see ObsStorage
 */
public interface IFileStorage extends FileUploader, FileDownloader, FileManager {

    /**
     * 分片信息记录（Java 17+ record）
     * <p>用于分片上传时携带分片标识信息。云存储通过 ETag 验证分片完整性。
     *
     * @param partNumber 分片编号（从 1 开始）
     * @param eTag       分片 ETag（云存储用于标识分片的唯一值）
     * @param size       分片大小（字节）
     */
    record PartInfo(int partNumber, String eTag, long size) {
    }
}
