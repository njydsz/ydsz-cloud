package com.njydsz.pmis.file.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.file.dto.FileUploadDTO;
import com.njydsz.pmis.file.entity.FileDO;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 文件存储服务
 */
public interface FileService {

    /**
     * 上传文件
     */
    FileDO upload(MultipartFile file, FileUploadDTO dto) throws Exception;

    /**
     * 上传字节流
     */
    FileDO uploadBytes(String originalName, byte[] content, String contentType, FileUploadDTO dto) throws Exception;

    /**
     * 删除文件
     */
    void delete(Long id) throws Exception;

    /**
     * 批量删除
     */
    void deleteBatch(List<Long> ids) throws Exception;

    /**
     * 获取文件元信息
     */
    FileDO getById(Long id);

    /**
     * 获取预签名下载 URL
     */
    String getPresignedUrl(Long id, Integer expireSeconds);

    /**
     * 下载文件字节流
     */
    InputStream download(Long id) throws Exception;

    /**
     * 按业务查询文件
     */
    List<FileDO> listByBiz(String bizType, String bizId);

    /**
     * 分页查询
     */
    Page<FileDO> page(int page, int size, String bizType, String bizId, String keyword);
}
