package com.njydsz.userinfo.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.njydsz.common.domain.query.PageQuery;

/**
 * 部门分页查询参数，继承 {@link PageQuery} 提供分页基础字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DepartmentPageQuery extends PageQuery {

    /** 部门编码，模糊查询 */
    private String deptCode;
    /** 部门名称，模糊查询 */
    private String deptName;
    /** 状态过滤：ENABLE/DISABLE */
    private String status;
}
