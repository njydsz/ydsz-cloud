package com.remisoft.literule.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 规则测试用例视图对象（VO）。
 * <p>
 * 用于 Controller 层返回规则测试用例的完整信息。测试用例包含输入因子和
 * 预期命中规则列表，支撑规则回归测试和质量保障。
 * </p>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class RuleTestCaseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 测试用例唯一标识（主键） */
    private String id;
    /** 测试用例名称 */
    private String name;
    /** 关联的规则编码 */
    private String ruleCode;
    /** 预期命中的规则编码列表 */
    private List<String> expectedTriggered;
    /** 测试用例描述 */
    private String description;
    /** 创建人 */
    private String createdBy;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新人 */
    private String updatedBy;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
