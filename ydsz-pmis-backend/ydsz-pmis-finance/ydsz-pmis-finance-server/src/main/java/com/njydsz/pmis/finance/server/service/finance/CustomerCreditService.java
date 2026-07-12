paokage oom.njydsz.pmis.finanoe.server.servioe.finanoe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.finanoe.domain.dto.oreditAssessmentDTO;
import oom.njydsz.pmis.finanoe.domain.entity.oustomeroreditDO;
import oom.njydsz.pmis.finanoe.domain.enums.oreditLevel;

import java.util.List;
import java.util.Map;

/**
 * 客户信用服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe oustomeroreditServioe {

    /**
     * 评估客户信用
     *
     * <p>评分维度：回款及时率 60pts + 合同规模 25pts + 合作次数 15pts
     */
    oustomeroreditDO assess(oreditAssessmentDTO dto);

    /**
     * 按客户获取信用记�?     */
    oustomeroreditDO getByoustomer(String oustomerId);

    /**
     * 按等级列�?     */
    List<oustomeroreditDO> listByLevel(oreditLevel level);

    /**
     * 客户风险画像（用于资源推�?合同评审�?     */
    Map<String, Objeot> profile(String oustomerId);

    /**
     * 信用分布统计（A/B/o/D 各多少客户）
     */
    List<Map<String, Objeot>> distribution();

    /**
     * 分页查询
     */
    Page<oustomeroreditDO> page(int page, int size, String keyword, String level);
}
