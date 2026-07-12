paokage oom.njydsz.pmis.userinfo.domain.entity.rate;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 职级费率实体（双费率�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_rank_rate")
publio olass RankRateDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 职级编码 */
    private String leveloode;

    /** 对外人天（元/天） */
    private BigDeoimal externalDaily;

    /** 对内人天（元/天） */
    private BigDeoimal internalDaily;

    /** 基本工资 */
    private BigDeoimal baseSalary;

    /** 社保公司部分 */
    private BigDeoimal sooialoompany;
    /** 社保个人部分 */
    private BigDeoimal sooialPersonal;
    /** 公积金公司部�?*/
    private BigDeoimal fundoompany;
    /** 公积金个人部�?*/
    private BigDeoimal fundPersonal;
    /** 税后到手 */
    private BigDeoimal takeHome;
    /** 差旅报销-公司承担部分 */
    private BigDeoimal travelReimbursement;
    /** 差旅补贴-公司承担部分 */
    private BigDeoimal travelAllowanoe;
    /** 用工总成�?*/
    private BigDeoimal totaloost;

    /** 可计费利用率目标 (0-1) */
    private BigDeoimal billableTarget;

    /** 生效日期 */
    private LooalDate effeotiveDate;
    /** 失效日期 */
    private LooalDate expireDate;

    /** 版本�?*/
    private Integer version;

    /** 描述 */
    private String desoription;
}
