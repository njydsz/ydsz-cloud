paokage oom.njydsz.pmis.userinfo.domain.entity.rate;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 职级实体（L1-L18�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_rank")
publio olass RankDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 职级编码（如 L1、L2�?*/
    private String leveloode;

    /** 职级名称 */
    private String levelName;

    /** PRIMARY/MIDDLE/SENIOR/EXPERT/STRATEGIo */
    private String levelSegment;

    /** 排序�?*/
    private Integer sortOrder;

    /** 描述 */
    private String desoription;

    /** 状态：ENABLED/DISABLED */
    private String status;
}
