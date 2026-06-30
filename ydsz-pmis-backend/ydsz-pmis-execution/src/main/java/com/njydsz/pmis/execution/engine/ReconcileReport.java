package com.njydsz.pmis.execution.engine;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对账报告聚合
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ReconcileReport implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 校验的项目 ID */
    private Long initiationId;

    /** 校验起始时间 */
    private LocalDateTime checkAt;

    /** 总记录数 */
    private int total;

    /** INFO / WARN / ERROR 计数 */
    private int infoCount;
    private int warnCount;
    private int errorCount;

    /** 按类型分组的计数 */
    private Map<String, Long> countByType;

    /** 详细结果列表 */
    private List<ReconcileResult> results;
}
