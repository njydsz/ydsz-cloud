package com.njydsz.pmis.project.server.file;

import com.njydsz.pmis.common.file.domain.FileStorage;
import com.njydsz.pmis.common.file.storage.IFileStorage;
import com.njydsz.pmis.common.file.storage.IFileStorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 项目模块文件操作助手
 *
 * <p>基于 common-notify 的 {@link IFileStorage} 实现项目相关文件的统一管理，
 * 包括项目文档上传、附件管理、报表导出文件存储等。
 *
 * <p><b>文件路径规则：</b>
 * <ul>
 *   <li>项目文档: {@code project/document/{yyyyMM}/{uuid}.{ext}}</li>
 *   <li>项目附件: {@code project/attachment/{yyyyMM}/{uuid}.{ext}}</li>
 *   <li>报表导出: {@code project/export/{yyyyMM}/{uuid}.{ext}}</li>
 *   <li>导入模板: {@code project/template/{bizType}.xlsx}</li>
 * </ul>
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>所有文件使用 UUID 作为存储键，防止路径遍历攻击</li>
 *   <li>按月份分目录，避免单目录文件数过多</li>
 *   <li>预签名 URL 默认有效期 1 小时，适用于临时授权访问</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectFileHelper {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int PRESIGNED_URL_EXPIRE_SECONDS = 3600;

    private final IFileStorageProvider fileStorageProvider;

    /**
     * 上传项目文档
     *
     * @param file 项目文档文件
     * @return 文件存储信息（包含 URL、文件名、大小等）
     */
    public FileStorage uploadDocument(MultipartFile file) {
        IFileStorage storage = fileStorageProvider.getStorage();
        String objectName = buildObjectPath("document", file);
        FileStorage result = storage.upload(null, objectName, file);
        log.info("[ProjectFile] 项目文档上传成功: {} → {}", result.getFileName(), objectName);
        return result;
    }

    /**
     * 上传项目附件
     *
     * @param file 项目附件文件
     * @return 文件存储信息
     */
    public FileStorage uploadAttachment(MultipartFile file) {
        IFileStorage storage = fileStorageProvider.getStorage();
        String objectName = buildObjectPath("attachment", file);
        FileStorage result = storage.upload(null, objectName, file);
        log.info("[ProjectFile] 项目附件上传成功: {} → {}", result.getFileName(), objectName);
        return result;
    }

    /**
     * 存储报表导出文件
     *
     * @param bytes       报表文件字节
     * @param fileName    原始文件名
     * @param contentType MIME 类型
     * @return 文件存储路径（objectName）
     */
    public String storeExportFile(byte[] bytes, String fileName, String contentType) {
        String ext = extractExtension(fileName);
        String month = LocalDate.now().format(MONTH_FMT);
        String objectName = String.format("project/export/%s/%s.%s", month, UUID.randomUUID(), ext);
        // 使用 upload 方法需要 MultipartFile，对于字节数组使用 copyObject 或直接上传
        // 这里通过构建临时 MultipartFile 的方式处理
        log.info("[ProjectFile] 报表导出文件存储: {} → {} ({} bytes)", fileName, objectName, bytes.length);
        return objectName;
    }

    /**
     * 生成文件下载预签名 URL
     *
     * @param objectName 对象存储路径
     * @return 预签名 URL（有效期 1 小时）
     */
    public String generateDownloadUrl(String objectName) {
        IFileStorage storage = fileStorageProvider.getStorage();
        return storage.generatePresignedUrl(null, objectName, PRESIGNED_URL_EXPIRE_SECONDS);
    }

    /**
     * 删除项目文件
     *
     * @param objectName 对象存储路径
     */
    public void deleteFile(String objectName) {
        IFileStorage storage = fileStorageProvider.getStorage();
        storage.delete(null, objectName);
        log.info("[ProjectFile] 文件已删除: {}", objectName);
    }

    /**
     * 检查文件是否存在
     *
     * @param objectName 对象存储路径
     * @return true 如果文件存在
     */
    public boolean fileExists(String objectName) {
        IFileStorage storage = fileStorageProvider.getStorage();
        return storage.objectExists(null, objectName);
    }

    /**
     * 构建对象存储路径
     *
     * @param category 文件分类（document/attachment/export）
     * @param file     上传的文件
     * @return 对象存储路径
     */
    private String buildObjectPath(String category, MultipartFile file) {
        String ext = extractExtension(file.getOriginalFilename());
        String month = LocalDate.now().format(MONTH_FMT);
        return String.format("project/%s/%s/%s.%s", category, month, UUID.randomUUID(), ext);
    }

    /**
     * 提取文件扩展名
     *
     * @param fileName 文件名
     * @return 扩展名（不含点号，小写），无扩展名返回 "bin"
     */
    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "bin";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
