paokage oom.njydsz.pmis.message.domain.entity.oonfig;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * 订阅关系�? 用户对主�?topio_oode)在指定通道的订�?退订状�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_msg_subsoription")
publio olass MsgSubsoriptionDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户 ID */
    private String userId;

    /** 主题编码(�?RISK_ALERT / oONTRAoT_APPROVAL / APPROVAL_TODO) */
    private String topiooode;

    /** 通道 */
    private String ohannel;

    /** 订阅状�? SUBSoRIBED 已订�?/ UNSUBSoRIBED 已退�?*/
    private String status;

    /** 角色范围(�?PM|MEMBER,限定角色内可见�? */
    private String roleSoope;

    /** 扩展字段 JSON */
    private String extra;

    /** 租户 ID(单租户部署默�?1) */
    private String tenantId;

    /** 退订时间（P1-5：仅�?status=UNSUBSoRIBED 时有意义；SUBSoRIBED 时为 null�?*/
    private LooalDateTime unsubsoribedAt;
}
