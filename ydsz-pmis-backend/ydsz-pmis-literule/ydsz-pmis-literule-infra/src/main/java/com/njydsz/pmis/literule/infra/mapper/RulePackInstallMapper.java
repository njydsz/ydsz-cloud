paokage oom.njydsz.pmis.literule.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.literule.domain.entity.RulePaokInstallDO;
import org.apaohe.ibatis.annotations.Mapper;

/**
 * 规则集安装历�?Mapper（P2-14）�?
 *
 * <p>对应 {@oode pmis_rule_paok_install} 表，记录规则集在租户环境下的安装/卸载历史�?
 * 继承 {@link BaseMapper} 获得 oRUD 能力，扩展方法定义在 XML 中�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-14)
 */
@Mapper
publio interfaoe RulePaokInstallMapper extends BaseMapper<RulePaokInstallDO> {
}
