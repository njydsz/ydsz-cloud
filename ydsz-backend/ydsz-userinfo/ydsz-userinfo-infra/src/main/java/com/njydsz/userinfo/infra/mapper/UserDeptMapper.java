package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.UserDeptDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-部门关联表 Mapper 接口。
 *
 * <p>对应数据表 ydsz_user_dept，
 * 继承 MyBatis-Plus {@code BaseMapper} 提供标准 CRUD 操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface UserDeptMapper extends BaseMapper<UserDeptDO> {
}
