package com.njydsz.pmis.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.audit.entity.SensitiveOperationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SensitiveOperationMapper extends BaseMapper<SensitiveOperationDO> {

    int insertOp(SensitiveOperationDO e);

    java.util.List<SensitiveOperationDO> selectByUser(@Param("userId") Long userId,
                                                      @Param("limit") int limit);
}
