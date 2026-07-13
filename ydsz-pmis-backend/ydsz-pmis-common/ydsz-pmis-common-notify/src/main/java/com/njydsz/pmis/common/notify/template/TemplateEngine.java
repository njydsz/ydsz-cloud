package com.njydsz.pmis.common.notify.template;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 通知模板引擎接口
 *
 * <p>提供模板注册和渲染能力，支持基于模板 ID 渲染消息内容。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public interface TemplateEngine {

    /**
     * 根据模板 ID 和变量渲染模板
     *
     * @param templateId 模板 ID
     * @param variables  模板变量
     * @return 渲染后的内容
     * @throws IllegalArgumentException 如果模板不存在
     */
    String render(String templateId, Map<String, Object> variables);

    /**
     * 注册模板
     *
     * @param template 模板定义
     */
    void register(NotifyTemplate template);

    /**
     * 判断是否包含指定模板
     *
     * @param templateId 模板 ID
     * @return 是否存在
     */
    boolean hasTemplate(String templateId);

    /**
     * 获取模板定义
     *
     * @param templateId 模板 ID
     * @return 模板定义，不存在时返回 null
     */
    NotifyTemplate getTemplate(String templateId);

    /**
     * 批量注册模板
     *
     * @param templateList 模板列表
     */
    default void registerAll(Collection<NotifyTemplate> templateList) {
        if (templateList == null || templateList.isEmpty()) {
            return;
        }
        for (NotifyTemplate template : templateList) {
            register(template);
        }
    }

    /**
     * 移除模板
     *
     * @param templateId 模板 ID
     */
    default void unregister(String templateId) {
        // 默认空实现，子类可覆盖
    }

    /**
     * 获取所有已注册的模板
     *
     * @return 模板 Map
     */
    default Map<String, NotifyTemplate> getAllTemplates() {
        return Collections.emptyMap();
    }
}
