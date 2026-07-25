package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 公司 VO（不含 deleted/createdBy 等内部字段）。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
public class CompanyVO {

    private String id;
    private String companyName;
    private String companyCode;
    private String parentId;
    private String contactPerson;
    private String contactPhone;
    private String address;
    private String status;
}
