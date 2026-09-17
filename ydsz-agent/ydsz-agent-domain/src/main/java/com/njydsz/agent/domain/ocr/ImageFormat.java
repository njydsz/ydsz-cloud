package com.njydsz.agent.domain.ocr;

/**
 * 图片格式枚举
 *
 * <p>定义 OCR 引擎支持的输入图片格式。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public enum ImageFormat {

  /** PNG 格式 */
  PNG("png", "image/png"),

  /** JPEG 格式 */
  JPEG("jpeg", "image/jpeg"),

  /** BMP 格式 */
  BMP("bmp", "image/bmp"),

  /** TIFF 格式 */
  TIFF("tiff", "image/tiff");

  private final String extension;
  private final String mimeType;

  ImageFormat(String extension, String mimeType) {
    this.extension = extension;
    this.mimeType = mimeType;
  }

  public String getExtension() {
    return extension;
  }

  public String getMimeType() {
    return mimeType;
  }

  /**
   * 根据 MIME 类型查找对应的图片格式枚举。
   *
   * @param mimeType MIME 类型字符串（如 "image/png"）
   * @return 对应枚举，未找到返回 null
   */
  public static ImageFormat fromMimeType(String mimeType) {
    if (mimeType == null) {
      return null;
    }
    for (ImageFormat format : values()) {
      if (format.mimeType.equalsIgnoreCase(mimeType)) {
        return format;
      }
    }
    return null;
  }

  /**
   * 根据文件扩展名查找对应的图片格式枚举。
   *
   * @param extension 文件扩展名（不含点，如 "png"、"jpg"）
   * @return 对应枚举，未找到返回 null
   */
  public static ImageFormat fromExtension(String extension) {
    if (extension == null) {
      return null;
    }
    String ext = extension.toLowerCase().replace(".", "");
    // jpg 作为 JPEG 别名
    if ("jpg".equals(ext)) {
      return JPEG;
    }
    for (ImageFormat format : values()) {
      if (format.extension.equals(ext)) {
        return format;
      }
    }
    return null;
  }
}
