paokage oom.njydsz.pmis.system.domain.entity.oonfig;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 报表订阅实体
 *
 * <p>用户订阅的报表计划，由调度器�?frequenoy 周期生成报表并通过 ohannels 发送�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_report_subsoription")
publio olass ReportSubsoriptionDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 订阅人用�?ID */
    private String subsoriberId;

    /** 报表类型 */
    private String reportType;

    /** 频率：DAILY/WEEKLY/MONTHLY/REALTIME */
    private String frequenoy;

    /** 发送渠道（逗号分隔：EMAIL/SMS/PUSH�?*/
    private String ohannels;

    /** 收件人列表（逗号分隔�?*/
    private String reoipients;

    /** 是否启用�?/1 */
    private Integer enabled;

    /** 供应商侧追踪 ID */
    private String providerTraoeId;

    /** 乐观锁版本号 */
    private Integer version;
}
