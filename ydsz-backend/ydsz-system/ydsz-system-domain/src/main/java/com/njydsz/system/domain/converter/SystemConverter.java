package com.njydsz.system.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.njydsz.system.domain.entity.AppInfo;
import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.domain.entity.DictItem;
import com.njydsz.system.domain.entity.DictType;
import com.njydsz.system.domain.entity.DictVersion;
import com.njydsz.system.domain.entity.Variable;
import com.njydsz.system.domain.vo.AppInfoVO;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.domain.vo.DictVersionVO;
import com.njydsz.system.domain.vo.VariableVO;

/**
 * 系统配置模块统一 MapStruct 转换器。
 *
 * <p>提供所有系统模块 Entity → VO 的转换方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface SystemConverter {

    SystemConverter INSTANT = Mappers.getMapper(SystemConverter.class);

    // ===== AppInfo =====
    AppInfoVO entityToVO(AppInfo entity);
    List<AppInfoVO> appInfoListToVO(List<AppInfo> entities);

    // ===== Config =====
    ConfigVO entityToVO(Config entity);
    List<ConfigVO> configListToVO(List<Config> entities);

    // ===== DictItem =====
    DictItemVO entityToVO(DictItem entity);
    List<DictItemVO> dictItemListToVO(List<DictItem> entities);

    // ===== DictType =====
    DictTypeVO entityToVO(DictType entity);
    List<DictTypeVO> dictTypeListToVO(List<DictType> entities);

    // ===== DictVersion =====
    DictVersionVO entityToVO(DictVersion entity);
    List<DictVersionVO> dictVersionListToVO(List<DictVersion> entities);

    // ===== Variable =====
    VariableVO entityToVO(Variable entity);
    List<VariableVO> variableListToVO(List<Variable> entities);
}
