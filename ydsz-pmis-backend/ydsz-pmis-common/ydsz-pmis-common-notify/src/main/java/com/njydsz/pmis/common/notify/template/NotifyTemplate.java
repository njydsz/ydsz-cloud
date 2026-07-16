package com.njydsz.pmis.common.notify.template;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.njydsz.pmis.common.notify.enums.NotifyChannel;

/**
 * 通知消息模板定义
 *
 * <p>描述一个可复用的通知模板，包含模板 ID、名称、内容（支持 Velocity / SpEL / MessageFormat 等多种模板语法）、适用渠道及变量定义。
 *
 * <p><b>示例：</b>
 * <pre>{@code
 * NotifyTemplate template = new NotifyTemplate()
 *     .setTemplateId("order_shipped")
 *     .setName("订单发货通知")
 *     .setContent("尊敬的 ${userName}，您的订单 ${orderNo} 已发货。")
 *     .setChannelType(NotifyChannel.EMAIL)
 *     .setVariable("userName", "用户名")
 *     .setVariable("orderNo", "订单号");
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class NotifyTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模板唯一标识 */
    private String templateId;

    /** 模板名称 */
    private String name;

    /** 模板内容（支持 Velocity / SpEL / MessageFormat 等多种模板语法） */
    private String content;

    /** 适用渠道 */
    private NotifyChannel channelType;

    /** 模板变量定义，key=变量名，value=变量描述 */
    private transient Map<String, String> variables = new HashMap<>();

    public NotifyTemplate() {
    }

    public NotifyTemplate(String templateId, String name, String content, NotifyChannel channelType) {
        this.templateId = templateId;
        this.name = name;
        this.content = content;
        this.channelType = channelType;
    }

    /**
     * 设置模板 ID
     *
     * @param templateId 模板唯一标识
     * @return this
     */
    public NotifyTemplate setTemplateId(String templateId) {
        this.templateId = templateId;
        return this;
    }

    /**
     * 设置模板名称
     *
     * @param name 模板名称
     * @return this
     */
    public NotifyTemplate setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * 设置模板内容
     *
     * @param content 模板内容（支持 Velocity / SpEL / MessageFormat 等多种模板语法）
     * @return this
     */
    public NotifyTemplate setContent(String content) {
        this.content = content;
        return this;
    }

    /**
     * 设置适用渠道
     *
     * @param channelType 通知渠道
     * @return this
     */
    public NotifyTemplate setChannelType(NotifyChannel channelType) {
        this.channelType = channelType;
        return this;
    }

    /**
     * 添加模板变量定义
     *
     * @param name        变量名
     * @param description 变量描述
     * @return this
     */
    public NotifyTemplate setVariable(String name, String description) {
        this.variables.put(name, description);
        return this;
    }

    /**
     * 获取模板ID
     *
     * @return 模板唯一标识
     */
    public String getTemplateId() {
        return templateId;
    }

    /**
     * 获取模板名称
     *
     * @return 模板名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取模板内容
     *
     * @return 模板内容字符串
     */
    public String getContent() {
        return content;
    }

    /**
     * 获取适用渠道
     *
     * @return 通知渠道枚举
     */
    public NotifyChannel getChannelType() {
        return channelType;
    }

    /**
     * 获取模板变量定义
     *
     * @return 变量映射，key=变量名，value=变量描述
     */
    public Map<String, String> getVariables() {
        return variables;
    }

    /**
     * 设置模板变量定义
     *
     * @param variables 变量映射，key=变量名，value=变量描述
     */
    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
    }

    @Override
    public String toString() {
        return "NotifyTemplate{" +
                "templateId='" + templateId + '\'' +
                ", name='" + name + '\'' +
                ", channelType=" + channelType +
                ", variables=" + variables.keySet() +
                '}';
    }
}
