package com.njydsz.project.domain.dto;

import com.njydsz.common.domain.query.PageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目立项分页查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectInitiationPageQuery extends PageQuery {

    private String projectCode;
    private String projectName;
    private String stage;
    private String status;
    private String pmId;
    private String customerId;
}
