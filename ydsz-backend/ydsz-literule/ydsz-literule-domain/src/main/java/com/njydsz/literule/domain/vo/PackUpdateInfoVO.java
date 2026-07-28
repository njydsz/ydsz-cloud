package com.njydsz.literule.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * PackUpdateInfo 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class PackUpdateInfoVO {

    /** packCode */
    private String packCode;

    /** packName */
    private String packName;

    /** installedVersion */
    private String installedVersion;

    /** latestVersion */
    private String latestVersion;

    /** hasUpdate */
    private boolean hasUpdate;

    /** installedAt */
    private LocalDateTime installedAt;

    /** industry */
    private String industry;

    /** description */
    private String description;

}
