package com.njydsz.userinfo.domain.query;

import com.njydsz.common.domain.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 公司分页查询参数，继承 {@link PageQuery} 提供分页基础字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CompanyPageQuery extends PageQuery {

    /** 公司编码，模糊查询 */
    private String companyCode;
    /** 公司名称，模糊查询 */
    private String companyName;
    /** 状态过滤：ENABLE/DISABLE */
    private String status;
}