package com.njydsz.pmis.message.dto.core;

import lombok.Data;

import java.util.List;

/**
 * 交互式卡片消息 DTO（P1-1）。
 *
 * <p>支持跨通道的交互式卡片消息,包含标题、内容、按钮列表,
 * 各通道按自身能力渲染为对应格式（钉钉 actionCard / 企微 textcard / 站内卡片）。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Data
public class CardMessageDTO {

    /** 卡片标题 */
    private String title;

    /** 卡片内容（支持 Markdown） */
    private String content;

    /** 卡片图标标识 */
    private String icon;

    /** 按钮列表 */
    private List<CardButton> buttons;

    /** 跳转 URL（单按钮时直接跳转） */
    private String actionUrl;

    /** 跳转按钮文案 */
    private String actionText;

    /** 通知级别 */
    private String level;

    /**
     * 卡片按钮定义。
     */
    @Data
    public static class CardButton {
        /** 按钮文案 */
        private String text;
        /** 按钮跳转 URL */
        private String url;
        /** 按钮样式: primary / default / danger / warning */
        private String style;
    }
}
