package com.njydsz.nextwiki.server.util;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;
import com.njydsz.common.file.util.FileOps;

/**
 * NextWiki 文件工具类 — @deprecated 由 {@link FileOps} 替代，此处仅作向后兼容保留。
 *
 * <p>请直接使用 {@link FileOps} 的门面方法，避免在业务模块中重复实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 使用 {@link FileOps} 替代
 */
@Deprecated
public final class NextwikiFileUtils {

    private NextwikiFileUtils() {
    }

    /** @deprecated 使用 {@link FileOps#extractSuffix(String)} 替代 */
    @Deprecated
    public static String extractSuffix(String filename) {
        return FileOps.extractSuffix(filename);
    }

    /** @deprecated 使用 {@link FileOps#sanitizeFileName(String)} 替代 */
    @Deprecated
    public static String sanitizeFileName(String filename) {
        return FileOps.sanitizeFileName(filename);
    }

    /** @deprecated 使用 {@link FileOps#generateStorageKey(String, String)} 替代 */
    @Deprecated
    public static String generateStorageKey(String namespace, String originalFilename) {
        return FileOps.generateStorageKey(namespace, originalFilename);
    }

    /** @deprecated 使用 {@link FileOps#toMultipartFile(Path, String, String)} 替代 */
    @Deprecated
    public static MultipartFile toMultipartFile(Path filePath, String name, String contentType)
            throws IOException {
        return FileOps.toMultipartFile(filePath, name, contentType);
    }
}
