package com.njydsz.common.notify.channel;

import java.util.List;

/**
 * IM 渠道卡片消息定义（P3-4）
 *
 * <p>支持钉钉、飞书、企业微信等 IM 渠道的富文本/卡片消息发送。 包含标题、内容、按钮、跳转链接等交互元素。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class CardMessage {

  /** 卡片标题。 */
  private String title;

  /** 卡片正文内容（支持 Markdown 语法渲染）。 */
  private String content;

  /** 卡片按钮列表，用于交互式卡片中用户点击触发动作。 */
  private List<Button> buttons;

  /** 点击卡片整区域时的跳转 URL。 */
  private String jumpUrl;

  /** 卡片展示用图片 URL。 */
  private String cardImage;

  /** 卡片主题颜色（如 blue、green、red），影响 IM 卡片左上角色条。 */
  private String theme = "blue";

  /** 消息类型，决定 IM 渠道如何渲染此卡片。 */
  private MessageType messageType = MessageType.CARD;

  /** 消息类型枚举，决定 IM 渠道的渲染方式。 */
  public enum MessageType {
    /** 纯文本消息，仅展示标题与内容。 */
    TEXT,
    /** Markdown 消息，正文按 Markdown 语法渲染。 */
    MARKDOWN,
    /** 卡片消息（默认），按 IM 卡片模板展示。 */
    CARD,
    /** 交互式消息，带按钮列表，用户可点击按钮触发操作。 */
    INTERACTIVE
  }

  /** 卡片按钮定义，用于交互式卡片中展示可点击按钮。 */
  public static class Button {

    /** 按钮显示文本。 */
    private final String text;

    /** 点击按钮后的跳转 URL。 */
    private final String url;

    /** 按钮动作类型（如 open_url、callback）。 */
    private final String action;

    /**
     * 构造按钮
     *
     * @param text 按钮文本
     * @param url 跳转 URL
     * @param action 动作类型（如 open_url、callback）
     */
    public Button(String text, String url, String action) {
      this.text = text;
      this.url = url;
      this.action = action != null ? action : "open_url";
    }

    /** 获取按钮显示文本。
     * @return 按钮文本，不会为 null
     */
    public String getText() {
      return text;
    }

    /** 获取按钮点击后的跳转 URL。
     * @return 跳转 URL
     */
    public String getUrl() {
      return url;
    }

    /** 获取按钮动作类型（如 open_url、callback）。
     * @return 动作类型字符串
     */
    public String getAction() {
      return action;
    }
  }

  /**
   * 创建纯文本卡片
   *
   * @param title 标题
   * @param content 内容
   * @return 卡片消息
   */
  public static CardMessage text(String title, String content) {
    CardMessage msg = new CardMessage();
    msg.title = title;
    msg.content = content;
    msg.messageType = MessageType.TEXT;
    return msg;
  }

  /**
   * 创建 Markdown 卡片
   *
   * @param title 标题
   * @param content Markdown 内容
   * @return 卡片消息
   */
  public static CardMessage markdown(String title, String content) {
    CardMessage msg = new CardMessage();
    msg.title = title;
    msg.content = content;
    msg.messageType = MessageType.MARKDOWN;
    return msg;
  }

  /**
   * 创建交互卡片
   *
   * @param title 标题
   * @param content 内容
   * @param buttons 按钮列表
   * @return 卡片消息
   */
  public static CardMessage interactive(String title, String content, List<Button> buttons) {
    CardMessage msg = new CardMessage();
    msg.title = title;
    msg.content = content;
    msg.buttons = buttons;
    msg.messageType = MessageType.INTERACTIVE;
    return msg;
  }

  // ==================== Getters and Setters ====================

  /** 获取卡片标题。
   * @return 卡片标题，未设置时返回 {@code null}
   */
  public String getTitle() {
    return title;
  }

  /** 设置卡片标题。
   * @param title 卡片标题，允许为 {@code null}
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /** 获取卡片正文内容（支持 Markdown 语法）。
   * @return 卡片正文内容，未设置时返回 {@code null}
   */
  public String getContent() {
    return content;
  }

  /** 设置卡片正文内容。
   * @param content 卡片正文内容，允许为 {@code null}
   */
  public void setContent(String content) {
    this.content = content;
  }

  /** 获取卡片按钮列表。
   * @return 按钮列表，未设置时返回 {@code null}
   */
  public List<Button> getButtons() {
    return buttons;
  }

  /** 设置卡片按钮列表。
   * @param buttons 按钮列表，允许为 {@code null}
   */
  public void setButtons(List<Button> buttons) {
    this.buttons = buttons;
  }

  /** 获取点击卡片整区域时的跳转 URL。
   * @return 跳转 URL，未设置时返回 {@code null}
   */
  public String getJumpUrl() {
    return jumpUrl;
  }

  /** 设置卡片整区域跳转 URL。
   * @param jumpUrl 跳转 URL，允许为 {@code null}
   */
  public void setJumpUrl(String jumpUrl) {
    this.jumpUrl = jumpUrl;
  }

  /** 获取卡片展示用图片 URL。
   * @return 图片 URL，未设置时返回 {@code null}
   */
  public String getCardImage() {
    return cardImage;
  }

  /** 设置卡片展示用图片 URL。
   * @param cardImage 图片 URL，允许为 {@code null}
   */
  public void setCardImage(String cardImage) {
    this.cardImage = cardImage;
  }

  /** 获取卡片主题颜色（如 blue、green、red）。
   * @return 主题颜色字符串，默认 "blue"
   */
  public String getTheme() {
    return theme;
  }

  /** 设置卡片主题颜色。
   * @param theme 主题颜色，允许为 {@code null}
   */
  public void setTheme(String theme) {
    this.theme = theme;
  }

  /** 获取消息类型。
   * @return 消息类型，默认 {@link MessageType#CARD}
   */
  public MessageType getMessageType() {
    return messageType;
  }

  /** 设置消息类型。
   * @param messageType 消息类型，允许为 {@code null}
   */
  public void setMessageType(MessageType messageType) {
    this.messageType = messageType;
  }
}
