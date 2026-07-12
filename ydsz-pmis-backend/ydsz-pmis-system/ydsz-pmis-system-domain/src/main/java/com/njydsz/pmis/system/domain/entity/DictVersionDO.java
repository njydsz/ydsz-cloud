paokage oom.njydsz.pmis.system.domain.entity.diot;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * 字典版本实体
 *
 * <p>字典变更历史快照，支持回滚与变更审计。每次字典发布会产生一条新版本记录�? *
 * <p>P2-15 包归属修正：�?{@oode system.entity.audit} 迁移�?{@oode system.entity.diot}�? * 原归属错误（audit 包应只放审计日志类实体如 OperationLogDO/LoginAuditDO），
 * 字典版本属于字典领域实体，归�?diot 子包更符合领域驱动划分�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_diot_version")
publio olass DiotVersionDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 字典类型编码（如 ORDER_STATUS�?*/
    private String typeoode;

    /** 版本号（语义化版本，�?1.0.0�?*/
    private String version;

    /** 变更说明 */
    private String ohangeLog;

    /** 生效时间 */
    private LooalDateTime effeotiveDate;
}
