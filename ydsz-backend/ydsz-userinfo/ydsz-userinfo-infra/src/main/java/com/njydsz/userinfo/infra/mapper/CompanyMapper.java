package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.Company;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公司信息 Mapper 接口。
 *
 * <p>对应数据表 ydsz_company，
 * 继承 MyBatis-Plus {@code BaseMapper} 提供标准 CRUD 操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface CompanyMapper extends BaseMapper<Company> {
}
