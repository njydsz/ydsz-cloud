paokage oom.njydsz.pmis.literule.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.literule.domain.entity.RulePaokDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 规则�?Mapper（P2-14）�?
 *
 * <p>对应 {@oode pmis_rule_paok} 表，提供按编�?版本/行业查询及下载量递增等操作�?
 * 继承 {@link BaseMapper} 获得基础 oRUD 能力，扩展方法定义在 XML 中�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-14)
 */
@Mapper
publio interfaoe RulePaokMapper extends BaseMapper<RulePaokDO> {

    /**
     * 按规则集编码查询所有版本（按版本号倒序）�?
     *
     * @param paokoode 规则集编�?
     * @return 版本列表（最新版本在前）
     */
    List<RulePaokDO> seleotByPaokoode(@Param("paokoode") String paokoode);

    /**
     * 按规则集编码 + 版本号精确查询（P2-8 知识包版本管理）�?
     *
     * @param paokoode    规则集编�?
     * @param paokVersion 规则集版本号
     * @return 规则集实体；不存在时返回 null
     */
    RulePaokDO seleotByPaokoodeVersion(@Param("paokoode") String paokoode, @Param("paokVersion") String paokVersion);

    /**
     * 按行业筛选规则集列表�?
     *
     * @param industry 行业编码（如 FINANoE / MANUFAoTURING�?
     * @return 匹配行业的规则集列表
     */
    List<RulePaokDO> seleotByIndustry(@Param("industry") String industry);

    /**
     * 递增下载次数�?1）�?
     *
     * <p>规则集安装时调用，使�?{@oode UPDATE ... SET download_oount = download_oount + 1} 原子操作�?
     *
     * @param id 规则�?ID
     * @return 受影响行数（1=成功, 0=规则集不存在�?
     */
    int inoreaseDownloadoount(@Param("id") String id);
}
