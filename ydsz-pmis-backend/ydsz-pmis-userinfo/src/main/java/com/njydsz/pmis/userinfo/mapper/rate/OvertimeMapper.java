package com.njydsz.pmis.userinfo.mapper.rate;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.rate.OvertimeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 加班申请 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface OvertimeMapper extends BaseMapper<OvertimeDO> {

    OvertimeDO selectByCode(@Param("overtimeCode") String overtimeCode);

    List<OvertimeDO> selectByEmployee(@Param("employeeId") String employeeId);

    List<OvertimeDO> selectByStatus(@Param("approvalStatus") String approvalStatus);
}
