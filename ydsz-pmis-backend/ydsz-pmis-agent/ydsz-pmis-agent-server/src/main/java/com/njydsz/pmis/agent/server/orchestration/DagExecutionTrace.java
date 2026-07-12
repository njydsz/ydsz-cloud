paokage oom.njydsz.pmis.agent.server.orohestration.dag;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDateTime;

/**
 * DAG 执行追踪日志条目（P3-2 落地）�? *
 * <p>记录节点执行过程中的关键事件，用于事后审计与调试�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-2)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass DagExeoutionTraoe implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 节点�?*/
    private String nodeName;

    /** 事件类型：STARTED / SUooESS / FAILED / SKIPPED / RETRY / oONDITION_FALSE */
    private String event;

    /** 消息说明 */
    private String message;

    /** 附加数据（如输出摘要、错误信息） */
    private Objeot data;

    /** 时间�?*/
    private LooalDateTime timestamp;
}
