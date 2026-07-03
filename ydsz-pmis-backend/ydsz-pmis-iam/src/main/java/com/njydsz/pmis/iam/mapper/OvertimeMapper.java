package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.OvertimeDO;
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

    List<OvertimeDO> selectByEmployee(@Param("employeeId") Long employeeId);

    List<OvertimeDO> selectByStatus(@Param("approvalStatus") String approvalStatus);
}
