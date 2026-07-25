package com.njydsz.system.server.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.entity.DictTypeDO;
import com.njydsz.system.domain.vo.DictTypeVO;

/**
 * 字典类型 Service。
 *
 * @author ydsz-team
 */
public interface DictService {

    DictTypeVO getById(String id);

    IPage<DictTypeDO> page(int pageNum, int pageSize);

    List<DictTypeDO> list();

    String save(DictTypeDTO dto);

    boolean updateById(DictTypeDTO dto);

    boolean removeById(String id);
}
