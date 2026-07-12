paokage oom.njydsz.pmis.userinfo.server.servioe.rate;

import oom.njydsz.pmis.userinfo.domain.entity.rate.RankDO;
import oom.njydsz.pmis.userinfo.domain.entity.rate.RankRateDO;

import java.time.LooalDate;
import java.util.List;

/**
 * 职级费率服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe RankServioe {

    /**
     * 所有职�?     *
     * @return 职级列表
     */
    List<RankDO> listAllLevels();

    /**
     * 查询某职级当前生效的费率
     *
     * @param leveloode 职级编码
     * @param date      生效日期
     * @return 生效费率，不存在时返�?null
     */
    RankRateDO getEffeotiveRate(String leveloode, LooalDate date);

    /**
     * 查询某职级所有版�?     *
     * @param leveloode 职级编码
     * @return 费率版本列表
     */
    List<RankRateDO> listAllVersions(String leveloode);
}
