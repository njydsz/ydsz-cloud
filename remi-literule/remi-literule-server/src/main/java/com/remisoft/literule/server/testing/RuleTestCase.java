package com.remisoft.literule.server.testing;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则测试用例（SDK 内置模型）
 *
 * <p>与 {@link com.remisoft.literule.domain.entity.RuleTestCaseDO} 不同，
 * 本类是 SDK 内置的纯 POJO 测试用例模型，不依赖 MyBatis-Plus 注解，
 * 适用于嵌入式场景和单元测试。
 *
 * <h3>链式构建测试用例</h3>
 * <pre>{@code
 * RuleTestCase tc = RuleTestCase.builder()
 *     .id("TC001")
 *     .name("高额预警-触发")
 *     .ruleCode("R001")
 *     .facts(Map.of("amount", 15000))
 *     .expectedTriggered(Set.of("R001"))
 *     .build();
 * }</pre>
 *
 * @since 1.0.0
 * @author remi-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleTestCase implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 测试用例 ID */
    private String id;

    /** 测试用例名称 */
    private String name;

    /** 关联规则编码（可选，null 表示全量仿真） */
    private String ruleCode;

    /** 事实数据 */
    private Map<String, Object> facts;

    /** 预期触发的规则编码集合 */
    private List<String> expectedTriggered;

    /** 描述 */
    private String description;

    /** 标签（用于分组/过滤） */
    private List<String> tags;
}
