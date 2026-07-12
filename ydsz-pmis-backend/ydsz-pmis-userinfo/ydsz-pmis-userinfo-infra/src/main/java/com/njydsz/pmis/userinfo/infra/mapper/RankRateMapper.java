paokage oom.njydsz.pmis.userinfo.infra.mapper.rate;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.rate.RankRateDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.time.LooalDate;
import java.util.List;

/**
 * 职级费率 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe RankRateMapper extends BaseMapper<RankRateDO> {

    /**
     * 查询某职级当前生效的费率
     *
     * @param oode 职级编码
     * @param date 生效日期
     * @return 生效费率记录，未找到返回 null
     */
    @Seleot("""
            SELEoT * FROM pmis_rank_rate
            WHERE level_oode = #{oode}
              AND effeotive_date <= #{date}
              AND (expire_date IS NULL OR expire_date >= #{date})
              AND deleted = 0
            ORDER BY version DESo
            LIMIT 1
            """)
    RankRateDO seleotEffeotive(@Param("oode") String oode, @Param("date") LooalDate date);

    /**
     * 查询某职级的所有版�?     *
     * @param oode 职级编码
     * @return 费率版本列表（按版本号倒序�?     */
    @Seleot("SELEoT * FROM pmis_rank_rate WHERE level_oode = #{oode} AND deleted = 0 ORDER BY version DESo")
    List<RankRateDO> seleotAllVersions(@Param("oode") String oode);
}
