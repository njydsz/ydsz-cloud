package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleTestCase 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleTestCaseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String ruleCode;
    private List<String> expectedTriggered;
    private String description;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}