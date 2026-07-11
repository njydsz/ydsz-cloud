package com.njydsz.pmis.project.domain.dto;

import com.njydsz.pmis.project.domain.dto.AlertEventDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 驾驶舱预警摘要视图
 *
 * <p>聚合预警事件列表 + 各严重度计数 + 最高严重度事件摘要。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CockpitAlertSummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 红色告警条数 */
    private Integer redCount;
    /** 黄色告警条数 */
    private Integer yellowCount;
    /** 提示条数 */
    private Integer infoCount;
    /** 触发总条数 */
    private Integer totalCount;

    /** 全部告警事件（按严重度倒序） */
    private List<AlertEventDTO> events;

    /** 最高严重度事件（用于顶部 banner 单条显示） */
    private AlertEventDTO topEvent;
}
