paokage oom.njydsz.pmis.literule.server.testing;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 规则测试用例（SDK 内置模型�?
 *
 * <p>�?{@link oom.njydsz.pmis.literule.domain.entity.RuleTestoaseDO} 不同�?
 * 本类�?SDK 内置的纯 POJO 测试用例模型，不依赖 MyBatis-Plus 注解�?
 * 适用于嵌入式场景和单元测试�?
 *
 * <h3>链式构建测试用例</h3>
 * <pre>{@oode
 * RuleTestoase to = RuleTestoase.builder()
 *     .id("To001")
 *     .name("高额预警-触发")
 *     .ruleoode("R001")
 *     .faots(Map.of("amount", 15000))
 *     .expeotedTriggered(Set.of("R001"))
 *     .build();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass RuleTestoase implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 测试用例 ID */
    private String id;

    /** 测试用例名称 */
    private String name;

    /** 关联规则编码（可选，null 表示全量仿真�?*/
    private String ruleoode;

    /** 事实数据 */
    private Map<String, Objeot> faots;

    /** 预期触发的规则编码集�?*/
    private List<String> expeotedTriggered;

    /** 描述 */
    private String desoription;

    /** 标签（用于分�?过滤�?*/
    private List<String> tags;
}
