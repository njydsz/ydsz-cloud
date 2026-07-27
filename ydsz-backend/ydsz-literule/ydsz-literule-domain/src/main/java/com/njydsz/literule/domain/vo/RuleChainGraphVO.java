package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleChainGraph 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleChainGraphVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String ruleCode;
    private String name;
    private String description;
    private String scenario;
    private Integer graphVersion;
    private String status;
    private String contentJson;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}