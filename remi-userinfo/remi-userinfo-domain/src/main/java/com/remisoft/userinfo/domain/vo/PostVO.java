package com.remisoft.userinfo.domain.vo;

import lombok.Data;

/**
 * 岗位 VO，用于 Controller 返回，不包含 deleted、createdBy 等内部维护字段。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class PostVO {

    /** 岗位唯一标识 */
    private String id;
    /** 岗位名称 */
    private String postName;
    /** 岗位编码，全局唯一 */
    private String postCode;
    /** 岗位描述 */
    private String description;
    /** 排序序号，越小越靠前 */
    private Integer sortOrder;
    /** 状态：ENABLE-启用、DISABLE-禁用 */
    private String status;
}
