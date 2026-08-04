package com.njydsz.project.server.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 项目模块配置属性。
 *
 * <p>统一管理项目模块的配置项，支持 IDE 自动补全和校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "ydsz.project")
@Valid
public class ProjectProperties {

    /**
     * 是否启用项目模块自动配置
     */
    private Boolean enabled = true;

    /**
     * 项目定义缓存 TTL（分钟）
     */
    @Min(1)
    @Max(1440)
    private Integer cacheDefinitionTtlMinutes = 30;

    /**
     * 项目本地缓存最大条目数
     */
    @Min(100)
    @Max(10000)
    private Integer cacheMaxSize = 1000;

    /**
     * 项目立项默认初始阶段
     */
    private String defaultInitialStage = "PRE_INITIATION";

    /**
     * 项目立项默认初始状态
     */
    private String defaultStatus = "DRAFT";

    /**
     * 项目立项默认等级（A/B/C/D）
     */
    private String defaultLevel = "C";

    /**
     * 阶段推进是否需要门审
     */
    private Boolean gateReviewRequired = false;

    /**
     * 查询结果缓存 TTL（秒）
     */
    @Min(10)
    @Max(300)
    private Integer queryCacheTtlSeconds = 60;

    /**
     * 导出 Excel 最大行数限制
     */
    @Min(1000)
    @Max(100000)
    private Integer exportMaxRows = 10000;

    /**
     * 项目附件最大上传大小（MB）
     */
    @Min(1)
    @Max(500)
    private Integer attachmentMaxSizeMb = 50;

    /**
     * 是否启用 Outbox 事件发布
     */
    private Boolean outboxEnabled = false;

    /**
     * 是否启用项目通知
     */
    private Boolean notifyEnabled = true;

    /**
     * 缓存配置子属性
     */
    private CacheConfig cache = new CacheConfig();

    /**
     * 通知配置子属性
     */
    private NotifyConfig notify = new NotifyConfig();

    /**
     * 项目缓存配置子属性。
     *
     * <p>对应 {@code ydsz.project.cache.*} 前缀，控制项目定义的本地缓存
     * 过期时间与容量上限，防止缓存膨胀挤占内存。
     */
    @Data
    public static class CacheConfig {
        /**
         * 项目定义缓存 TTL（分钟）
         */
        @Min(1)
        @Max(1440)
        private Integer definitionTtlMinutes = 30;

        /**
         * 项目本地缓存最大条目数
         */
        @Min(100)
        @Max(10000)
        private Integer maxSize = 1000;
    }

    /**
     * 项目通知配置子属性。
     *
     * <p>对应 {@code ydsz.project.notify.*} 前缀，控制项目生命周期关键节点
     * （立项创建、阶段变更、关闭、门审提醒）是否触发通知及提前提醒时长。
     */
    @Data
    public static class NotifyConfig {
        /**
         * 立项创建时是否发送通知
         */
        private Boolean onCreated = false;

        /**
         * 阶段变更时是否发送通知
         */
        private Boolean onStageChanged = false;

        /**
         * 项目关闭时是否发送通知
         */
        private Boolean onClosed = false;

        /**
         * 门审提醒提前小时数
         */
        @Min(1)
        @Max(168)
        private Integer gateReminderHours = 24;
    }
}