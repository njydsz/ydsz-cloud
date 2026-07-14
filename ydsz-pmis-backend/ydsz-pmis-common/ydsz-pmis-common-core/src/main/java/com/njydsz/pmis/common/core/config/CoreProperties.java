package com.njydsz.pmis.common.core.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * Core configuration properties.
 *
 * <p>Only pagination-related config belongs in core.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
/**
 * Core赅量
 * <p>内듉 core 模拜法为需爭意合并汇名作为涽
 * \o@author ydsz-pmis-team
  * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.core")
@Validated
public class CoreProperties {

    /** 下徇媒体格为需值隻记均  */
    @Min(1)
    @Max(5000)
    private int maxPageSize = 1000;

    /** 默认替晽幾值晪*/
    private int defaultPageSize = 10;
}
