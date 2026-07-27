package com.njydsz.literule.domain.dto.post;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * RuleTestCaseDO 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleTestCaseDOPostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private String ruleCode;
    private Map<String, Object> factsData;
    private List<String> expectedTriggered;
    private String description;
}