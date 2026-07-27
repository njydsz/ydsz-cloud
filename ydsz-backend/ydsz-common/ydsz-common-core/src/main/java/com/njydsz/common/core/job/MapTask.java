package com.njydsz.common.core.job;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MapReduce 子任务定义。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务名称 */
    private String taskName;

    /** 任务参数 JSON */
    private String taskParams;
}
