package com.njydsz.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.generator.po.GenDatasourcePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源配置 MyBatis-Plus Mapper。
 *
 * <p>继承 BaseMapper 提供基础 CRUD，无需 XML 映射。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Mapper
public interface GenDatasourceMapper extends BaseMapper<GenDatasourcePO> {
  // BaseMapper 已提供 selectById / insert / updateById / deleteById /
  // selectList / selectCount 等基础方法
}
