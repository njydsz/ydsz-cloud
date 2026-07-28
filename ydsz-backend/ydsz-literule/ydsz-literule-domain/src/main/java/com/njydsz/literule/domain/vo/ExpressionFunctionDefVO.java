package com.njydsz.literule.domain.vo;

import lombok.Data;

/**
 * ExpressionFunctionDef 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ExpressionFunctionDefVO {

    /** name */
    private String name;

    /** signature */
    private String signature;

    /** description */
    private String description;

    /** sample */
    private String sample;

    /** category */
    private String category;

    /** supportedEngines */
    private String supportedEngines;

}
