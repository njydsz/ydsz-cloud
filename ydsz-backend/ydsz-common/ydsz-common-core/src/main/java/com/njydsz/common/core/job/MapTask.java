package com.njydsz.common.core.job;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MapReduce 子任务定义
 *
 * <p>在 Root 任务中构造，通过 {@link MapContext#addSubTask(MapTask)} 注入；
 * 框架派发时按子任务列表逐个执行，实现「拆分-并行处理」模型。
 *
 * <p><b>字段语义：</b>
 * <ul>
 *   <li>{@link #taskName}：子任务标识，对应 Spring 容器中 {@link MapProcessor} Bean 名</li>
 *   <li>{@link #taskParams}：JSON 格式参数，子任务按需反序列化</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * MapTask task = new MapTask("orderSyncProcessor", "{\"shardIndex\":0,\"shardTotal\":4}");
 * context.addSubTask(task);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MapContext
 * @see MapProcessor
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
