package com.njydsz.literule.domain.vo;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * DecisionTableDefinition 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class DecisionTableDefinitionVO {

    /** tableCode */
    private String tableCode;

    /** tableName */
    private String tableName;

    /** description */
    private String description;

    /** category */
    private String category;

    /** conditionColumns */
    private List<Column> conditionColumns;

    /** actionColumns */
    private List<Column> actionColumns;

    /** rows */
    private List<Row> rows;

    /** defaultActions */
    private Map<String, Object> defaultActions;

    /** scope */
    private String scope;

    /** name */
    private String name;

    /** label */
    private String label;

    /** type */
    private String type;

    /** conditions */
    private Map<String, String> conditions;

    /** actions */
    private Map<String, Object> actions;

}
