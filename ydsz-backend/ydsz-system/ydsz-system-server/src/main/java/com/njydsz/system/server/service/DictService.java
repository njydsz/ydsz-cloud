package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.entity.DictTypeDO;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.vo.DictTypeVO;

/**
 * 字典类型 Service。
 *
 * <p>继承通用 CRUD 能力，并提供全量列表查询等扩展能力。
 *
 * @author ydsz-team
 */
public interface DictService extends BaseCrudService<DictTypeDO, DictTypeDTO, DictTypeVO, DictPageQuery, String> {

    /**
     * 查询全部字典类型（不区分状态）。
     *
     * @return 字典类型列表
     */
    List<DictTypeVO> listAll();
}
