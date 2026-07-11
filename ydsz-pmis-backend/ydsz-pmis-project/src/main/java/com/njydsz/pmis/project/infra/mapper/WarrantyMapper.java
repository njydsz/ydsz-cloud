package com.njydsz.pmis.project.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.domain.entity.WarrantyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 质保期 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface WarrantyMapper extends BaseMapper<WarrantyDO> {

    /**
     * 按编码查询质保期
     *
     * @param code 质保期编码
     * @return 质保期对象，未找到返回 null
     */
    WarrantyDO selectByCode(@Param("code") String code);

    /**
     * 按立项 ID 查询质保期列表
     *
     * @param initiationId 立项 ID
     * @return 质保期列表
     */
    List<WarrantyDO> selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 即将到期（end_date ≤ 截止日期 且状态为 ACTIVE/EXPIRING_SOON）
     *
     * @param until 截止日期
     * @return 即将到期的质保期列表
     */
    List<WarrantyDO> selectExpiringBefore(@Param("until") LocalDate until);

    /**
     * 已过期（end_date < today 且状态非 EXPIRED/TERMINATED）
     *
     * @param today 当前日期
     * @return 已过期的质保期列表
     */
    List<WarrantyDO> selectOverdue(@Param("today") LocalDate today);

    /**
     * 更新质保期状态
     *
     * @param id               质保期 ID
     * @param status           目标状态
     * @param terminatedReason 终止原因
     * @return 受影响行数
     */
    int markStatus(@Param("id") String id, @Param("status") String status,
                   @Param("terminatedReason") String terminatedReason);
}
