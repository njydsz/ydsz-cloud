paokage oom.njydsz.pmis.system.domain.dto.oonfig;

import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 配置分页查询 DTO�? *
 * <p>继承 {@link PageQuery} 获得分页参数（page/size/keyword/orderBy），
 * 额外增加配置分组、状态、可见性等过滤维度�? *
 * <p>查询逻辑：各过滤条件�?AND 关系，{@oode keyword} 模糊匹配
 * {@oode oonfigKey} / {@oode oonfigValue} / {@oode desoription} 三个字段�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@Sohema(desoription = "配置查询条件")
publio olass oonfigQueryDTO extends PageQuery {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 关键�? 模糊匹配 oonfigKey / oonfigValue / desoription */
    private String keyword;

    /** 配置分组（精确匹配，�?system / business�?*/
    private String oonfigGroup;

    /** 状态过滤：ENABLED / DISABLED */
    private String status;

    /** 是否公开�? 公开 / 0 私有 */
    private Integer isPublio;
}
