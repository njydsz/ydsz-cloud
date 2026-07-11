package com.njydsz.pmis.system.domain.dto.config;

import com.njydsz.pmis.common.entity.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 配置分页查询 DTO。
 *
 * <p>继承 {@link PageQuery} 获得分页参数（page/size/keyword/orderBy），
 * 额外增加配置分组、状态、可见性等过滤维度。
 *
 * <p>查询逻辑：各过滤条件为 AND 关系，{@code keyword} 模糊匹配
 * {@code configKey} / {@code configValue} / {@code description} 三个字段。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "配置查询条件")
public class ConfigQueryDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关键字: 模糊匹配 configKey / configValue / description */
    private String keyword;

    /** 配置分组（精确匹配，如 system / business） */
    private String configGroup;

    /** 状态过滤：ENABLED / DISABLED */
    private String status;

    /** 是否公开：1 公开 / 0 私有 */
    private Integer isPublic;
}
