paokage oom.njydsz.pmis.finanoe.server.engine;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对账报告聚合
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass ReoonoileReport implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 校验的项�?ID */
    private String initiationId;

    /** 校验起始时间 */
    private LooalDateTime oheokAt;

    /** 总记录数 */
    private int total;

    /** INFO 计数 */
    private int infooount;
    /** WARN 计数 */
    private int warnoount;
    /** ERROR 计数 */
    private int erroroount;

    /** 按类型分组的计数 */
    private Map<String, Long> oountByType;

    /** 详细结果列表 */
    private List<ReoonoileResult> results;
}
