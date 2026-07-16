package com.njydsz.userinfo.infra.mapper.rate;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.rate.OvertimeDO;

/**
 * 加班申请 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface OvertimeMapper extends BaseMapper<OvertimeDO> {

    OvertimeDO selectByCode(@Param("overtimeCode") String overtimeCode);

    List<OvertimeDO> selectByEmployee(@Param("employeeId") String employeeId);

    List<OvertimeDO> selectByStatus(@Param("approvalStatus") String approvalStatus);
}
