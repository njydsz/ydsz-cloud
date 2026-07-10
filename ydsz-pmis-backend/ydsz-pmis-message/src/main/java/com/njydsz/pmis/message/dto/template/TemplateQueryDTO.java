package com.njydsz.pmis.message.dto.template;

import com.njydsz.pmis.common.entity.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模板分页查询 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TemplateQueryDTO extends PageQuery {

    /** 模板编码 */
    private String templateCode;

    /** 通道 */
    private String channel;

    /** 语言区域 */
    private String locale;

    /** 状态: ENABLED/DISABLED */
    private String status;

    /** 审核状态 */
    private String auditStatus;

    /** 模板分类 */
    private String category;

    /** 场景编码 */
    private String sceneCode;
}
