package com.njydsz.pmis.common.jdbc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.pmis.common.jdbc.domain.InterceptConfig;

import lombok.Getter;
import lombok.Setter;

/**
 * 字段填充配置类，控制自动填充字段的行为
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ydsz.jdbc.field-fill")
public class FieldFillConfiguration {

    /**
     * 创建人字段填充配置
     */
    private InterceptConfig createdByIntercept = new InterceptConfig();

    /**
     * 更新人字段填充配置
     */
    private InterceptConfig updateByIntercept = new InterceptConfig();

    /**
     * 创建时间字段填充配置
     */
    private InterceptConfig createAtIntercept = new InterceptConfig();

    /**
     * 更新时间字段填充配置
     */
    private InterceptConfig updateAtIntercept = new InterceptConfig();
}
