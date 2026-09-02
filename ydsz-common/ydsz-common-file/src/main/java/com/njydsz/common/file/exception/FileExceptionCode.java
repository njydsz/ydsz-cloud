package com.njydsz.common.file.exception;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 文件存储模块异常码枚举
 *
 * <p>采用两段式错误码结构（Fxx 段位 + 五位数字），便于按域分类与日志检索：
 *
 * <ul>
 *   <li>F01*** - 文件操作错误（上传/下载/删除/路径/大小/后缀等）
 *   <li>F02*** - 存储桶错误
 *   <li>F04*** - 配置错误
 *   <li>F07*** - 分片上传错误
 *   <li>F99*** - 未知错误（兜底）
 * </ul>
 *
 * <p><b>稳定性：</b>错误码是业务契约，修改/废弃必须保留向前兼容的 alias， 避免错误码硬编码在客户端代码中后无法平滑升级。
 *
 * <p><b>26.09.01 变更：</b>收敛冗余错误码（28 → 13），合并语义相近的错误码， 降低客户端处理复杂度。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@YdszExceptionCode(module = "file", description = "文件存储")
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
  /** 文件操作失败（下载/删除/拷贝/列举/目录操作/私有链接等） */
  FILE_OPERATE_FAILED("F01006", "file.operate.failed"),
  /** 文件不存在 */
  FILE_NOT_FOUND("F01008", "file.not.found"),
  /** 文件路径非法/为空 */
  FILE_PATH_EMPTY("F01010", "file.path.empty"),
  /** 文件病毒检测命中 */
  FILE_VIRUS_DETECTED("F01014", "file.virus.detected"),
  /** 存储桶错误（创建失败/不存在） */
  BUCKET_ERROR("F02001", "bucket.error"),
  /** 存储配置无效（Endpoint 格式错误/客户端构建失败/域名未配置） */
  CONFIG_INVALID("F04001", "config.invalid"),
  /** 分片上传失败（初始化/完成/并发冲突） */
  MULTIPART_UPLOAD_FAILED("F07001", "multipart.upload.failed"),
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

  /**
   * 返回错误码字符串，用于响应体 {@code code} 字段与日志检索。
   *
   * <p>该值为对外业务契约，客户端可能硬编码判断，禁止随意变更。
   *
   * @return 形如 {@code F01008} 的两段式错误码，非 {@code null}
   */
  @Override
  public String getCode() {
    return code;
  }

  /**
   * 返回国际化消息 key，由异常处理器据此从资源文件解析用户可读文案。
   *
   * <p>若资源文件缺少对应条目，上层通常降级为直接展示 key 本身， 因此新增枚举项时务必同步补充 i18n 资源。
   *
   * @return 形如 {@code file.not.found} 的点分 i18n key，非 {@code null}
   */
  @Override
  public String getKey() {
    return key;
  }
}
