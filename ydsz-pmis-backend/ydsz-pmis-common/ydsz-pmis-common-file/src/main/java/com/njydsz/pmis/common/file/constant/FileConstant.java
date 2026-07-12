package com.njydsz.pmis.common.file.constant;

/**
 * 全局公共常量
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @date 2024/6/7 11:12
 */
public interface FileConstant {

    /**
     * 路径目录分隔符
     */
    String DIR_SPLIT = "/";

    /**
     * 字符串分隔符
     */
    String STRING_SPLIT = ",";

    /**
     * 后缀分隔符
     */
    String SUFFIX_SPLIT = ".";

    /**
     * 目录默认类型
     */
    String DEFAULT_DIR_TYPE = "dir";

    /**
     * 设置存储桶访问权限
     */
    String ACCESS_PRIVATE = "Private";

    String ACCESS_PUBLIC = "Public";

    String ACCESS_CUSTOM = "Custom";

    /**
     * 默认树顶级id
     */
    String ROOT_PARENT_ID = "0";

    String X_REQUESTED_WITH = "X-Requested-With";

    String XML_HTTP_REQUEST = "XMLHttpRequest";

    /**
     * 存储类型-本地
     */
    String FILE_TYPE_LOCAL = "local";

    /**
     * 云存储类型-oss
     */
    String FILE_TYPE_OSS = "oss";

    /**
     * 云存储类型-cos
     */
    String FILE_TYPE_COS = "cos";

    /**
     * 云存储类型-七牛
     */
    String FILE_TYPE_QINIU = "qiniu";

    /**
     * 云存储类型-S3
     */
    String FILE_TYPE_S3 = "s3";

    /**
     * 云存储类型-Minio
     */
    String FILE_TYPE_MINIO = "minio";

    /**
     * 本地目录映射
     */
    String LOCAL_DIRECTORY_MAPPING = "/uploads/";

}
