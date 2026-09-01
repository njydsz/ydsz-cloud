package com.njydsz.agent.domain.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 多模态消息内容
 *
 * <p>支持文本和图片两种内容类型，用于 Vision 模型的输入。 每条消息可由多个内容段落组成（如：文本 + 图片 + 文本）。
 *
 * <p><b>线程安全</b>：不可变值对象，字段 final，可安全跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class MessageContent implements Serializable {

  /** 低分辨率单图 Token 估算值 */
  private static final double IMAGE_TOKEN_ESTIMATE = 85;

  /** 图片 Token 转字符数比率（与 tokenCharRatio 估算口径一致） */
  private static final double IMAGE_TOKEN_CHAR_RATIO = 2.5;

  private static final long serialVersionUID = 1L;

  /** 内容段落列表（按显示顺序排列） */
  private final List<ContentPart> parts;

  public MessageContent(List<ContentPart> parts) {
    this.parts = parts != null ? List.copyOf(parts) : List.of();
  }

  public List<ContentPart> getParts() {
    return parts;
  }

  /**
   * 创建仅包含文本的内容。
   *
   * @param text 文本内容
   * @return MessageContent 实例
   */
  public static MessageContent text(String text) {
    return new MessageContent(List.of(ContentPart.text(text)));
  }

  /**
   * 创建文本 + 图片的多模态内容。
   *
   * @param text 文本描述
   * @param imageUrl 图片 URL（http(s):// 或 data:image/...）
   * @return MessageContent 实例
   */
  public static MessageContent textAndImage(String text, String imageUrl) {
    return new MessageContent(List.of(ContentPart.text(text), ContentPart.image(imageUrl)));
  }

  /**
   * 创建多张图片的内容。
   *
   * @param imageUrls 图片 URL 列表
   * @return MessageContent 实例
   */
  public static MessageContent images(List<String> imageUrls) {
    List<ContentPart> imgParts = imageUrls.stream().map(ContentPart::image).toList();
    return new MessageContent(imgParts);
  }

  /**
   * 判断是否包含图片内容。
   *
   * @return true 表示至少一个图片段落
   */
  public boolean hasImages() {
    // ContentPart 为 record，访问器为 type()（record 组件方法）
    return parts.stream().anyMatch(p -> "image_url".equals(p.type()));
  }

  /**
   * 判断内容是否为空。
   *
   * @return true 表示无段落或所有段落为空
   */
  public boolean isEmpty() {
    return parts.isEmpty();
  }

  /**
   * 估算多模态内容的 Token 字符数。
   *
   * <p>文本段落按实际字符数计算；图片段落按固定 85 Token估算。
   *
   * @return 估算 Token 字符数
   */
  public int estimateTokenChars() {
    int chars = 0;
    for (ContentPart part : parts) {
      if (part.isText() && part.text() != null) {
        chars += part.text().length();
      } else if (part.isImage()) {
            // 低分辨率单图约 85 Token，按 tokenCharRatio 反算字符数
        chars += IMAGE_TOKEN_ESTIMATE * IMAGE_TOKEN_CHAR_RATIO;
      }
    }
    return chars;
  }

  /**
   * 内容段落（文本或图片）
   *
   * @param type 内容类型（text / image_url）
   * @param text 文本内容（type=text 时有效）
   * @param imageUrl 图片 URL（type=image_url 时有效）
   */
  public record ContentPart(String type, String text, String imageUrl) implements Serializable {

    /**
     * 创建文本段落。
     *
     * @param text 文本内容
     * @return 文本段落
     */
    public static ContentPart text(String text) {
      return new ContentPart("text", text, null);
    }

    /**
     * 创建图片段落。
     *
     * @param url 图片 URL（http(s):// 或 data:image/... 内联格式）
     * @return 图片段落
     */
    public static ContentPart image(String url) {
      Objects.requireNonNull(url, "imageUrl 不能为 null");
      return new ContentPart("image_url", null, url);
    }

    /**
     * 判断是否为文本段落。
     *
     * @return true 表示文本类型
     */
    public boolean isText() {
      return "text".equals(type);
    }

    /**
     * 判断是否为图片段落。
     *
     * @return true 表示图片类型
     */
    public boolean isImage() {
      return "image_url".equals(type);
    }
  }
}
