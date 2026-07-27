package com.njydsz.literule.domain.dto.post;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * DecisionTable 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class DecisionTablePostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tableCode;
    private String tableName;
    private String description;
    private String category;
    private String hitPolicy;
    private Boolean enabled;
    private Integer priority;
    private Integer version;
}