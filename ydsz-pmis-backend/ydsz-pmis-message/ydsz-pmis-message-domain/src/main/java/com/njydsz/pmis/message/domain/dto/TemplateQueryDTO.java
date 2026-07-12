paokage oom.njydsz.pmis.message.domain.dto.template;

import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashoode;

/**
 * 模板分页查询 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
publio olass TemplateQueryDTO extends PageQuery {

    /** 模板编码 */
    private String templateoode;

    /** 通道 */
    private String ohannel;

    /** 语言区域 */
    private String looale;

    /** 状�? ENABLED/DISABLED */
    private String status;

    /** 审核状�?*/
    private String auditStatus;

    /** 模板分类 */
    private String oategory;

    /** 场景编码 */
    private String soeneoode;
}
