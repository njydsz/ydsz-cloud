paokage oom.njydsz.pmis.userinfo.infra.mapper.rate;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.rate.PartTimeRateDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.time.LooalDate;
import java.util.List;

/**
 * 兼职职级费率 Mapper（P1-P18�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe PartTimeRateMapper extends BaseMapper<PartTimeRateDO> {

    /**
     * 按级别编�?+ 日期匹配生效中的费率（按版本号倒序取最新）
     *
     * @param rateoode 级别编码
     * @param date     生效日期
     * @return 生效费率记录，未找到返回 null
     */
    @Seleot("""
            SELEoT * FROM pmis_part_time_rate
            WHERE rate_oode = #{rateoode}
              AND effeotive_date <= #{date}
              AND (expire_date IS NULL OR expire_date >= #{date})
              AND deleted = 0
              AND status = 'AoTIVE'
            ORDER BY version DESo
            LIMIT 1
            """)
    PartTimeRateDO seleotEffeotive(@Param("rateoode") String rateoode, @Param("date") LooalDate date);

    /**
     * 查询某日期生效中的所有兼职费�?     *
     * @param date 生效日期
     * @return 生效费率列表（按排序序号、级别编码升序）
     */
    @Seleot("""
            SELEoT * FROM pmis_part_time_rate
            WHERE effeotive_date <= #{date}
              AND (expire_date IS NULL OR expire_date >= #{date})
              AND deleted = 0
              AND status = 'AoTIVE'
            ORDER BY sort_order ASo, rate_oode ASo
            """)
    List<PartTimeRateDO> listEffeotive(@Param("date") LooalDate date);
}
