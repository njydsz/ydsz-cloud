package com.njydsz.pmis.finance.server.file;

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
 * 财务模块文件操作助手
 *
 * <p>基于 common-file 的 {@link IFileStorage} 实现财务相关文件的统一管理，
 * 包括发票扫描件上传、付款凭证管理、财务报表导出等。
 *
 * <p><b>文件路径规则：</b>
 * <ul>
 *   <li>发票文件: {@code finance/invoice/{yyyyMM}/{uuid}.{ext}}</li>
 *   <li>付款凭证: {@code finance/payment/{yyyyMM}/{uuid}.{ext}}</li>
 *   <li>对账单: {@code finance/reconcile/{yyyyMM}/{uuid}.{ext}}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanceFileHelper {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int PRESIGNED_URL_EXPIRE_SECONDS = 3600;

    private final IFileStorageProvider fileStorageProvider;

    /**
     * 上传发票扫描件/电子发票
     *
     * @param file 发票文件
     * @return 文件存储信息
     */
    public FileStorage uploadInvoiceFile(MultipartFile file) {
        IFileStorage storage = fileStorageProvider.getStorage();
        String objectName = buildObjectPath("invoice", file);
        FileStorage result = storage.upload(null, objectName, file);
        log.info("[FinanceFile] 发票文件上传成功: {} → {}", result.getFileName(), objectName);
        return result;
    }

    /**
     * 上传付款凭证
     *
     * @param file 付款凭证文件
     * @return 文件存储信息
     */
    public FileStorage uploadPaymentVoucher(MultipartFile file) {
        IFileStorage storage = fileStorageProvider.getStorage();
        String objectName = buildObjectPath("payment", file);
        FileStorage result = storage.upload(null, objectName, file);
        log.info("[FinanceFile] 付款凭证上传成功: {} → {}", result.getFileName(), objectName);
        return result;
    }

    /**
     * 上传对账单
     *
     * @param file 对账单文件
     * @return 文件存储信息
     */
    public FileStorage uploadReconcileFile(MultipartFile file) {
        IFileStorage storage = fileStorageProvider.getStorage();
        String objectName = buildObjectPath("reconcile", file);
        FileStorage result = storage.upload(null, objectName, file);
        log.info("[FinanceFile] 对账单上传成功: {} → {}", result.getFileName(), objectName);
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
     * 删除财务文件
     *
     * @param objectName 对象存储路径
     */
    public void deleteFile(String objectName) {
        IFileStorage storage = fileStorageProvider.getStorage();
        storage.delete(null, objectName);
        log.info("[FinanceFile] 文件已删除: {}", objectName);
    }

    /**
     * 构建对象存储路径
     */
    private String buildObjectPath(String category, MultipartFile file) {
        String ext = extractExtension(file.getOriginalFilename());
        String month = LocalDate.now().format(MONTH_FMT);
        return String.format("finance/%s/%s/%s.%s", category, month, UUID.randomUUID(), ext);
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "bin";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
