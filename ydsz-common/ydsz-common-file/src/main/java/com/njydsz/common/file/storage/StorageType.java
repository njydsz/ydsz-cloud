package com.njydsz.common.file.storage;

/**
 * 存储类型 SPI
 *
 * <p>抽象文件存储后端类型常量。支持通过 SPI 扩展自定义存储类型，不再受限于硬编码枚举。 业务方只需实现 {@link IFileStorageProvider} 接口并通过 {@code
 * META-INF/services} 注册即可扩展新的存储后端。
 *
 * <p><b>内置类型：</b>
 *
 * <ul>
 *   <li>{@link #LOCAL} - 本地存储
 *   <li>{@link #ALIYUN} - 阿里云 OSS
 *   <li>{@link #MINIO} - MinIO
 *   <li>{@link #AWS_S3} - Amazon S3
 *   <li>{@link #QINIU} - 七牛云
 *   <li>{@link #TENCENT_COS} - 腾讯云 COS
 *   <li>{@link #HUAWEI_OBS} - 华为云 OBS
 * </ul>
 *
 * <p><b>存储后端兼容性：</b>S3 兼容协议的实现可直接复用 {@link com.njydsz.common.file.storage.platform.S3Storage}， 通过修改
 * endpoint/region 即可对接 MinIO、Ceph、腾讯云 COS 等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class StorageType {

  /** 本地存储（文件系统） */
  public static final String LOCAL = "local";

  /** 阿里云 OSS 存储 */
  public static final String ALIYUN = "aliyun";

  /** MinIO 对象存储 */
  public static final String MINIO = "minio";

  /** Amazon S3 存储（兼容 S3 协议的其他厂商可复用此实现） */
  public static final String AWS_S3 = "aws-s3";

  /** 七牛云存储 */
  public static final String QINIU = "qiniu";

  /** 腾讯云 COS 存储 */
  public static final String TENCENT_COS = "tencent-cos";

  /** 华为云 OBS 存储 */
  public static final String HUAWEI_OBS = "huawei-obs";

  /** 工具类构造器，禁止实例化 */
  private StorageType() {
    throw new UnsupportedOperationException("StorageType 是常量类，禁止实例化");
  }
}
