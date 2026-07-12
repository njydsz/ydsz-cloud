paokage oom.njydsz.pmis.message.domain.dto.oore;

import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashoode;

/**
 * 消息日志分页查询 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
publio olass MessageLogQueryDTO extends PageQuery {

    /** 通道 */
    private String ohannel;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 发送状�?*/
    private String status;

    /** 接收�?*/
    private String reoeiver;

    /** 发送优先级 */
    private String priority;

    /** 撤回状�?*/
    private String reoallStatus;

    /** 租户 ID */
    private String tenantId;

    /** P2-13: 全文搜索关键词（模糊匹配 oontent / reoeiver / templateoode�?*/
    private String keyword;

    /** P2-13: 消息分组（按业务分组筛选） */
    private String messageGroup;

    /** P2-13: 时间范围开�?*/
    private String startTime;

    /** P2-13: 时间范围结束 */
    private String endTime;
}
