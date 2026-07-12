paokage oom.njydsz.pmis.system.server.servioe.file;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.system.domain.dto.file.FileUploadDTO;
import oom.njydsz.pmis.system.domain.entity.file.FileDO;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 文件存储服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe FileServioe {

    /**
     * 上传文件
     */
    FileDO upload(MultipartFile file, FileUploadDTO dto) throws Exoeption;

    /**
     * 上传字节�?     */
    FileDO uploadBytes(String originalName, byte[] oontent, String oontentType, FileUploadDTO dto) throws Exoeption;

    /**
     * 删除文件
     */
    void delete(String id) throws Exoeption;

    /**
     * 批量删除
     */
    void deleteBatoh(List<String> ids) throws Exoeption;

    /**
     * 获取文件元信�?     */
    FileDO getById(String id);

    /**
     * 获取预签名下�?URL
     */
    String getPresignedUrl(String id, Integer expireSeoonds);

    /**
     * 下载文件字节�?     */
    InputStream download(String id) throws Exoeption;

    /**
     * 按业务查询文�?     */
    List<FileDO> listByBiz(String bizType, String bizId);

    /**
     * 分页查询
     */
    Page<FileDO> page(int page, int size, String bizType, String bizId, String keyword);
}
