package com.njydsz.pmis.common.file.exception;

import com.njydsz.pmis.common.exception.enums.ExceptionCode;

/**
 * 文件存储模块异常码枚举
 * <p>
 * 采用两段式错误码结构（Fxx 段位 + 五位数字），便于按域分类与日志检索：
 * <ul>
 *   <li>F01*** - 文件操作错误（上传/下载/删除/路径/大小/后缀等）</li>
 *   <li>F02*** - 存储桶错误（创建桶/桶不存在）</li>
 *   <li>F03*** - 目录错误（创建目录/目录不存在）</li>
 *   <li>F04*** - 配置错误（Endpoint 格式错误/客户端构建失败）</li>
 *   <li>F05*** - 私有链接错误（domain 未配置/链接生成失败）</li>
 *   <li>F06*** - 范围下载错误（不支持 Range 请求）</li>
 *   <li>F07*** - 分片上传错误（初始化/上传分片/完成分片失败）</li>
 *   <li>F99*** - 未知错误（兜底）</li>
 * </ul>
 *
 * <p><b>稳定性：</b>错误码是业务契约，修改/废弃必须保留向前兼容的 alias，
 * 避免错误码硬编码在客户端代码中后无法平滑升级。</p>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
public enum FileExceptionCode implements ExceptionCode {

    /** 上传文件为空 */
    FILE_EMPTY("F01001", "file.empty"),
    /** 文件扩展名不在允许列表中 */
    FILE_SUFFIX_NOT_ALLOWED("F01002", "file.suffix.not.allowed"),
    /** 文件大小超出限制 */
    FILE_SIZE_EXCEEDED("F01003", "file.size.exceeded"),
    /** 文件名无效 */
    FILE_NAME_INVALID("F01004", "file.name.invalid"),
    /** 文件上传失败 */
    FILE_UPLOAD_FAILED("F01005", "file.upload.failed"),
    /** 文件删除失败 */
    FILE_DELETE_FAILED("F01006", "file.delete.failed"),
    /** 文件下载失败 */
    FILE_DOWNLOAD_FAILED("F01007", "file.download.failed"),
    /** 文件不存在 */
    FILE_NOT_FOUND("F01008", "file.not.found"),
    /** 文件路径非法 */
    FILE_PATH_ILLEGAL("F01009", "file.path.illegal"),
    /** 文件路径为空 */
    FILE_PATH_EMPTY("F01010", "file.path.empty"),
    /** 对象拷贝失败 */
    OBJECT_COPY_FAILED("F01011", "object.copy.failed"),
    /** 获取对象元数据失败 */
    OBJECT_METADATA_GET_FAILED("F01012", "object.metadata.get.failed"),
    /** 列举对象失败 */
    OBJECT_LIST_FAILED("F01013", "object.list.failed"),
    /** 存储桶创建失败 */
    BUCKET_CREATE_FAILED("F02001", "bucket.create.failed"),
    /** 存储桶不存在 */
    BUCKET_NOT_FOUND("F02002", "bucket.not.found"),
    /** 目录创建失败 */
    FOLDER_CREATE_FAILED("F03001", "folder.create.failed"),
    /** 目录不存在 */
    FOLDER_NOT_FOUND("F03002", "folder.not.found"),
    /** 存储配置无效 */
    STORAGE_CONFIG_INVALID("F04001", "storage.config.invalid"),
    /** 存储客户端构建失败 */
    STORAGE_CLIENT_BUILD_FAILED("F04002", "storage.client.build.failed"),
    /** 私有链接生成失败 */
    PRIVATE_URL_GENERATE_FAILED("F05001", "private.url.generate.failed"),
    /** 私有链接域名未配置 */
    PRIVATE_URL_DOMAIN_NOT_CONFIGURED("F05002", "private.url.domain.not.configured"),
    /** 不支持范围下载 */
    RANGE_DOWNLOAD_NOT_SUPPORTED("F06001", "range.download.not.supported"),
    /** 分片上传失败 */
    MULTIPART_UPLOAD_FAILED("F07001", "multipart.upload.failed"),
    /** 分片上传初始化失败 */
    MULTIPART_UPLOAD_INIT_FAILED("F07002", "multipart.upload.init.failed"),
    /** 分片上传完成失败 */
    MULTIPART_UPLOAD_COMPLETE_FAILED("F07003", "multipart.upload.complete.failed"),
    /** 上传并发冲突（同一文件被并发上传） */
    UPLOAD_CONCURRENT_CONFLICT("F07004", "upload.concurrent.conflict"),
    /** 未知错误（兜底） */
    UNKNOWN("F99999", "unknown.error");

    /** 错误码（业务契约，不应轻易变更） */
    private final String code;
    /** 国际化 key */
    private final String key;

    FileExceptionCode(String code, String key) {
        this.code = code;
        this.key = key;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getKey() {
        return key;
    }
}
