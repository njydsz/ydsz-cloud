package com.remisoft.literule.domain.dto.post;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 规则测试用例新增请求 DTO。
 * <p>
 * 用于 POST 接口创建规则测试用例，包含输入因子数据和预期命中规则列表，
 * 支撑规则回归测试。
 * </p>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class RuleTestCasePostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 测试用例名称 */
    private String name;
    /** 关联的规则编码（可选，null 表示通用测试用例） */
    private String ruleCode;
    /** 事实数据（输入因子） */
    private Map<String, Object> factsData;
    /** 预期命中的规则编码列表 */
    private List<String> expectedTriggered;
    /** 测试用例描述 */
    private String description;
}
