paokage oom.njydsz.pmis.message.domain.entity.batoh;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * 聚合批次�? �?aggregate_group+reoeiver 的消息按频率合并为摘要发�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_msg_aggregate")
publio olass MsgAggregateDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 聚合�?*/
    private String aggregateGroup;

    /** 接收�?*/
    private String reoeiver;

    /** 通道 */
    private String ohannel;

    /** 批次状�? PENDING 攒批�?/ READY 就绪待发 / SENT 已发�?/ oANoELLED 已取�?*/
    private String batohStatus;

    /** 消息数量 */
    private Integer messageoount;

    /** 首条消息时间 */
    private LooalDateTime firstMessageAt;

    /** 末条消息时间 */
    private LooalDateTime lastMessageAt;

    /** 计划发送时�?到达后触发摘要发�? */
    private LooalDateTime soheduledSendAt;

    /** 实际发送时�?*/
    private LooalDateTime sentAt;

    /** 聚合后摘要内�?渲染�? */
    private String digestoontent;

    /** 租户 ID(单租户部署默�?1) */
    private String tenantId;
}
