package com.njydsz.system.server.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.entity.VariableDO;
import com.njydsz.system.domain.vo.VariableVO;

/**
 * 系统变量 Service。
 *
 * @author ydsz-team
 */
public interface VariableService {

    VariableVO getById(String id);

    IPage<VariableDO> page(int pageNum, int pageSize);

    List<VariableDO> list();

    String save(VariableDTO dto);

    boolean updateById(VariableDTO dto);

    boolean removeById(String id);
}
