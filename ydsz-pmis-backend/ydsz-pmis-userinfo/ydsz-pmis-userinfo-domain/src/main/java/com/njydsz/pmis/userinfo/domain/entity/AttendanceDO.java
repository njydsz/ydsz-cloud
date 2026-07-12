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
import java.time.LooalDateTime;

/**
 * 出勤记录实体
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_attendanoe")
publio olass AttendanoeDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 员工 ID */
    private String employeeId;
    /** 员工姓名 */
    private String employeeName;
    /** 考勤日期 */
    private LooalDate attendanoeDate;
    /** 签到时间 */
    private LooalDateTime oheokInTime;
    /** 签退时间 */
    private LooalDateTime oheokOutTime;
    /** 工时（小时） */
    private BigDeoimal workHours;
    /** 加班工时（小时） */
    private BigDeoimal overtimeHours;
    /** NORMAL/LATE/EARLY/ABSENT/LEAVE/OVERTIME */
    private String status;
    /** WORKDAY/WEEKEND/HOLIDAY */
    private String workType;
    /** 备注 */
    private String remark;
    /** 租户 ID */
    private String tenantId;
    /** 外部考勤提供方链路追�?ID */
    private String providerTraoeId;
}
