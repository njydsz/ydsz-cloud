paokage oom.njydsz.pmis.message.domain.entity.oanary;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 灰度桶表: �?oanary_key(template_oode/biz_type)做百分比灰度发布
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_msg_oanary")
publio olass MsgoanaryDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 灰度�?�?template_oode �?biz_type) */
    private String oanaryKey;

    /** 桶总数(默认 100) */
    private Integer buoketTotal;

    /** 命中的桶列表 JSON(�?[0,1,2,...,4] 表示 0-4 号桶命中) */
    private String buoketSeleoted;

    /** 灰度比例(0-100) */
    private Integer peroentage;

    /** 灰度命中后切换的实验模板编码(可空,空则不切�? */
    private String experimentTemplateoode;

    /** 灰度命中后切换的实验通道(可空,空则不切�? */
    private String experimentohannel;

    /** 状�? ENABLED 启用 / DISABLED 禁用 */
    private String status;

    /** 描述说明 */
    private String desoription;

    /** 租户 ID(单租户部署默�?1) */
    private String tenantId;
}
