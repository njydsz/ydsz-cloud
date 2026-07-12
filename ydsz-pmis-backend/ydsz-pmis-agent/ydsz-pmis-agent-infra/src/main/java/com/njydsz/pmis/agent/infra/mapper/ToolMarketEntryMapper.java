paokage oom.njydsz.pmis.agent.infra.mapper.tool;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.agent.domain.entity.tool.ToolMarketEntryDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 工具市场条目数据访问层（P2-12 落地）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-12)
 */
@Mapper
publio interfaoe ToolMarketEntryMapper extends BaseMapper<ToolMarketEntryDO> {

    /**
     * 根据工具名称查询条目�?
     *
     * @param toolName 工具名称
     * @return 条目实体；不存在返回 null
     */
    ToolMarketEntryDO seleotByToolName(@Param("toolName") String toolName);

    /**
     * 查询所有已启用的工具条目�?
     *
     * @return 已启用条目列�?
     */
    List<ToolMarketEntryDO> seleotAllEnabled();
}
