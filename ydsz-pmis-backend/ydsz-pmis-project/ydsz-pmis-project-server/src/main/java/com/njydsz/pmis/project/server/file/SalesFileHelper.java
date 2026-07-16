package com.njydsz.pmis.project.server.file;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.pmis.common.file.domain.FileStorage;
import com.njydsz.pmis.common.file.storage.IFileStorage;
import com.njydsz.pmis.common.file.storage.IFileStorageProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 销售模块文件操作助手
 *
 * <p>基于 common-file 的 {@link IFileStorage} 实现销售相关文件的统一管理，
 * 包括合同文件上传、补充协议管理、销售方案文档存储等。
 *
 * <p><b>文件路径规则：</b>
 * <ul>
 *   <li>合同文件: {@code sales/contract/{yyyyMM}/{uuid}.{ext}}</li>
 *   <li>补充协议: {@code sales/supplement/{yyyyMM}/{uuid}.{ext}}</li>
 *   <li>销售方案: {@code sales/proposal/{yyyyMM}/{uuid}.{ext}}</li>
 *   <li>客户资质: {@code sales/qualification/{yyyyMM}/{uuid}.{ext}}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesFileHelper {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int PRESIGNED_URL_EXPIRE_SECONDS = 3600;

    private final IFileStorageProvider fileStorageProvider;

    /**
     * 上传合同文件（签章版合同扫描件或电子合同）
     *
     * @param file 合同文件
     * @return 文件存储信息
     */
    public FileStorage uploadContractFile(MultipartFile file) {
        IFileStorage storage = fileStorageProvider.getStorage();
        String objectName = buildObjectPath("contract", file);
        FileStorage result = storage.upload(null, objectName, file);
        log.info("[SalesFile] 合同文件上传成功: {} → {}", result.getFileName(), objectName);
        return result;
    }

    /**
     * 上传补充协议文件
     *
     * @param file 补充协议文件
     * @return 文件存储信息
     */
    public FileStorage uploadSupplementFile(MultipartFile file) {
        IFileStorage storage = fileStorageProvider.getStorage();
        String objectName = buildObjectPath("supplement", file);
        FileStorage result = storage.upload(null, objectName, file);
        log.info("[SalesFile] 补充协议上传成功: {} → {}", result.getFileName(), objectName);
        return result;
    }

    /**
     * 上传销售方案文档
     *
     * @param file 销售方案文件
     * @return 文件存储信息
     */
    public FileStorage uploadProposalFile(MultipartFile file) {
        IFileStorage storage = fileStorageProvider.getStorage();
        String objectName = buildObjectPath("proposal", file);
        FileStorage result = storage.upload(null, objectName, file);
        log.info("[SalesFile] 销售方案上传成功: {} → {}", result.getFileName(), objectName);
        return result;
    }

    /**
     * 上传客户资质文件
     *
     * @param file 客户资质文件
     * @return 文件存储信息
     */
    public FileStorage uploadQualificationFile(MultipartFile file) {
        IFileStorage storage = fileStorageProvider.getStorage();
        String objectName = buildObjectPath("qualification", file);
        FileStorage result = storage.upload(null, objectName, file);
        log.info("[SalesFile] 客户资质上传成功: {} → {}", result.getFileName(), objectName);
        return result;
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
     * 删除销售文件
     *
     * @param objectName 对象存储路径
     */
    public void deleteFile(String objectName) {
        IFileStorage storage = fileStorageProvider.getStorage();
        storage.delete(null, objectName);
        log.info("[SalesFile] 文件已删除: {}", objectName);
    }

    /**
     * 构建对象存储路径
     */
    private String buildObjectPath(String category, MultipartFile file) {
        String ext = extractExtension(file.getOriginalFilename());
        String month = LocalDate.now().format(MONTH_FMT);
        return String.format("sales/%s/%s/%s.%s", category, month, UUID.randomUUID(), ext);
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "bin";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
