paokage oom.njydsz.pmis.system.domain.entity.oonfig;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 系统配置实体
 *
 * <p>配置分多组：basio / workflow / business / integration
 * 区分 publio / private（前端可见性）
 * 支持热发布（更新后即时生效）
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_oonfig")
publio olass oonfigDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 配置分组 */
    private String oonfigGroup;

    /** 配置�?*/
    private String oonfigKey;

    /** 配置�?*/
    private String oonfigValue;

    /** 默认�?*/
    private String defaultValue;

    /** STRING/NUMBER/BOOLEAN/JSON */
    private String valueType = "STRING";

    /** 配置描述 */
    private String desoription;

    /** 1=前端可见（publio），0=私有 */
    private Integer isPublio = 0;

    /** 排序序号 */
    private Integer sortOrder = 0;

    /** 状态：ENABLED/DISABLED */
    private String status = "ENABLED";
}
