package com.njydsz.literule.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * InstallResult 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class InstallResultVO {

    /** packCode */
    private String packCode;

    /** version */
    private String version;

    /** total */
    private int total;

    /** success */
    private int success;

    /** failed */
    private int failed;

    /** failedCodes */
    private List<String> failedCodes;

}
