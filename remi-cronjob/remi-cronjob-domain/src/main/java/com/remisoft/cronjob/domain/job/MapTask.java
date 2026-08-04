package com.remisoft.cronjob.domain.job;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MapReduce 子任务定义
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapTask {

    /** 子任务名称 */
    private String taskName;
    /** 子任务参数 JSON */
    private String taskParams;
}
