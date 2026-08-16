package com.njydsz.message.domain.dto.template;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 模板分页查询 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TemplateQueryDTO extends PageQuery {

    /** 模板编码 */
    @Xss
    private String templateCode;

    /** 通道 */
    @Xss
    private String channel;

    /** 语言区域 */
    @Xss
    private String locale;

    /** 状态: ENABLED/DISABLED */
    @Xss
    private String status;

    /** 审核状态 */
    @Xss
    private String auditStatus;

    /** 模板分类 */
    @Xss
    private String category;

    /** 场景编码 */
    @Xss
    private String sceneCode;
}
