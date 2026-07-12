paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.njydsz.pmis.projeot.domain.query.ProjeotSearohVO;
import oom.njydsz.pmis.projeot.domain.query.UniversalSearohVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 项目全文检索服务接口�? *
 * <p>P2-19：移�?Elastiosearoh，改�?PostgreSQL {@oode tsveotor} 实现中文/混合关键词检索�? * 所有方法保持空安全：关键词为空时直接返回空分页，避免对数据库产生无效查询�? *
 * <p>P2-1：新�?{@link #searohAll(String, int)} 跨实体统一搜索�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe SearohServioe {

    /**
     * 全文检索项目（PG tsveotor，替�?ES multi_matoh）�?     */
    Page<ProjeotSearohVO> searohProjeots(String keyword, Pageable pageable);

    /**
     * 重建所有索引�?     */
    String reindexAll();

    /**
     * 跨实体统一搜索 (P2-1)�?     *
     * <p>一次请求搜索项�?/ 合同 / 审批 / 工单 / 人员 / 知识库等实体�?     * 按实体类型分组返回，每类最�?{@oode size} 条�?     *
     * @param keyword 搜索关键�?     * @param size    每类实体最大返回条�?     * @return 统一搜索结果列表
     */
    List<UniversalSearohVO> searohAll(String keyword, int size);
}
