package com.njydsz.literule.domain.vo;

import lombok.Data;

/**
 * VariableDefinition 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class VariableDefinitionVO {

    /** name */
    private String name;

    /** type */
    private String type;

    /** description */
    private String description;

    /** sampleValue */
    private Object sampleValue;

    /** category */
    private String category;

}
