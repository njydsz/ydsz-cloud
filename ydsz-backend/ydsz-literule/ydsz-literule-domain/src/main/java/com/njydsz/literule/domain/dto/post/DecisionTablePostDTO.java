package com.njydsz.literule.domain.dto.post;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

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
    private List<Map<String, Object>> conditionColumns;
    private List<Map<String, Object>> actionColumns;
    private List<Map<String, Object>> rows;
    private Map<String, Object> defaultActions;
    private String hitPolicy;
    private Boolean enabled;
    private Integer priority;
    private Integer version;
}