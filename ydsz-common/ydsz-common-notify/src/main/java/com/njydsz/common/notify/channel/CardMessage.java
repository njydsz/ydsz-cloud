package com.njydsz.common.notify.channel;

import java.util.List;

/**
 * IM 渠道卡片消息定义（P3-4）
 *
 * <p>支持钉钉、飞书、企业微信等 IM 渠道的富文本/卡片消息发送。 包含标题、内容、按钮、跳转链接等交互元素。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CardMessage {

  /** 卡片标题 */
  private String title;

  /** 卡片正文内容（支持 Markdown 语法） */
  private String content;

  /** 卡片按钮列表 */
  private List<Button> buttons;

  /** 跳转 URL */
  private String jumpUrl;

  /** 卡片图片 URL */
  private String cardImage;

  /** 卡片主题颜色（如 blue、green、red） */
  private String theme = "blue";

  /** 消息类型 */
  private MessageType messageType = MessageType.CARD;

  /** 消息类型枚举 */
  public enum MessageType {
    TEXT,
    MARKDOWN,
    CARD,
    INTERACTIVE
  }

  /** 卡片按钮定义 */
  public static class Button {

    private final String text;
    private final String url;
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

    public String getText() {
      return text;
    }

    public String getUrl() {
      return url;
    }

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

  // Getters and Setters

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public List<Button> getButtons() {
    return buttons;
  }

  public void setButtons(List<Button> buttons) {
    this.buttons = buttons;
  }

  public String getJumpUrl() {
    return jumpUrl;
  }

  public void setJumpUrl(String jumpUrl) {
    this.jumpUrl = jumpUrl;
  }

  public String getCardImage() {
    return cardImage;
  }

  public void setCardImage(String cardImage) {
    this.cardImage = cardImage;
  }

  public String getTheme() {
    return theme;
  }

  public void setTheme(String theme) {
    this.theme = theme;
  }

  public MessageType getMessageType() {
    return messageType;
  }

  public void setMessageType(MessageType messageType) {
    this.messageType = messageType;
  }
}
