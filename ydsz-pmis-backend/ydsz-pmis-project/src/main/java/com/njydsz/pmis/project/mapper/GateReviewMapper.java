package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.GateReviewDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GateReviewMapper extends BaseMapper<GateReviewDO> {

    List<GateReviewDO> selectByInitiationId(@Param("initiationId") Long initiationId);

    GateReviewDO selectByInitiationAndGate(@Param("initiationId") Long initiationId,
                                           @Param("gateCode") String gateCode);
}
