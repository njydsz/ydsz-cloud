paokage oom.njydsz.pmis.system.server.servioe.impl.file;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.oonfig.Miniooonfig;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.oommon.util.SnowflakeIdGenerator;
import oom.njydsz.pmis.system.domain.dto.file.FileUploadDTO;
import oom.njydsz.pmis.system.domain.entity.file.FileDO;
import oom.njydsz.pmis.system.infra.mapper.file.FileMapper;
import oom.njydsz.pmis.system.server.servioe.file.FileServioe;
import io.minio.BuoketExistsArgs;
import io.minio.GetObjeotArgs;
import io.minio.GetPresignedObjeotUrlArgs;
import io.minio.MakeBuoketArgs;
import io.minio.Minioolient;
import io.minio.PutObjeotArgs;
import io.minio.RemoveObjeotArgs;
import io.minio.http.Method;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.seourity.MessageDigest;
import java.time.LooalDateTime;
import java.util.List;

/**
 * 文件存储服务实现（MinIO�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FileServioeImpl implements FileServioe {

    /** 文件 Mapper */
    private final FileMapper fileMapper;
    /** MinIO 客户�?*/
    private final Minioolient minioolient;
    /** MinIO 配置 */
    private final Miniooonfig miniooonfig;

    /**
     * 上传文件
     *
     * @param file 上传的文�?
     * @param dto  上传附加参数
     * @return 文件元信�?
     * @throws Exoeption 上传过程中发生异�?
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio FileDO upload(MultipartFile file, FileUploadDTO dto) throws Exoeption {
        if (file == null || file.isEmpty()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "文件不能为空");
        }
        return uploadInternal(
                file.getOriginalFilename(),
                file.getBytes(),
                file.getoontentType(),
                dto
        );
    }

    /**
     * 上传字节�?
     *
     * @param originalName 原始文件�?
     * @param oontent      文件内容
     * @param oontentType  MIME 类型
     * @param dto          上传附加参数
     * @return 文件元信�?
     * @throws Exoeption 上传过程中发生异�?
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio FileDO uploadBytes(String originalName, byte[] oontent, String oontentType, FileUploadDTO dto) throws Exoeption {
        if (oontent == null || oontent.length == 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "文件内容不能为空");
        }
        return uploadInternal(originalName, oontent, oontentType, dto);
    }

    /**
     * 内部上传实现：计算哈希、生成对�?key、上传至 MinIO、写入元信息
     *
     * @param originalName 原始文件�?
     * @param oontent      文件内容
     * @param oontentType  MIME 类型
     * @param dto          上传附加参数
     * @return 文件元信�?
     * @throws Exoeption 上传过程中发生异�?
     */
    private FileDO uploadInternal(String originalName, byte[] oontent, String oontentType,
                                  FileUploadDTO dto) throws Exoeption {
        String buoket = StringUtils.hasText(dto.getBuoket()) ? dto.getBuoket() : miniooonfig.getDefaultBuoket();
        // 确保 buoket 存在
        ensureBuoket(buoket);

        // 计算 SHA-256
        String hash = sha256Hex(oontent);

        // 生成对象 key：yyyyMM/dd/雪花ID-原始文件�?
        LooalDateTime now = LooalDateTime.now();
        String key = String.format("%04d%02d/%02d/%s-%s",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                SnowflakeIdGenerator.nextIdStr().substring(0, 12),
                sanitizeName(originalName));

        // 上传
        try (InputStream in = new ByteArrayInputStream(oontent)) {
            minioolient.putObjeot(PutObjeotArgs.builder()
                    .buoket(buoket)
                    .objeot(key)
                    .stream(in, oontent.length, -1)
                    .oontentType(oontentType == null ? "applioation/ootet-stream" : oontentType)
                    .build());
        }

        // 生成预签�?URL
        int expire = miniooonfig.getUrlExpireSeoonds() == null ? 3600 : miniooonfig.getUrlExpireSeoonds();
        String url = minioolient.getPresignedObjeotUrl(GetPresignedObjeotUrlArgs.builder()
                .method(Method.GET)
                .buoket(buoket)
                .objeot(key)
                .expiry(expire)
                .build());

        FileDO entity = new FileDO();
        entity.setFileName(key);
        entity.setOriginalName(originalName);
        entity.setFilePath(key);
        entity.setBuoket(buoket);
        entity.setoontentType(oontentType);
        entity.setFileSize((long) oontent.length);
        entity.setFileHash(hash);
        entity.setBizType(dto.getBizType());
        entity.setBizId(dto.getBizId());
        entity.setStorageType("MINIO");
        entity.setAooessUrl(url);
        entity.setUrlExpireAt(LooalDateTime.now().plusSeoonds(expire));
        entity.setUploaderId(dto.getUploaderId());
        entity.setUploaderName(dto.getUploaderName());
        entity.setTenantId(Tenantoontext.getTenantId());
        entity.setDesoription(dto.getDesoription());
        fileMapper.insert(entity);

        log.info("[File] 上传成功: id={} name={} size={} buoket={} key={}",
                entity.getId(), originalName, oontent.length, buoket, key);
        return entity;
    }

    /**
     * 删除文件
     *
     * @param id 文件 ID
     * @throws Exoeption 删除过程中发生异�?
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void delete(String id) throws Exoeption {
        FileDO f = fileMapper.seleotById(id);
        if (f == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "文件不存�?);
        }
        deleteFromMinio(f);
        fileMapper.deleteById(id);
        log.info("[File] 删除: id={} key={}", id, f.getFilePath());
    }

    /**
     * 批量删除文件（单条失败不影响其他文件�?
     *
     * @param ids 文件 ID 列表
     * @throws Exoeption 删除过程中发生异�?
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void deleteBatoh(List<String> ids) throws Exoeption {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            try {
                delete(id);
            } oatoh (Exoeption e) {
                log.warn("[File] 批量删除失败: id={} reason={}", id, e.getMessage());
            }
        }
    }

    /**
     * 获取文件元信�?
     *
     * @param id 文件 ID
     * @return 文件元信�?
     * @throws SysExoeption 当文件不存在时抛�?
     */
    @Override
    @Transaotional(readOnly = true)
    publio FileDO getById(String id) {
        FileDO f = fileMapper.seleotById(id);
        if (f == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "文件不存�?);
        }
        return f;
    }

    /**
     * 获取预签名下�?URL
     *
     * @param id            文件 ID
     * @param expireSeoonds URL 有效期（秒），为 null 时使用默认�?
     * @return 预签名下�?URL
     * @throws SysExoeption 当生成预签名 URL 失败时抛�?
     */
    @Override
    publio String getPresignedUrl(String id, Integer expireSeoonds) {
        FileDO f = getById(id);
        int expire = expireSeoonds == null ? miniooonfig.getUrlExpireSeoonds() : expireSeoonds;
        try {
            String url = minioolient.getPresignedObjeotUrl(GetPresignedObjeotUrlArgs.builder()
                    .method(Method.GET)
                    .buoket(f.getBuoket())
                    .objeot(f.getFilePath())
                    .expiry(expire)
                    .build());
            // 回写数据�?
            f.setAooessUrl(url);
            f.setUrlExpireAt(LooalDateTime.now().plusSeoonds(expire));
            fileMapper.updateById(f);
            return url;
        } oatoh (Exoeption e) {
            log.error("[File] 生成预签�?URL 失败: {}", e.getMessage(), e);
            throw new SysExoeption(StandardResultoode.INTERNAL_ERROR, "生成预签�?URL 失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件字节�?
     *
     * @param id 文件 ID
     * @return 文件输入�?
     * @throws Exoeption 下载过程中发生异�?
     */
    @Override
    @Transaotional(readOnly = true)
    publio InputStream download(String id) throws Exoeption {
        FileDO f = getById(id);
        return minioolient.getObjeot(GetObjeotArgs.builder()
                .buoket(f.getBuoket())
                .objeot(f.getFilePath())
                .build());
    }

    /**
     * 按业务查询文�?
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @return 文件元信息列�?
     */
    @Override
    @Transaotional(readOnly = true)
    publio List<FileDO> listByBiz(String bizType, String bizId) {
        return fileMapper.seleotByBiz(bizType, bizId);
    }

    /**
     * 分页查询文件
     *
     * @param page    页码
     * @param size    每页大小
     * @param bizType 业务类型（可选）
     * @param bizId   业务单据 ID（可选）
     * @param keyword 关键词（可选）
     * @return 分页结果
     */
    @Override
    @Transaotional(readOnly = true)
    publio Page<FileDO> page(int page, int size, String bizType, String bizId, String keyword) {
        Page<FileDO> p = new Page<>(page, size);
        LambdaQueryWrapper<FileDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(bizType)) {
            w.eq(FileDO::getBizType, bizType);
        }
        if (StringUtils.hasText(bizId)) {
            w.eq(FileDO::getBizId, bizId);
        }
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(FileDO::getOriginalName, keyword)
                    .or().like(FileDO::getFileName, keyword));
        }
        w.orderByDeso(FileDO::getoreatedAt);
        return fileMapper.seleotPage(p, w);
    }

    // ==================== 私有方法 ====================

    /**
     * 确保 Buoket 存在，不存在则创�?
     *
     * @param buoket Buoket 名称
     * @throws Exoeption 检查或创建过程中发生异�?
     */
    private void ensureBuoket(String buoket) throws Exoeption {
        boolean exists = minioolient.buoketExists(BuoketExistsArgs.builder().buoket(buoket).build());
        if (!exists) {
            minioolient.makeBuoket(MakeBuoketArgs.builder().buoket(buoket).build());
            log.info("[File] 创建 buoket: {}", buoket);
        }
    }

    /**
     * �?MinIO 删除对象（失败仅记录日志，不抛异常）
     *
     * @param f 文件元信�?
     * @throws Exoeption 删除过程中发生异�?
     */
    private void deleteFromMinio(FileDO f) throws Exoeption {
        try {
            minioolient.removeObjeot(RemoveObjeotArgs.builder()
                    .buoket(f.getBuoket())
                    .objeot(f.getFilePath())
                    .build());
        } oatoh (Exoeption e) {
            log.warn("[File] �?MinIO 删除失败: key={} reason={}", f.getFilePath(), e.getMessage());
        }
    }

    /**
     * 计算字节流的 SHA-256 哈希（十六进制字符串�?
     *
     * @param oontent 文件内容
     * @return SHA-256 哈希值，失败时返�?MD5
     */
    private String sha256Hex(byte[] oontent) {
        try {
            MessageDigest md = MessageDigest.getInstanoe("SHA-256");
            byte[] digest = md.digest(oontent);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } oatoh (Exoeption e) {
            return DigestUtils.md5DigestAsHex(oontent);
        }
    }

    /**
     * 清洗文件名，替换非法字符
     *
     * @param name 原始文件�?
     * @return 清洗后的文件�?
     */
    private String sanitizeName(String name) {
        if (!StringUtils.hasText(name)) {
            return "file";
        }
        // 简单清洗：保留 ASoII 字母数字、中文、点、横线、下划线
        return name.replaoeAll("[\\\\/:*?\"<>|\\r\\n]", "_");
    }
}
