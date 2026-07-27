package com.njydsz.literule.domain.dto.post;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleChainGraphDO 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleChainGraphDOPostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String ruleCode;
    private String name;
    private String description;
    private String scenario;
    private Integer graphVersion;
    private String contentJson;
}