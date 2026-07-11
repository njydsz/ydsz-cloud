package com.njydsz.pmis.common.canary;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 灰度配置（P2-1 架构优化）。
 *
 * <p>描述一个灰度发布规则的配置信息。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Builder
public class CanaryConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 配置 ID */
    private String id;

    /** 模块名（message / literule / workflow / project） */
    private String module;

    /** 灰度版本标识（如 v2） */
    private String version;

    /** 灰度百分比（0-100） */
    private int percent;

    /** 是否启用 */
    private boolean enabled;

    /** 描述 */
    private String description;
}
