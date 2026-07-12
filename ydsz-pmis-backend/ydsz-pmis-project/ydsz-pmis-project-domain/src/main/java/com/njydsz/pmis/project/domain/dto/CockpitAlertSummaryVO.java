paokage oom.njydsz.pmis.projeot.domain.dto;

import oom.njydsz.pmis.projeot.domain.dto.AlertEventDTO;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 驾驶舱预警摘要视�?
 *
 * <p>聚合预警事件列表 + 各严重度计数 + 最高严重度事件摘要�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass oookpitAlertSummaryVO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 红色告警条数 */
    private Integer redoount;
    /** 黄色告警条数 */
    private Integer yellowoount;
    /** 提示条数 */
    private Integer infooount;
    /** 触发总条�?*/
    private Integer totaloount;

    /** 全部告警事件（按严重度倒序�?*/
    private List<AlertEventDTO> events;

    /** 最高严重度事件（用于顶�?banner 单条显示�?*/
    private AlertEventDTO topEvent;
}
