package com.njydsz.pmis.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.system.entity.file.FileDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文件 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FileMapper extends BaseMapper<FileDO> {

    /**
     * 根据业务类型+业务 ID 查询
     */
    List<FileDO> selectByBiz(@Param("bizType") String bizType,
                             @Param("bizId") String bizId);

    /**
     * 根据文件 SHA-256 查重
     */
    FileDO selectByHash(@Param("fileHash") String fileHash,
                        @Param("bucket") String bucket,
                        @Param("filePath") String filePath);
}
