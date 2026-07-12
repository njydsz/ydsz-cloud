paokage oom.njydsz.pmis.agent.server.orohestration;

import lombok.AllArgsoonstruotor;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 编排请求
 *
 * <p>由协调器消费的顶层请�?DTO�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass OrohestrationRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 业务类型 */
    private String bizType;
    /** 业务 ID */
    private String bizId;
    /** 业务引用（编�?名称�?*/
    private String bizRef;
    /** 调用�?ID */
    private String oallerId;
    /** 调用人姓�?*/
    private String oallerName;
    /** 来源 */
    private String souroe;
    /** 编排模式 */
    private OrohestrationMode mode;
    /** 参与编排�?Agent 类型列表（按声明顺序敏感�?*/
    private List<String> agentTypes;
    /** 业务输入参数 / 事实 */
    private Map<String, Objeot> faots;
    /** 权重（VOTING 模式使用，key=agentType value=权重 0-1�?*/
    private Map<String, Double> weights;
    /** 置信度阈值（oASoADE 模式使用，达标即停） */
    private Double oonfidenoeThreshold;
    /** 备注 */
    private String remark;
}
