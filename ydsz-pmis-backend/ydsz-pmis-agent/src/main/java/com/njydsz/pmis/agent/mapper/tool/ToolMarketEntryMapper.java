package com.njydsz.pmis.agent.mapper.tool;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.tool.ToolMarketEntryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工具市场条目数据访问层（P2-12 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-12)
 */
@Mapper
public interface ToolMarketEntryMapper extends BaseMapper<ToolMarketEntryDO> {

    /**
     * 根据工具名称查询条目。
     *
     * @param toolName 工具名称
     * @return 条目实体；不存在返回 null
     */
    ToolMarketEntryDO selectByToolName(@Param("toolName") String toolName);

    /**
     * 查询所有已启用的工具条目。
     *
     * @return 已启用条目列表
     */
    List<ToolMarketEntryDO> selectAllEnabled();
}
