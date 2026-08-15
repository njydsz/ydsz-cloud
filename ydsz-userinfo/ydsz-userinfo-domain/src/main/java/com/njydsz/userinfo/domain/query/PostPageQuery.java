package com.njydsz.userinfo.domain.query;

import com.njydsz.common.domain.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 岗位分页查询参数，继承 {@link PageQuery} 提供分页基础字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PostPageQuery extends PageQuery {

    /** 岗位编码，模糊查询 */
    private String postCode;
    /** 岗位名称，模糊查询 */
    private String postName;
    /** 状态过滤：ENABLE/DISABLE */
    private String status;
}
