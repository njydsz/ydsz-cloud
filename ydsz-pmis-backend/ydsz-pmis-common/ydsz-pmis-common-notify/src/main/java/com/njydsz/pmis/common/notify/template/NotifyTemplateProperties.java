package com.njydsz.pmis.common.notify.template;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知模板引擎配置属性
 *
 * <p>配置项：
 * <ul>
 *   <li>pmis.notify.template.enabled - 是否启用模板引擎（默认 true）</li>
 *   <li>pmis.notify.template.base-path - 模板文件基础路径（默认 classpath:notify-templates/）</li>
 *   <li>pmis.notify.template.cache-enabled - 是否启用模板缓存（默认 true）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * 
 * @since 1.0.0
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "pmis.notify.template")
public class NotifyTemplateProperties {

    /** 是否启用模板引擎 */
    private boolean enabled = true;

    /** 模板文件基础路径（支持 classpath: 前缀） */
    private String basePath = "classpath:notify-templates/";

    /** 是否启用模板缓存 */
    private boolean cacheEnabled = true;
}
