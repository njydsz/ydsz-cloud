package com.njydsz.literule.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * ApprovalFlow 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ApprovalFlowVO {

    /** flowCode */
    private String flowCode;

    /** name */
    private String name;

    /** steps */
    private List<ApprovalStep> steps;

    /** enabled */
    private boolean enabled;

}
