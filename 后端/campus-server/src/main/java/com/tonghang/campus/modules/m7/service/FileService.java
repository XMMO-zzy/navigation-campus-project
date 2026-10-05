package com.tonghang.campus.modules.m7.service;

import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.modules.m7.entity.FileObject;
import com.tonghang.campus.modules.m7.mapper.FileObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件上传服务（M7）。
 *
 * <p>来源：《13-API接口文档》§3.7「POST /files/upload 文件上传
 * （返回 file_id/url + 审核状态，≤10MB，白名单）」— FR-M7-04。
 *
 * <p>实现说明（P0 降级策略）：
 * <ul>
 *   <li>对象存储（腾讯云 COS）凭证未接入时，{@code campus.file.cos-enabled=false}，
 *       仅落库 file_object 记录并返回本地占位 URL，不臆造云存储返回；</li>
 *   <li>类型白名单与大小限制按《13》§3.7 与《18》隐私合规要求做硬校验（超限 → 40010）；</li>
 *   <li>审核状态默认 0 待审，机审接入后由 admin 侧回写 audit_status。</li>
 * </ul>
 */
@Service
public class FileService {

    /** 允许的图片 MIME 白名单（《13》§3.7） */
    private static final List<String> ALLOWED_MIME = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif");

    /** 最大 10MB（《13》§3.7 与《12》§2.2「单图 ≤10MB」一致） */
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private final FileObjectMapper fileObjectMapper;

    /** 对象存储是否就绪（未接入时为 false，走本地占位 URL） */
    @Value("${campus.file.cos-enabled:false}")
    private boolean cosEnabled;

    /** 本地占位 URL 前缀（cosEnabled=false 时使用） */
    @Value("${campus.file.local-prefix:/static/upload/}")
    private String localPrefix;

    public FileService(FileObjectMapper fileObjectMapper) {
        this.fileObjectMapper = fileObjectMapper;
    }

    /**
     * 登记一个已上传的文件。
     *
     * <p>前端先直传对象存储、拿到 url 后调本接口登记；或由本接口代理上传。
     * P0 阶段由前端传 url（直传）或走本地占位。
     *
     * @param userId  上传者
     * @param bizType 业务类型，见 {@code Enums.FileBiz}
     * @param url     文件访问地址（可空，空则生成本地占位）
     * @param size    字节数
     * @param mime    MIME
     * @param objectKey 对象键（可空）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(Long userId, Integer bizType, String url,
                                        Long size, String mime, String objectKey) {
        if (bizType == null) {
            throw BizException.param("缺少 bizType（文件业务类型）");
        }
        if (size != null && size > MAX_SIZE) {
            throw new BizException(ErrorCode.FILE_ILLEGAL, "文件大小不能超过 10MB");
        }
        if (mime != null && !mime.trim().isEmpty() && !ALLOWED_MIME.contains(mime.trim().toLowerCase())) {
            throw new BizException(ErrorCode.FILE_ILLEGAL, "仅支持 jpg / png / webp / gif 格式");
        }

        String finalUrl = url;
        String finalKey = objectKey;
        if (finalUrl == null || finalUrl.trim().isEmpty()) {
            if (cosEnabled) {
                throw new BizException(ErrorCode.THIRD_PARTY_ERROR,
                        "对象存储已启用但未返回 url，请检查直传结果");
            }
            // 本地占位：不伪造云地址，仅给出可识别的本地路径
            finalKey = "local/" + userId + "/" + System.currentTimeMillis();
            finalUrl = localPrefix + finalKey;
        }

        FileObject f = new FileObject();
        f.setUserId(userId);
        f.setBizType(bizType);
        f.setUrl(finalUrl.trim());
        f.setObjectKey(finalKey);
        f.setSize(size == null ? 0L : size);
        f.setMime(mime);
        f.setAuditStatus(FileObject.AUDIT_PENDING);
        fileObjectMapper.insert(f);

        Map<String, Object> data = new LinkedHashMap<>(5);
        data.put("fileId", f.getId());
        data.put("url", f.getUrl());
        data.put("auditStatus", f.getAuditStatus());
        data.put("size", f.getSize());
        return data;
    }

    /** 取文件（供其它模块回读校验；不存在返回 null） */
    public FileObject get(Long fileId) {
        return fileId == null ? null : fileObjectMapper.selectById(fileId);
    }

    /** 文件是否可用（审核未拦截） */
    public boolean usable(Long fileId) {
        FileObject f = get(fileId);
        return f != null && (f.getAuditStatus() == null
                || f.getAuditStatus() != FileObject.AUDIT_REJECTED);
    }

    /** 文件上传业务类型名（供文档/前端字典对齐，见 Enums.FileBiz） */
    public static int bizAvatar() {
        return Enums.FileBiz.AVATAR;
    }

    /** 校验 fileId 列表均可（多图上传场景） */
    public void requireUsable(List<Long> fileIds) {
        if (fileIds == null) {
            return;
        }
        for (Long id : fileIds) {
            if (!usable(id)) {
                throw new BizException(ErrorCode.FILE_ILLEGAL, "素材文件无效或未通过审核：fileId=" + id);
            }
        }
    }
}
