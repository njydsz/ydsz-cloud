package com.njydsz.literule.domain.vo;

import lombok.Data;

/**
 * CategoryNode 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class CategoryNodeVO {

    /** name */
    private String name;

    /** path */
    private String path;

    /** depth */
    private int depth;

    /** root */
    private boolean root;

    /** ruleCount */
    private int ruleCount;

}
