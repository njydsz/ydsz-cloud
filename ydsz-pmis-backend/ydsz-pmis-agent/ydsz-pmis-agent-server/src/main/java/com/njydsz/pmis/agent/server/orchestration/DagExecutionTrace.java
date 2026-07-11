package com.njydsz.pmis.agent.server.orchestration.dag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DAG 执行追踪日志条目（P3-2 落地）。
 *
 * <p>记录节点执行过程中的关键事件，用于事后审计与调试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DagExecutionTrace implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 节点名 */
    private String nodeName;

    /** 事件类型：STARTED / SUCCESS / FAILED / SKIPPED / RETRY / CONDITION_FALSE */
    private String event;

    /** 消息说明 */
    private String message;

    /** 附加数据（如输出摘要、错误信息） */
    private Object data;

    /** 时间戳 */
    private LocalDateTime timestamp;
}
