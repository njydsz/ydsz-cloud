package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 系统配置实体
 *
 * <p>对应数据库表 {@code ydsz_config}，存储系统级配置项。
 * 支持按分组分类、按配置键查找，公开/私有配置区分，多种值类型（字符串/数字/布尔/JSON）。
 * 运行时可通过 {@code ConfigClient} 监听配置变更（基于 Nacos long-polling）。
 *
 * <p><b>典型使用场景：</b>
 * <ul>
 *   <li>功能开关（feature flag）：通过 {@code configGroup=feature} + {@code isPublic=1} 让前端感知</li>
 *   <li>限流阈值：运行时调整接口限流参数，无需发版</li>
 *   <li>第三方服务地址：密钥/地址变更不需重新部署</li>
 *   <li>UI 文案：前端展示文本、错误提示等可由配置动态下发</li>
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_config_group_key}（{@code config_group}, {@code config_key}），
 * 加速按分组+键查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.enums.ConfigValueType 值类型枚举
 * @see com.njydsz.system.server.service.ConfigService 配置业务逻辑
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_config")
public class Config extends MpBaseEntity<String> {

    /** 配置分组（用于按业务域分类管理配置） */
    private String configGroup;

    /** 配置键（同组内唯一标识） */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型（STRING/NUMBER/BOOLEAN/JSON，参见 ConfigValueType 枚举） */
    private String valueType;

    /** 默认值（配置未设置时使用） */
    private String defaultValue;

    /** 配置描述 */
    private String description;

    /** 是否公开配置（1=公开，前端可查；0=私有，仅后端可查） */
    private Integer isPublic;

    /** 排序序号 */
    private Integer sortOrder;

}
