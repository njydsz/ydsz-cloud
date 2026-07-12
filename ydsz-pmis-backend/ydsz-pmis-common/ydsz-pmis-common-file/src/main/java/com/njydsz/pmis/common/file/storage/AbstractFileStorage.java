package com.njydsz.pmis.common.file.storage;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.file.callback.UploadProgressListener;
import com.njydsz.pmis.common.file.config.FileProperties;
import com.njydsz.pmis.common.file.config.FileUploadProperties;
import com.njydsz.pmis.common.file.constant.FileConstant;
import com.njydsz.pmis.common.file.domain.BatchDeleteResult;
import com.njydsz.pmis.common.file.domain.ChunkedUploadResult;
import com.njydsz.pmis.common.file.domain.FileStorage;
import com.njydsz.pmis.common.file.domain.ListObjectsResult;
import com.njydsz.pmis.common.file.domain.ObjectMetadata;
import com.njydsz.pmis.common.file.domain.PolicyResult;
import com.njydsz.pmis.common.file.domain.UploadCheckpoint;
import com.njydsz.pmis.common.file.exception.FileExceptionCode;
import com.njydsz.pmis.common.file.util.FileTypeValidator;
import com.njydsz.pmis.common.util.string.StringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 鏂囦欢瀛樺偍鎶借薄鍩虹被
 * <p>灏佽鎵€鏈夊瓨鍌ㄥ疄鐜板叕鍏遍€昏緫锛屽噺灏戝瓙绫婚噸澶嶄唬鐮併€?
 *
 * <p>瀛愮被鍙渶瀹炵幇浠ヤ笅鏍稿績鎶借薄鏂规硶鍗冲彲锛?
 * <ul>
 *   <li>{@link #doBucketExists(String)} - 鍒ゆ柇妗舵槸鍚﹀瓨鍦?/li>
 *   <li>{@link #doMakeBucket(String)} - 鍒涘缓妗?/li>
 *   <li>{@link #doFolderExists(String, String)} - 鍒ゆ柇鐩綍鏄惁瀛樺湪</li>
 *   <li>{@link #doMakeFolder(String, String)} - 鍒涘缓鐩綍</li>
 *   <li>{@link #doPutObject(String, String, InputStream, long, String)} - 鍐欏叆瀵硅薄</li>
 *   <li>{@link #doGetObject(String, String, Long, Long)} - 璇诲彇瀵硅薄</li>
 *   <li>{@link #doRemoveObject(String, String)} - 鍒犻櫎瀵硅薄</li>
 *   <li>{@link #buildObjectUrl(String, String)} - 鏋勫缓瀵硅薄璁块棶鍦板潃</li>
 *   <li>{@link #doInitiateMultipartUpload(String, String)} - 鍒濆鍖栧垎鐗囦笂浼?/li>
 *   <li>{@link #doUploadPart(String, String, String, int, InputStream, long)} - 涓婁紶鍒嗙墖</li>
 *   <li>{@link #doCompleteMultipartUpload(String, String, String, List)} - 瀹屾垚鍒嗙墖涓婁紶</li>
 *   <li>{@link #doAbortMultipartUpload(String, String, String)} - 涓鍒嗙墖涓婁紶</li>
 *   <li>{@link #listParts(String, String, String)} - 鍒椾妇宸蹭笂浼犲垎鐗?/li>
 * </ul>
 *
 * <p>鍏叡鑳藉姏锛?
 * <ul>
 *   <li>bucketName 榛樿鍊艰В鏋愶紙瀛愮被鏃犻渶閲嶅瀹炵幇 formatBucketName锛?/li>
 *   <li>鍒嗙墖涓婁紶鍙傛暟鏍￠獙锛堝瓙绫绘棤闇€閲嶅瀹炵幇 validateMultipartArgs / validateCompleteParts锛?/li>
 *   <li>鍒嗙墖鍚堝苟鍓嶆湇鍔＄鏍￠獙锛堢‘淇濆垎鐗囧畬鏁存€э級</li>
 *   <li>澶辫触鏃惰嚜鍔?abort 娓呯悊</li>
 *   <li>杩涘害鍥炶皟瑙﹀彂锛坥nStart/onProgress/onSuccess/onFailure锛?/li>
 *   <li>璺緞绌胯秺闃叉姢锛坮esolveObjectKey锛?/li>
 *   <li>鍒嗙墖涓婁紶涓婁笅鏂囧拰妫€鏌ョ偣浣跨敤鍒嗗竷寮忓瓨鍌紙Redis锛夛紝鏀寔澶氬疄渚嬪叡浜?/li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see IFileStorage
 */
@Slf4j
public abstract class AbstractFileStorage implements IFileStorage {

    /**
     * 鍒嗙墖涓存椂瀵硅薄鍓嶇紑锛堢敤浜庢爣璇嗕复鏃跺垎鐗囨枃浠讹級
     */
    protected static final String CHUNK_DIR_PREFIX = ".multipart";

    /**
     * 鍒嗙墖鏂囦欢鍚嶆牸寮?
     */
    protected static final String CHUNK_FILE_NAME_FORMAT = "part-%d";

    /**
     * 鍒嗙墖涓婁笅鏂?TTL锛?4 灏忔椂锛?
     */
    private static final long MULTIPART_CONTEXT_TTL_SECONDS = 24 * 3600;

    /**
     * 妫€鏌ョ偣 TTL锛?4 灏忔椂锛?
     */
    private static final long CHECKPOINT_TTL_SECONDS = 24 * 3600;

    /**
     * 瀛樺偍閰嶇疆灞炴€?
     */
    @Getter
    protected final FileProperties fileProperties;

    /**
     * 榛樿瀛樺偍妗跺悕绉?
     */
    @Getter
    protected final String defaultBucket;

    /**
     * 榛樿璁块棶鍩熷悕
     */
    @Getter
    protected final String domain;

    /**
     * 榛樿绔偣鍦板潃
     */
    @Getter
    protected final String endpoint;

    /**
     * 鍒嗙墖涓婁紶涓婁笅鏂囧瓨鍌紙搴曞眰瀛樺偍鎺ュ彛锛?
     */
    protected volatile MultipartContextStore multipartContextStore;

    /**
     * 妫€鏌ョ偣鏈嶅姟锛堥珮灞備笟鍔″皝瑁咃級
     */
    protected volatile CheckpointService checkpointService;

    /**
     * 鍒嗙墖涓婁紶妯℃澘锛堢粍鍚堟柟寮忥紝閬垮厤缁ф壙瀵艰嚧绫昏啫鑳€锛?
     */
    protected AbstractChunkedUploadTemplate chunkedUploadTemplate;

    /**
     * 骞跺彂涓婁紶淇濇姢鍣紙鍙€夛級
     */
    protected UploadConcurrencyGuard concurrencyGuard;

    /**
     * 鍒嗙墖涓婁紶閰嶇疆锛堝彲閫夛紝涓虹┖鏃朵笉浣跨敤 MD5 鏍￠獙锛?
     */
    protected FileUploadProperties fileUploadProperties;

    /**
     * 娴佸紡 MD5 鎽樿鍣紙uploadId 鈫?MessageDigest锛夛紝姣忎笂浼犱竴鐗囧氨鏇存柊鎽樿銆?
     * 浠呯紦瀛?MessageDigest 鐘舵€侊紙绾?128 瀛楄妭锛夛紝鑰岄潪鍘熷鍒嗙墖鏁版嵁锛岄伩鍏嶅ぇ鏂囦欢 OOM銆?
     */
    private final ConcurrentHashMap<String, MessageDigest> chunkedMd5DigestMap =
            new ConcurrentHashMap<>();

    protected AbstractFileStorage(FileProperties fileProperties) {
        this(fileProperties, null);
    }

    protected AbstractFileStorage(FileProperties fileProperties, FileUploadProperties fileUploadProperties) {
        this.fileProperties = fileProperties;
        this.fileUploadProperties = fileUploadProperties;
        this.defaultBucket = fileProperties.getBucket();
        this.domain = fileProperties.getDomain();
        this.endpoint = fileProperties.getEndpoint();
        // 榛樿浣跨敤鍐呭瓨/鏈湴鏂囦欢瀹炵幇鐨勬湇鍔″眰
        this.multipartContextStore = new InMemoryMultipartContextStore();
        CheckpointStore defaultCheckpointStore = new LocalCheckpointStore(fileProperties.getCheckpointDir());
        // 浣跨敤灞€閮ㄦ暟缁勬寔鏈夎€呭欢杩熺粦瀹?this::listParts锛岄伩鍏嶆瀯閫犲櫒涓?this 閫冮€?
        final DefaultCheckpointService.MultipartLister[] listerHolder = new DefaultCheckpointService.MultipartLister[1];
        this.checkpointService = new DefaultCheckpointService(defaultCheckpointStore,
                (bucket, object, uploadId) -> {
                    DefaultCheckpointService.MultipartLister lister = listerHolder[0];
                    return lister != null ? lister.listParts(bucket, object, uploadId)
                            : Collections.emptyList();
                },
                CHECKPOINT_TTL_SECONDS);
        listerHolder[0] = this::listParts;
        // 鍒濆鍖栧垎鐗囦笂浼犳ā鏉匡紙鍩轰簬褰撳墠瀹炰緥鐨?checkpoint 淇濆瓨/鍔犺浇鑳藉姏锛?
        this.chunkedUploadTemplate = createChunkedUploadTemplate();
    }

    /**
     * 璁剧疆鍒嗙墖涓婁紶閰嶇疆
     */
    public void setFileUploadProperties(FileUploadProperties properties) {
        this.fileUploadProperties = properties;
    }

    /**
     * 鏄惁鍚敤鍒嗙墖 MD5 鏍￠獙
     */
    protected boolean isChunkMd5CheckEnabled() {
        return fileUploadProperties != null && fileUploadProperties.isChunkMd5Check();
    }

    /**
     * 璁剧疆鍒嗙墖涓婁紶涓婁笅鏂囧瓨鍌?
     */
    public void setMultipartContextStore(MultipartContextStore store) {
        if (store != null) {
            this.multipartContextStore = store;
        }
    }

    /**
     * 璁剧疆妫€鏌ョ偣鏈嶅姟
     */
    public void setCheckpointService(CheckpointService service) {
        if (service != null) {
            this.checkpointService = service;
            // 閲嶅缓妯℃澘浠ヤ娇鐢ㄦ柊鐨勬湇鍔?
            this.chunkedUploadTemplate = createChunkedUploadTemplate();
        }
    }

    /**
     * 璁剧疆鍒嗙墖涓婁紶妯℃澘
     * <p>瀛愮被鍙敞鍏ヨ嚜瀹氫箟鐨勬ā鏉垮疄鐜?
     */
    public void setChunkedUploadTemplate(AbstractChunkedUploadTemplate template) {
        this.chunkedUploadTemplate = template;
    }

    /**
     * 鍒涘缓榛樿鐨勫垎鐗囦笂浼犳ā鏉垮疄鐜?
     * <p>鍩轰簬 checkpointStore 淇濆瓨/鍔犺浇妫€鏌ョ偣鏁版嵁
     */
    protected final AbstractChunkedUploadTemplate createChunkedUploadTemplate() {
        final AbstractFileStorage self = this;
        return new AbstractChunkedUploadTemplate(CHECKPOINT_TTL_SECONDS * 1000L) {
            @Override
            protected void saveCheckpoint(String bucketName, String objectName, String uploadId,
                                          long partSize, long totalSize, int totalParts,
                                          int completedParts, long expiresAt) {
                // 濮旀墭缁?AbstractFileStorage 鐨勬鏌ョ偣淇濆瓨閫昏緫
                UploadCheckpoint checkpoint = new UploadCheckpoint();
                checkpoint.setUploadId(uploadId);
                checkpoint.setBucketName(bucketName);
                checkpoint.setObjectName(objectName);
                checkpoint.setPartSize(partSize);
                checkpoint.setTotalSize(totalSize);
                checkpoint.setUploadedPartsCount(completedParts);
                self.saveCheckpoint(checkpoint);
            }

            @Override
            protected AbstractChunkedUploadTemplate.ChunkedUploadCheckpoint doLoadCheckpoint(
                    String bucketName, String objectName, String uploadId) {
                UploadCheckpoint loaded = self.loadCheckpoint(bucketName, objectName);
                if (loaded == null) {
                    return null;
                }
                long partSize = loaded.getPartSize() != null ? loaded.getPartSize() : 0;
                long totalSize = loaded.getTotalSize() != null ? loaded.getTotalSize() : 0;
                int totalParts = partSize > 0 && totalSize > 0 ? (int) Math.ceil((double) totalSize / partSize) : 0;
                return new AbstractChunkedUploadTemplate.ChunkedUploadCheckpoint(
                        uploadId,
                        partSize,
                        totalSize,
                        totalParts,
                        loaded.getUploadedPartsCount() != null ? loaded.getUploadedPartsCount() : 0,
                        System.currentTimeMillis() + CHECKPOINT_TTL_SECONDS * 1000L);
            }

            @Override
            protected void deleteCheckpoint(String bucketName, String objectName, String uploadId) {
                UploadCheckpoint checkpoint = new UploadCheckpoint();
                checkpoint.setBucketName(bucketName);
                checkpoint.setObjectName(objectName);
                self.deleteCheckpoint(checkpoint);
            }
        };
    }

    /**
     * 璁剧疆骞跺彂涓婁紶淇濇姢鍣?
     */
    public void setConcurrencyGuard(UploadConcurrencyGuard guard) {
        this.concurrencyGuard = guard;
    }

    /**
     * 娓呯悊杩囨湡鐨勫垎鐗囦笂浼犱笂涓嬫枃
     * <p>寤鸿瀹氭椂璋冪敤锛堝姣忓皬鏃朵竴娆★級娓呯悊瓒呮椂鏈畬鎴愮殑涓婁紶浠诲姟
     *
     * @param timeoutMinutes 瓒呮椂鏃堕棿锛堝垎閽燂級锛岃秴杩囨鏃堕棿鏈洿鏂扮殑涓婁笅鏂囧皢琚竻鐞?
     */
    public void cleanExpiredMultipartContexts(int timeoutMinutes) {
        MultipartContextStore store = multipartContextStore;
        if (store != null) {
            store.cleanExpired(timeoutMinutes);
        }
    }

    @Override
    public boolean bucketExists(String bucketName) {
        String resolvedBucket = resolveBucketName(bucketName);
        if (StringUtils.isBlank(resolvedBucket)) {
            return false;
        }
        return doBucketExists(resolvedBucket);
    }

    @Override
    public void makeBucket(String bucketName) {
        String resolvedBucket = resolveBucketName(bucketName);
        if (StringUtils.isBlank(resolvedBucket)) {
            throw new BusinessException(FileExceptionCode.BUCKET_NOT_FOUND);
        }
        if (!bucketExists(resolvedBucket)) {
            doMakeBucket(resolvedBucket);
        }
    }

    @Override
    public boolean folderExists(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        return doFolderExists(resolvedBucket, resolvedObjectName);
    }

    @Override
    public boolean objectExists(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        try {
            ObjectMetadata metadata = doGetMetadata(resolvedBucket, resolvedObjectName);
            return metadata != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ObjectMetadata getMetadata(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        return doGetMetadata(resolvedBucket, resolvedObjectName);
    }

    @Override
    public void copyObject(String srcBucketName, String srcObjectName, String destBucketName, String destObjectName) {
        String resolvedSrcBucket = resolveBucketName(srcBucketName);
        String resolvedSrcObject = resolveObjectKey(resolvedSrcBucket, srcObjectName);
        String resolvedDestBucket = resolveBucketName(destBucketName);
        String resolvedDestObject = resolveObjectKey(resolvedDestBucket, destObjectName);

        try {
            ObjectMetadata metadata = doGetMetadata(resolvedSrcBucket, resolvedSrcObject);
            if (metadata == null) {
                throw new BusinessException(FileExceptionCode.FILE_NOT_FOUND);
            }
            String contentType = metadata.getContentType() != null ? metadata.getContentType() : "application/octet-stream";
            long size = metadata.getSize();
            try (InputStream is = doGetObject(resolvedSrcBucket, resolvedSrcObject, null, null)) {
                doPutObject(resolvedDestBucket, resolvedDestObject, is, size, contentType);
            }
            log.info("[Storage] copyObject success, src={}/{}, dest={}/{}",
                    resolvedSrcBucket, resolvedSrcObject, resolvedDestBucket, resolvedDestObject);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Storage] copyObject failed, src={}/{}, dest={}/{}, message={}",
                    resolvedSrcBucket, resolvedSrcObject, resolvedDestBucket, resolvedDestObject, e.getMessage());
            throw new BusinessException(FileExceptionCode.OBJECT_COPY_FAILED);
        }
    }

    @Override
    public void moveObject(String srcBucketName, String srcObjectName, String destBucketName, String destObjectName) {
        copyObject(srcBucketName, srcObjectName, destBucketName, destObjectName);
        delete(srcBucketName, srcObjectName);
        log.info("[Storage] moveObject success, src={}/{}, dest={}/{}",
                resolveBucketName(srcBucketName), resolveObjectKey(resolveBucketName(srcBucketName), srcObjectName),
                resolveBucketName(destBucketName), resolveObjectKey(resolveBucketName(destBucketName), destObjectName));
    }

    @Override
    public ListObjectsResult listObjects(String bucketName, String prefix, String cursor, int maxKeys) {
        String resolvedBucket = resolveBucketName(bucketName);
        return doListObjects(resolvedBucket, prefix, cursor, maxKeys);
    }

    @Override
    public void makeFolder(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        if (!folderExists(resolvedBucket, resolvedObjectName)) {
            doMakeFolder(resolvedBucket, resolvedObjectName);
        }
    }

    @Override
    public FileStorage upload(String bucketName, String objectName, MultipartFile file) {
        return upload(bucketName, objectName, file, null);
    }

    @Override
    public FileStorage upload(String bucketName, String objectName, MultipartFile file, UploadProgressListener listener) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        if (file.isEmpty()) {
            throw new BusinessException(FileExceptionCode.FILE_EMPTY);
        }

        FileTypeValidator.validate(file);

        // 鑾峰彇骞跺彂涓婁紶閿?
        String lockToken = acquireConcurrencyLock(resolvedObjectName);

        makeBucket(resolvedBucket);
        FileStorage fileStorage = buildFileStorage(file);
        long totalBytes = file.getSize();

        if (listener != null) {
            listener.onStart(totalBytes);
        }

        try (InputStream inputStream = file.getInputStream()) {
            doPutObject(resolvedBucket, resolvedObjectName, inputStream,
                    file.getSize(), file.getContentType());

            fileStorage.setUuidName(resolvedObjectName);
            fileStorage.setUrl(buildObjectUrl(resolvedBucket, resolvedObjectName));

            if (listener != null) {
                listener.onSuccess(resolvedObjectName);
            }
            return fileStorage;
        } catch (BusinessException e) {
            if (listener != null) {
                listener.onFailure(resolvedObjectName, e);
            }
            throw e;
        } catch (Exception e) {
            log.error("[Storage] file upload failed, bucket={}, object={}, message={}",
                    resolvedBucket, resolvedObjectName, e.getMessage(), e);
            if (listener != null) {
                listener.onFailure(resolvedObjectName, e);
            }
            throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
        } finally {
            releaseConcurrencyLock(resolvedObjectName, lockToken);
        }
    }

    @Override
    public void delete(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        if (StringUtils.isEmpty(resolvedObjectName)) {
            throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
        }

        try {
            doRemoveObject(resolvedBucket, resolvedObjectName);
        } catch (Exception e) {
            log.error("[Storage] file delete failed, bucket={}, object={}, message={}",
                    resolvedBucket, resolvedObjectName, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.FILE_DELETE_FAILED);
        }
    }

    @Override
    public BatchDeleteResult batchDelete(String bucketName, List<String> objectNames) {
        if (objectNames == null || objectNames.isEmpty()) {
            return BatchDeleteResult.allSuccess(Collections.emptyList());
        }
        List<String> successList = new ArrayList<>();
        Map<String, String> failedMap = new ConcurrentHashMap<>();
        for (String objectName : objectNames) {
            try {
                delete(bucketName, objectName);
                successList.add(objectName);
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                failedMap.put(objectName, errorMsg);
                log.error("[Storage] batch delete failed, object={}, message={}", objectName, errorMsg, e);
            }
        }
        return new BatchDeleteResult(List.copyOf(successList), Map.copyOf(failedMap));
    }

    @Override
    public void download(String bucketName, String objectName, HttpServletResponse response) {
        download(bucketName, objectName, response, null, null);
    }

    @Override
    public void download(String bucketName, String objectName, HttpServletResponse response, Long offset, Long length) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        try (InputStream is = doGetObject(resolvedBucket, resolvedObjectName, offset, length);
             OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
            log.info("[Storage] file download success, bucket={}, object={}", resolvedBucket, resolvedObjectName);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Storage] file download failed, bucket={}, object={}, message={}",
                    resolvedBucket, resolvedObjectName, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }

    @Override
    public String getPublicUrl(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        return buildObjectUrl(resolvedBucket, resolvedObjectName);
    }

    @Override
    public String getPrivateUrl(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        return buildPrivateUrl(resolvedBucket, resolvedObjectName);
    }

    @Override
    public String generatePresignedUrl(String objectKey, int expireSeconds) {
        return generatePresignedUrl(null, objectKey, expireSeconds);
    }

    @Override
    public String generatePresignedUrl(String bucketName, String objectKey, int expireSeconds) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectKey = resolveObjectKey(resolvedBucket, objectKey);
        return doGeneratePresignedUrl(resolvedBucket, resolvedObjectKey, expireSeconds);
    }

    @Override
    public InputStream downloadAsStream(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        try {
            return doGetObject(resolvedBucket, resolvedObjectName, null, null);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Storage] downloadAsStream failed, bucket={}, object={}, message={}",
                    resolvedBucket, resolvedObjectName, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }

    @Override
    public ChunkedUploadResult initiateChunkedUpload(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        makeBucket(resolvedBucket);
        ChunkedUploadResult result = doInitiateMultipartUpload(resolvedBucket, resolvedObjectName);

        multipartContextStore.save(result.getUploadId(),
                new MultipartContextStore.MultipartContextData(result.getUploadId(), resolvedBucket, resolvedObjectName),
                MULTIPART_CONTEXT_TTL_SECONDS);

        log.info("[Storage] chunked upload initiated, bucket={}, object={}, uploadId={}",
                resolvedBucket, resolvedObjectName, result.getUploadId());
        return result;
    }

    @Override
    public void uploadChunk(String bucketName, String objectName, String uploadId, int partNumber, MultipartFile file) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        validateUploadId(uploadId);
        validatePartNumber(partNumber);

        try {
            byte[] chunkData = file.getBytes();

            // 娴佸紡鏇存柊 MD5 鎽樿锛屼粎缂撳瓨 MessageDigest 鐘舵€佽€岄潪鍘熷鏁版嵁锛岄伩鍏?OOM
            String chunkMd5 = null;
            if (isChunkMd5CheckEnabled()) {
                // 璁＄畻鍒嗙墖 MD5锛堢敤浜庢牎楠岋級
                chunkMd5 = UploadCheckpoint.calculateMd5(chunkData);
                
                // 娴佸紡鏇存柊鏁翠綋鏂囦欢 MD5 鎽樿
                MessageDigest digest = chunkedMd5DigestMap.computeIfAbsent(
                        uploadId, k -> createMessageDigest());
                digest.update(chunkData);
            }

            String chunkObjectName = buildChunkObjectName(resolvedObjectName, uploadId, partNumber);
            doUploadPart(resolvedBucket, chunkObjectName, uploadId, partNumber,
                    new java.io.ByteArrayInputStream(chunkData), file.getSize());

            MultipartContextStore.MultipartContextData context = multipartContextStore.get(uploadId);
            if (context == null) {
                context = new MultipartContextStore.MultipartContextData(uploadId, resolvedBucket, resolvedObjectName);
            }
            Map<Integer, String> partChunkNames = new ConcurrentHashMap<>(context.partChunkNames());
            partChunkNames.put(partNumber, chunkObjectName);
            multipartContextStore.save(uploadId,
                    new MultipartContextStore.MultipartContextData(
                            context.uploadId(), context.bucketName(), context.objectName(),
                            partChunkNames, context.createTime(), System.currentTimeMillis()),
                    MULTIPART_CONTEXT_TTL_SECONDS);

            // 鏇存柊妫€鏌ョ偣涓殑鍒嗙墖 MD5
            if (isChunkMd5CheckEnabled() && chunkMd5 != null) {
                checkpointService.updateChunkMd5InCheckpoint(resolvedBucket, resolvedObjectName, partNumber, chunkMd5, chunkData.length);
            }

            log.info("[Storage] chunk uploaded, bucket={}, object={}, part={}",
                    resolvedBucket, resolvedObjectName, partNumber);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Storage] uploadChunk failed, bucket={}, object={}, part={}, message={}",
                    resolvedBucket, resolvedObjectName, partNumber, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    @Override
    public void completeChunkedUpload(String bucketName, String objectName, String uploadId, List<Integer> partNumbers) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        validateUploadId(uploadId);
        validatePartNumbers(partNumbers);

        MultipartContextStore.MultipartContextData context = multipartContextStore.get(uploadId);
        if (context == null || !resolvedObjectName.equals(context.objectName())) {
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }

        Set<Integer> uniqueParts = new HashSet<>(partNumbers);
        List<Integer> sortedParts = new ArrayList<>(uniqueParts);
        Collections.sort(sortedParts);

        List<PartInfo> uploadedParts = listParts(resolvedBucket, resolvedObjectName, uploadId);

        for (Integer partNumber : sortedParts) {
            boolean found = uploadedParts.stream()
                    .anyMatch(p -> p.partNumber() == partNumber);
            if (!found) {
                throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
            }
        }

        try {
            doCompleteMultipartUpload(resolvedBucket, resolvedObjectName, uploadId, sortedParts);

            // 鍩轰簬娴佸紡 MessageDigest 璁＄畻绱Н MD5锛岄伩鍏嶇紦瀛樺師濮嬪垎鐗囨暟鎹鑷?OOM
            if (isChunkMd5CheckEnabled()) {
                MessageDigest digest = chunkedMd5DigestMap.get(uploadId);
                if (digest != null) {
                    String accumulatedMd5 = bytesToHex(digest.digest());
                    checkpointService.updateAccumulatedMd5(resolvedBucket, resolvedObjectName, accumulatedMd5);
                    log.debug("[Storage] computed accumulated MD5 via streaming digest, uploadId={}, md5={}", uploadId, accumulatedMd5);
                }
            }

            safeAbortMultipartUpload(resolvedBucket, resolvedObjectName, uploadId);
            multipartContextStore.remove(uploadId);
            chunkedMd5DigestMap.remove(uploadId);

            // 鍒嗙墖涓婁紶瀹屾垚鍚庯紝鍩轰簬 fileMd5 鏍￠獙鏂囦欢瀹屾暣鎬?
            if (isChunkMd5CheckEnabled()) {
                checkpointService.validateFileMd5(resolvedBucket, resolvedObjectName, true,
                        this::computeMd5,
                        (b, o) -> doGetObject(b, o, null, null));
            }

            log.info("[Storage] chunked upload completed, bucket={}, object={}, parts={}",
                    resolvedBucket, resolvedObjectName, sortedParts.size());
        } catch (BusinessException e) {
            safeAbortMultipartUpload(resolvedBucket, resolvedObjectName, uploadId);
            multipartContextStore.remove(uploadId);
            chunkedMd5DigestMap.remove(uploadId);
            throw e;
        } catch (Exception e) {
            safeAbortMultipartUpload(resolvedBucket, resolvedObjectName, uploadId);
            multipartContextStore.remove(uploadId);
            chunkedMd5DigestMap.remove(uploadId);
            log.error("[Storage] completeChunkedUpload failed, bucket={}, object={}, message={}",
                    resolvedBucket, resolvedObjectName, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }
    }

    /**
     * 灏嗗瓨鍌ㄦ《鍚嶇О瑙ｆ瀽涓哄疄闄呬娇鐢ㄧ殑鍊?
     * <p>褰撲紶鍏ュ€间负绌烘椂锛屼娇鐢ㄩ厤缃枃浠朵腑鐨勯粯璁ゆ《鍚嶇О
     *
     * @param bucketName 瀛樺偍妗跺悕绉帮紙鍙负 null锛?
     * @return 瑙ｆ瀽鍚庣殑瀛樺偍妗跺悕绉?
     */
    protected String resolveBucketName(String bucketName) {
        return StringUtils.isNotBlank(bucketName) ? bucketName : defaultBucket;
    }

    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile("(\\.\\.)|(%2e%2e)|(%2E%2E)");

    /**
     * 杞箟 JSON 瀛楃涓蹭腑鐨勭壒娈婂瓧绗?
     * <p>鐢ㄤ簬瀹夊叏鍦板皢瀛楃涓插祵鍏?JSON 鏂囨湰涓紝闃叉娉ㄥ叆銆?
     *
     * @param value 寰呰浆涔夌殑瀛楃涓?
     * @return 杞箟鍚庣殑瀛楃涓?
     */
    protected static String escapeJsonString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 鏍￠獙璺緞鏄惁鍦ㄥ畨鍏ㄧ洰褰曡寖鍥村唴锛岄槻姝㈢洰褰曠┛瓒婃敾鍑?
     * <p>瀹夊叏鏍￠獙瑙勫垯锛?
     * <ul>
     *   <li>浣跨敤 {@code Paths.normalize()} 瑙勮寖鍖栬矾寰?/li>
     *   <li>鏍￠獙瑙勮寖鍖栧悗鐨勮矾寰勪互 baseDir 涓哄墠缂€</li>
     *   <li>鎷掔粷绌哄瓧鑺傘€佹帶鍒跺瓧绗︾瓑寮傚父杈撳叆</li>
     * </ul>
     *
     * @param path    寰呮牎楠岀殑鏂囦欢璺緞
     * @param baseDir 鍏佽鐨勫熀纭€鐩綍
     * @return true 琛ㄧず璺緞瀹夊叏
     */
    protected static boolean isSafePath(String path, String baseDir) {
        if (StringUtils.isBlank(path) || StringUtils.isBlank(baseDir)) {
            return false;
        }
        try {
            Path basePath = Paths.get(baseDir).normalize().toAbsolutePath();
            Path resolvedPath = basePath.resolve(path).normalize().toAbsolutePath();
            return resolvedPath.startsWith(basePath);
        } catch (Exception e) {
            log.warn("[Storage] path validation failed, path={}, baseDir={}, message={}",
                    path, baseDir, e.getMessage());
            return false;
        }
    }

    /**
     * 瑙ｆ瀽骞舵牎楠屽璞¤矾寰勶紝闃叉璺緞绌胯秺鏀诲嚮
     * <p>鏍￠獙瑙勫垯锛?
     * <ul>
     *   <li>璺緞涓嶈兘涓虹┖</li>
     *   <li>绂佹鍖呭惈绌哄瓧鑺?{@code \0} 鍙婃帶鍒跺瓧绗?/li>
     *   <li>绂佹鍖呭惈 {@code ..} 璺緞绌胯秺绗︼紙鍚?URL 缂栫爜褰㈠紡锛?/li>
     *   <li>瑙勮寖鍖栬矾寰勫悗绂佹浠?{@code ..} 浣滀负璺緞娈?/li>
     *   <li>浣跨敤 {@code Paths.normalize()} 杩涜浜屾鏍￠獙</li>
     * </ul>
     *
     * @param bucketName 瀛樺偍妗跺悕绉?
     * @param objectName 瀵硅薄璺緞
     * @return 瑙ｆ瀽鍚庣殑瀵硅薄璺緞
     * @throws BusinessException 褰撹矾寰勪负绌烘垨瀛樺湪瀹夊叏椋庨櫓鏃?
     */
    protected final String resolveObjectKey(String bucketName, String objectName) {
        if (StringUtils.isEmpty(objectName)) {
            throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
        }
        if (objectName.indexOf('\0') >= 0) {
            log.warn("[Storage] null byte detected in objectName={}", objectName);
            throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
        }
        if (PATH_TRAVERSAL_PATTERN.matcher(objectName).find()) {
            log.warn("[Storage] path traversal detected, objectName={}", objectName);
            throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
        }
        String resolved = objectName;
        if (!resolved.startsWith("/")) {
            resolved = "/" + resolved;
        }
        String normalized = resolved.replace("\\", "/").replaceAll("/+", "/");
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                log.warn("[Storage] path traversal after normalization, objectName={}", objectName);
                throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
            }
        }
        try {
            String canonicalPath = Paths.get(normalized).normalize().toString();
            if (!canonicalPath.equals(normalized) && canonicalPath.contains("..")) {
                log.warn("[Storage] path traversal after canonical normalization, objectName={}, normalized={}, canonical={}",
                        objectName, normalized, canonicalPath);
                throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
            }
        } catch (Exception e) {
            log.warn("[Storage] path canonicalization failed, objectName={}, message={}",
                    objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
        }
        return normalizeObjectKey(normalized);
    }

    protected String normalizeObjectKey(String objectKey) {
        return objectKey;
    }

    /**
     * 妫€娴嬫枃浠剁殑 MIME Type
     * <p>浼樺厛浣跨敤 MultipartFile.getContentType()锛岃嫢涓虹┖鍒欓€氳繃
     * java.net.URLConnection.guessContentTypeFromStream() 鍩轰簬鏂囦欢澶撮瓟鏁版娴嬨€?
     *
     * @param file 涓婁紶鐨勬枃浠?
     * @return MIME Type锛屾棤娉曟娴嬫椂杩斿洖 application/octet-stream
     */
    protected String detectMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (StringUtils.isNotBlank(contentType)) {
            return contentType;
        }
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            is.mark(32);
            String guessed = URLConnection.guessContentTypeFromStream(is);
            if (StringUtils.isNotBlank(guessed)) {
                return guessed;
            }
        } catch (Exception e) {
            log.debug("[Storage] MIME Type detection failed for file: {}, message={}",
                    file.getOriginalFilename(), e.getMessage());
        }
        // fallback: 鍩轰簬鍚庣紑鎺ㄦ柇
        String suffix = "";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf(FileConstant.SUFFIX_SPLIT);
            if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
                suffix = originalFilename.substring(dotIndex + 1).toLowerCase();
            }
        }
        String mapped = URLConnection.guessContentTypeFromName("file." + suffix);
        return mapped != null ? mapped : "application/octet-stream";
    }

    /**
     * 浠?FileStorage 鏋勫缓 MultipartFile 鐨勬枃浠朵俊鎭璞?
     * <p>瀛愮被鍙鐩栨鏂规硶浠ヨ嚜瀹氫箟 FileStorage 鐨勬瀯寤洪€昏緫
     *
     * @param file 涓婁紶鐨勬枃浠?
     * @return 鏂囦欢瀛樺偍淇℃伅瀵硅薄
     */
    protected FileStorage buildFileStorage(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || StringUtils.isBlank(originalFilename)) {
            throw new BusinessException(FileExceptionCode.FILE_NAME_INVALID);
        }

        int dotIndex = originalFilename.lastIndexOf(FileConstant.SUFFIX_SPLIT);
        if (dotIndex < 0) {
            throw new BusinessException(FileExceptionCode.FILE_NAME_INVALID);
        }

        String suffix = originalFilename.substring(dotIndex + 1).toLowerCase();
        if (!isAllowedSuffix(suffix)) {
            throw new BusinessException(FileExceptionCode.FILE_SUFFIX_NOT_ALLOWED);
        }

        FileStorage fileStorage = new FileStorage();
        fileStorage.setFileName(originalFilename);
        fileStorage.setSuffix(suffix);
        fileStorage.setSize(file.getSize());
        fileStorage.setIsDir(0);
        fileStorage.setIsImage(isImageSuffix(suffix) ? 1 : 0);
        fileStorage.setIsVideo(isVideoSuffix(suffix) ? 1 : 0);
        fileStorage.setIsAudio(isAudioSuffix(suffix) ? 1 : 0);
        fileStorage.setIsOffice(isOfficeSuffix(suffix) ? 1 : 0);
        fileStorage.setIsCode(isCodeSuffix(suffix) ? 1 : 0);
        fileStorage.setType(isCodeSuffix(suffix) ? "code" : suffix);
        fileStorage.setMimeType(detectMimeType(file));
        fileStorage.setUploadAt(LocalDateTime.now());
        return fileStorage;
    }

    /**
     * 妫€鏌ユ枃浠跺悗缂€鏄惁鍏佽涓婁紶
     *
     * @param suffix 鏂囦欢鍚庣紑锛堜笉鍚偣锛?
     * @return true 鍏佽涓婁紶
     */
    protected boolean isAllowedSuffix(String suffix) {
        List<String> allowedSuffixes = fileProperties.getAllowedSuffixes();
        if (allowedSuffixes == null || allowedSuffixes.isEmpty()) {
            return true;
        }
        return allowedSuffixes.stream()
                .map(String::toLowerCase)
                .anyMatch(s -> s.equalsIgnoreCase(suffix));
    }

    private static final Set<String> IMAGE_SUFFIXES = Set.of(
            "png", "bmp", "jpg", "jpeg", "gif", "svg", "ico", "webp");

    private static final Set<String> VIDEO_SUFFIXES = Set.of(
            "mp4", "flv", "avi", "mkv", "mov", "wmv", "3gp");

    private static final Set<String> AUDIO_SUFFIXES = Set.of(
            "mp3", "wma", "wav", "flac", "aac", "ogg");

    private static final Set<String> OFFICE_SUFFIXES = Set.of(
            "txt", "md", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "csv");

    private static final Set<String> CODE_SUFFIXES = Set.of(
            "java", "sql", "js", "py", "php", "vue", "sh", "css", "html", "htm", "xml", "json");

    /**
     * 妫€鏌ユ槸鍚︿负鍥剧墖鏂囦欢鍚庣紑
     */
    protected boolean isImageSuffix(String suffix) {
        return suffix != null && IMAGE_SUFFIXES.contains(suffix.toLowerCase());
    }

    /**
     * 妫€鏌ユ槸鍚︿负瑙嗛鏂囦欢鍚庣紑
     */
    protected boolean isVideoSuffix(String suffix) {
        return suffix != null && VIDEO_SUFFIXES.contains(suffix.toLowerCase());
    }

    /**
     * 妫€鏌ユ槸鍚︿负闊抽鏂囦欢鍚庣紑
     */
    protected boolean isAudioSuffix(String suffix) {
        return suffix != null && AUDIO_SUFFIXES.contains(suffix.toLowerCase());
    }

    /**
     * 妫€鏌ユ槸鍚︿负鍔炲叕鏂囨。鍚庣紑
     */
    protected boolean isOfficeSuffix(String suffix) {
        return suffix != null && OFFICE_SUFFIXES.contains(suffix.toLowerCase());
    }

    /**
     * 妫€鏌ユ槸鍚︿负浠ｇ爜鏂囦欢鍚庣紑
     */
    protected boolean isCodeSuffix(String suffix) {
        return suffix != null && CODE_SUFFIXES.contains(suffix.toLowerCase());
    }

    /**
     * 鏍￠獙涓婁紶 ID 鏍煎紡
     */
    protected void validateUploadId(String uploadId) {
        if (StringUtils.isBlank(uploadId) || uploadId.length() > 64) {
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    /**
     * 鏍￠獙鍒嗙墖缂栧彿
     */
    protected void validatePartNumber(int partNumber) {
        if (partNumber <= 0) {
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    /**
     * 鏍￠獙鍒嗙墖缂栧彿鍒楄〃
     */
    protected void validatePartNumbers(List<Integer> partNumbers) {
        if (partNumbers == null || partNumbers.isEmpty()) {
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }
        for (Integer partNumber : partNumbers) {
            if (partNumber == null || partNumber <= 0) {
                throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
            }
        }
    }

    /**
     * 鏋勫缓鍒嗙墖瀵硅薄鍚嶇О
     */
    protected String buildChunkObjectName(String objectName, String uploadId, int partNumber) {
        return CHUNK_DIR_PREFIX + FileConstant.DIR_SPLIT +
                objectName + FileConstant.DIR_SPLIT +
                uploadId + FileConstant.DIR_SPLIT +
                String.format(CHUNK_FILE_NAME_FORMAT, partNumber);
    }

    /**
     * 瀹夊叏涓鍒嗙墖涓婁紶锛堝け璐ユ椂娓呯悊璧勬簮锛?
     */
    protected void safeAbortMultipartUpload(String bucketName, String objectName, String uploadId) {
        if (StringUtils.isBlank(uploadId)) {
            return;
        }
        try {
            doAbortMultipartUpload(bucketName, objectName, uploadId);
        } catch (Exception e) {
            log.warn("[Storage] abort multipart upload failed, bucket={}, object={}, uploadId={}, message={}",
                    bucketName, objectName, uploadId, e.getMessage());
        }
    }

    /**
     * 鍒涘缓鐩綍锛堜互 / 缁撳熬鐨?0 瀛楄妭瀵硅薄锛?
     */
    protected void createFolderByEmptyObject(String bucketName, String folderName) {
        try (InputStream emptyStream = new ByteArrayInputStream(new byte[]{})) {
            doPutObject(bucketName, folderName, emptyStream, 0L, "application/directory");
        } catch (Exception e) {
            throw new BusinessException(FileExceptionCode.FOLDER_CREATE_FAILED);
        }
    }

    @Override
    public PolicyResult generateUploadPolicy(String bucketName, String objectNamePrefix, Integer expires) {
        log.warn("[Storage] generateUploadPolicy is not supported for this storage type");
        return null;
    }

    @Override
    public UploadCheckpoint initChunkedUploadWithCheckpoint(String bucketName, String objectName, MultipartFile file) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        if (file.isEmpty()) {
            throw new BusinessException(FileExceptionCode.FILE_EMPTY);
        }

        makeBucket(resolvedBucket);

        UploadCheckpoint existingCheckpoint = loadCheckpoint(resolvedBucket, resolvedObjectName);

        if (existingCheckpoint != null && existingCheckpoint.getUploadId() != null) {
            UploadCheckpoint loadedCheckpoint = validateAndRecoverCheckpoint(existingCheckpoint, file);
            if (loadedCheckpoint != null) {
                log.info("[Storage] recovered existing checkpoint, bucket={}, object={}, uploadId={}, uploadedParts={}",
                        resolvedBucket, resolvedObjectName, loadedCheckpoint.getUploadId(), loadedCheckpoint.getUploadedPartsCount());
                return loadedCheckpoint;
            }
        }

        ChunkedUploadResult chunkResult = initiateChunkedUpload(resolvedBucket, resolvedObjectName);

        long fileSize = file.getSize();
        long partSize = fileProperties.getPartSize() != null ? fileProperties.getPartSize() : 5242880L;

        UploadCheckpoint checkpoint = new UploadCheckpoint();
        checkpoint.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        checkpoint.setBucketName(resolvedBucket);
        checkpoint.setObjectName(resolvedObjectName);
        checkpoint.setUploadId(chunkResult.getUploadId());
        checkpoint.setTotalSize(fileSize);
        checkpoint.setFileName(file.getOriginalFilename());
        checkpoint.setContentType(file.getContentType());
        checkpoint.setPartSize(partSize);
        checkpoint.setCreateTime(LocalDateTime.now());
        checkpoint.setLastModifyTime(LocalDateTime.now());
        checkpoint.setUploadedBytes(0L);
        checkpoint.setUploadedPartsCount(0);
        checkpoint.setUploadedParts(new ArrayList<>());

        // 鍒濆鍖栨椂璁＄畻鏂囦欢 MD5
        if (isChunkMd5CheckEnabled()) {
            try {
                String fileMd5 = UploadCheckpoint.calculateMd5(file.getBytes());
                checkpoint.setFileMd5(fileMd5);
            } catch (Exception e) {
                log.warn("[Storage] initChunkedUploadWithCheckpoint fileMd5 compute failed, message={}", e.getMessage());
            }
        }

        saveCheckpoint(checkpoint);

        return checkpoint;
    }

    @Override
    public FileStorage resumeChunkedUpload(UploadCheckpoint checkpoint, UploadProgressListener listener) {
        if (checkpoint == null || StringUtils.isBlank(checkpoint.getUploadId())) {
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }

        String bucketName = checkpoint.getBucketName();
        String objectName = checkpoint.getObjectName();
        String uploadId = checkpoint.getUploadId();

        if (listener != null) {
            listener.onStart(checkpoint.getTotalSize());
        }

        try {
            long uploadedBytes = checkpoint.getUploadedBytes() != null ? checkpoint.getUploadedBytes() : 0;
            if (listener != null) {
                listener.onProgress(uploadedBytes, checkpoint.getTotalSize());
            }

            completeChunkedUpload(bucketName, objectName, uploadId,
                    checkpoint.getUploadedParts().stream()
                            .map(UploadCheckpoint.UploadedPart::getPartNumber)
                            .sorted()
                            .toList());

            deleteCheckpoint(checkpoint);

            FileStorage fileStorage = new FileStorage();
            fileStorage.setUuidName(objectName);
            fileStorage.setUrl(getPublicUrl(bucketName, objectName));
            fileStorage.setSize(checkpoint.getTotalSize());
            fileStorage.setFileName(checkpoint.getFileName());

            if (listener != null) {
                listener.onSuccess(objectName);
            }

            return fileStorage;
        } catch (Exception e) {
            if (listener != null) {
                listener.onFailure(objectName, e);
            }
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }
    }

    @Override
    public UploadCheckpoint getCheckpoint(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        return loadCheckpoint(resolvedBucket, resolvedObjectName);
    }

    /**
     * 淇濆瓨涓婁紶妫€鏌ョ偣
     *
     * @param checkpoint 妫€鏌ョ偣鏁版嵁
     */
    protected void saveCheckpoint(UploadCheckpoint checkpoint) {
        CheckpointService service = checkpointService;
        if (service != null) {
            service.saveCheckpoint(checkpoint);
        }
    }

    /**
     * 鍔犺浇涓婁紶妫€鏌ョ偣
     *
     * @param bucketName 瀛樺偍妗跺悕绉?
     * @param objectName 瀵硅薄鍚嶇О
     * @return 妫€鏌ョ偣鏁版嵁锛屼笉瀛樺湪鏃惰繑鍥?null
     */
    protected UploadCheckpoint loadCheckpoint(String bucketName, String objectName) {
        CheckpointService service = checkpointService;
        if (service != null) {
            return service.loadCheckpoint(bucketName, objectName);
        }
        return null;
    }

    /**
     * 鏍￠獙骞舵仮澶嶄笂浼犳鏌ョ偣
     *
     * @param checkpoint 宸叉湁妫€鏌ョ偣
     * @param file 涓婁紶鏂囦欢
     * @return 鏍￠獙鍚庣殑妫€鏌ョ偣
     */
    protected UploadCheckpoint validateAndRecoverCheckpoint(UploadCheckpoint checkpoint, org.springframework.web.multipart.MultipartFile file) {
        CheckpointService service = checkpointService;
        if (service != null) {
            return service.validateAndRecoverCheckpoint(checkpoint, file);
        }
        return checkpoint;
    }

    @Override
    public void deleteCheckpoint(UploadCheckpoint checkpoint) {
        CheckpointService service = checkpointService;
        if (service != null) {
            service.deleteCheckpoint(checkpoint);
        }
    }

    /**
     * 鑾峰彇骞跺彂涓婁紶閿?
     *
     * @param objectKey 鏂囦欢瀵硅薄閿?
     * @return 閿佷护鐗岋紝鐢ㄤ簬閲婃斁閿?
     */
    protected String acquireConcurrencyLock(String objectKey) {
        if (concurrencyGuard != null) {
            return concurrencyGuard.acquire(objectKey);
        }
        return null;
    }

    /**
     * 閲婃斁骞跺彂涓婁紶閿?
     *
     * @param objectKey  鏂囦欢瀵硅薄閿?
     * @param lockToken  閿佷护鐗?
     */
    protected void releaseConcurrencyLock(String objectKey, String lockToken) {
        if (concurrencyGuard != null && lockToken != null) {
            try {
                concurrencyGuard.release(objectKey, lockToken);
            } catch (Exception e) {
                log.warn("[Storage] releaseConcurrencyLock failed, object={}, error={}", objectKey, e.getMessage());
            }
        }
    }

    // ==================== MD5 鏍￠獙杈呭姪鏂规硶 ====================

    /**
     * 鍒涘缓 MD5 鎽樿瀹炰緥
     *
     * @return MessageDigest 瀹炰緥
     */
    private static MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create MD5 MessageDigest", e);
        }
    }

    /**
     * 璁＄畻杈撳叆娴佺殑 MD5锛堜細娑堣垂娴侊紝璋冪敤鑰呴渶鑷閲嶆柊鑾峰彇娴侊級
     */
    protected String computeMd5(InputStream inputStream) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            byte[] digest = md.digest();
            return bytesToHex(digest);
        } catch (Exception e) {
            log.warn("[Storage] computeMd5 failed, message={}", e.getMessage());
            return null;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ==================== 鎶借薄鏂规硶锛屽瓙绫诲繀椤诲疄鐜?====================

    /**
     * 鍒ゆ柇瀛樺偍妗舵槸鍚﹀瓨鍦?
     *
     * @param bucketName 宸茶В鏋愮殑瀛樺偍妗跺悕绉?
     * @return true 琛ㄧず瀛樺湪
     */
    protected abstract boolean doBucketExists(String bucketName);

    /**
     * 鍒涘缓瀛樺偍妗?
     *
     * @param bucketName 宸茶В鏋愮殑瀛樺偍妗跺悕绉?
     */
    protected abstract void doMakeBucket(String bucketName);

    /**
     * 鍒ゆ柇鐩綍鏄惁瀛樺湪
     *
     * @param bucketName 宸茶В鏋愮殑瀛樺偍妗跺悕绉?
     * @param folderName 宸茶В鏋愮殑鐩綍鍚嶇О
     * @return true 琛ㄧず瀛樺湪
     */
    protected abstract boolean doFolderExists(String bucketName, String folderName);

    /**
     * 鑾峰彇瀵硅薄鍏冧俊鎭?
     *
     * @param bucketName 宸茶В鏋愮殑瀛樺偍妗跺悕绉?
     * @param objectName 宸茶В鏋愮殑瀵硅薄鍚嶇О
     * @return 瀵硅薄鍏冧俊鎭紝鑻ヤ笉瀛樺湪杩斿洖 null
     */
    protected abstract ObjectMetadata doGetMetadata(String bucketName, String objectName);

    /**
     * 鍒嗛〉鍒椾妇瀵硅薄
     *
     * @param bucketName 宸茶В鏋愮殑瀛樺偍妗跺悕绉?
     * @param prefix    瀵硅薄鍓嶇紑杩囨护
     * @param cursor    鍒嗛〉娓告爣
     * @param maxKeys   姣忛〉鏈€澶ц繑鍥炴暟閲?
     * @return 鍒嗛〉缁撴灉
     */
    protected abstract ListObjectsResult doListObjects(String bucketName, String prefix, String cursor, int maxKeys);

    /**
     * 鍒涘缓鐩綍
     *
     * @param bucketName 宸茶В鏋愮殑瀛樺偍妗跺悕绉?
     * @param folderName 鐩綍鍚嶇О锛堝簲纭繚浠?/ 缁撳熬锛?
     */
    protected abstract void doMakeFolder(String bucketName, String folderName);

    /**
     * 鍐欏叆瀵硅薄鍒板瓨鍌?
     *
     * @param bucketName  瀛樺偍妗跺悕绉?
     * @param objectName  瀵硅薄璺緞
     * @param inputStream 鏁版嵁杈撳叆娴?
     * @param size        鏁版嵁澶у皬
     * @param contentType 鍐呭绫诲瀷
     */
    protected abstract void doPutObject(String bucketName, String objectName,
                                        InputStream inputStream, long size, String contentType);

    /**
     * 璇诲彇瀵硅薄鍐呭
     *
     * @param bucketName 瀛樺偍妗跺悕绉?
     * @param objectName 瀵硅薄璺緞
     * @param offset     璧峰鍋忕Щ锛坣ull 琛ㄧず浠?0 寮€濮嬶級
     * @param length     璇诲彇闀垮害锛坣ull 琛ㄧず璇诲彇鍏ㄩ儴锛?
     * @return 杈撳叆娴?
     */
    protected abstract InputStream doGetObject(String bucketName, String objectName,
                                               Long offset, Long length);

    /**
     * 鍒犻櫎瀵硅薄
     *
     * @param bucketName 瀛樺偍妗跺悕绉?
     * @param objectName 瀵硅薄璺緞
     */
    protected abstract void doRemoveObject(String bucketName, String objectName);

    /**
     * 鏋勫缓鍏紑璁块棶 URL
     *
     * @param bucketName 瀛樺偍妗跺悕绉?
     * @param objectName 瀵硅薄璺緞
     * @return 鍏紑璁块棶 URL
     */
    protected abstract String buildObjectUrl(String bucketName, String objectName);

    /**
     * 鏋勫缓绉佹湁绛惧悕璁块棶 URL
     *
     * @param bucketName 瀛樺偍妗跺悕绉?
     * @param objectName 瀵硅薄璺緞
     * @return 绉佹湁绛惧悕 URL锛堜笉鏀寔鏃惰繑鍥炵┖瀛楃涓诧級
     */
    protected String buildPrivateUrl(String bucketName, String objectName) {
        return "";
    }

    /**
     * 鐢熸垚棰勭鍚?URL锛堝彲鑷畾涔夎繃鏈熸椂闂达級
     *
     * <p>榛樿瀹炵幇杩斿洖鍏紑璁块棶 URL銆傚瓙绫诲彲瑕嗙洊姝ゆ柟娉曚互浣跨敤浜戝巶鍟?SDK 鐢熸垚绛惧悕 URL銆?
     *
     * @param bucketName    瀛樺偍妗跺悕绉?
     * @param objectName    瀵硅薄璺緞
     * @param expireSeconds 杩囨湡鏃堕棿锛堢锛?
     * @return 棰勭鍚?URL
     */
    protected String doGeneratePresignedUrl(String bucketName, String objectName, int expireSeconds) {
        return buildObjectUrl(bucketName, objectName);
    }

    /**
     * 鍒濆鍖栧垎鐗囦笂浼?
     *
     * @param bucketName 瀛樺偍妗跺悕绉?
     * @param objectName 瀵硅薄璺緞
     * @return 鍒嗙墖涓婁紶缁撴灉锛堝寘鍚?uploadId锛?
     */
    protected abstract ChunkedUploadResult doInitiateMultipartUpload(String bucketName, String objectName);

    /**
     * 涓婁紶鍗曚釜鍒嗙墖
     *
     * @param bucketName    瀛樺偍妗跺悕绉?
     * @param chunkObjectName 鍒嗙墖瀵硅薄鍚嶇О
     * @param uploadId      鍒嗙墖浠诲姟 ID
     * @param partNumber    鍒嗙墖缂栧彿
     * @param inputStream   鍒嗙墖鏁版嵁娴?
     * @param size          鍒嗙墖澶у皬
     */
    protected abstract void doUploadPart(String bucketName, String chunkObjectName,
                                         String uploadId, int partNumber,
                                         InputStream inputStream, long size);

    /**
     * 瀹屾垚鍒嗙墖涓婁紶骞跺悎骞?
     *
     * @param bucketName  瀛樺偍妗跺悕绉?
     * @param objectName  瀵硅薄璺緞
     * @param uploadId    鍒嗙墖浠诲姟 ID
     * @param partNumbers 宸蹭笂浼犵殑鍒嗙墖缂栧彿鍒楄〃锛堝崌搴忥級
     */
    protected abstract void doCompleteMultipartUpload(String bucketName, String objectName,
                                                       String uploadId, List<Integer> partNumbers);

    /**
     * 涓鍒嗙墖涓婁紶锛堟竻鐞嗗凡涓婁紶鐨勫垎鐗囷級
     *
     * @param bucketName 瀛樺偍妗跺悕绉?
     * @param objectName 瀵硅薄璺緞
     * @param uploadId   鍒嗙墖浠诲姟 ID
     */
    protected abstract void doAbortMultipartUpload(String bucketName, String objectName, String uploadId);

    /**
     * 鍒椾妇宸蹭笂浼犵殑鍒嗙墖
     *
     * @param bucketName 瀛樺偍妗跺悕绉?
     * @param objectName 瀵硅薄璺緞
     * @param uploadId   鍒嗙墖浠诲姟 ID
     * @return 宸蹭笂浼犲垎鐗囦俊鎭垪琛?
     */
    protected abstract List<PartInfo> listParts(String bucketName, String objectName, String uploadId);
}
