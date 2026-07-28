package com.njydsz.literule.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * PackDiff 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class PackDiffVO {

    /** packCode */
    private String packCode;

    /** fromVersion */
    private String fromVersion;

    /** toVersion */
    private String toVersion;

    /** added */
    private List<String> added;

    /** removed */
    private List<String> removed;

    /** changed */
    private List<String> changed;

}
