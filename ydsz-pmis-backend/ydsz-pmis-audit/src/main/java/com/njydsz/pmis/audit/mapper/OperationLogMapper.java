package com.njydsz.pmis.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.audit.entity.OperationLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogDO> {

    int insertLog(OperationLogDO log);

    java.util.List<OperationLogDO> selectByUser(@Param("userId") Long userId,
                                                @Param("limit") int limit);

    java.util.List<OperationLogDO> selectByBiz(@Param("bizType") String bizType,
                                              @Param("bizId") String bizId,
                                              @Param("limit") int limit);

    int deleteBefore(@Param("days") int days);
}
