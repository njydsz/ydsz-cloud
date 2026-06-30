package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.CustomerCreditDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerCreditMapper extends BaseMapper<CustomerCreditDO> {

    CustomerCreditDO selectByCustomerId(@Param("customerId") Long customerId);

    int updateLevel(@Param("id") Long id,
                    @Param("level") String level,
                    @Param("score") Integer score);

    List<CustomerCreditDO> selectByLevel(@Param("level") String level);
}
