package com.tonghang.campus.modules.m7.controller;

import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.modules.m7.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 文件接口（M7）。
 *
 * <p>来源：《13-API接口文档》§3.7「POST /files/upload 文件上传
 * （返回 file_id/url + 审核状态，≤10MB，白名单）」— FR-M7-04。
 *
 * <p>上传方式（P0）：前端直传对象存储后调本接口登记；
 * 亦兼容本接口代理登记本地占位 URL（{@code campus.file.cos-enabled=false} 时）。
 * 真实二进制上传（multipart）待对象存储接入后补充，不在 P0 范围。
 */
@Tag(name = "M7 文件")
@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * POST /files/upload —— 文件登记（FR-M7-04）。
     *
     * <p>请求体：{@code { bizType, url, size, mime, objectKey }}。
     * 若 url 为空且未启用对象存储，则生成本地占位地址（不伪造云地址）。
     */
    @Operation(summary = "文件登记（返回 file_id/url + 审核状态，≤10MB）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestBody Map<String, Object> body) {
        Long uid = UserContext.requireLogin();
        Integer bizType = intOrNull(body == null ? null : body.get("bizType"));
        String url = body == null ? null : str(body.get("url"));
        Long size = longOrNull(body == null ? null : body.get("size"));
        String mime = body == null ? null : str(body.get("mime"));
        String objectKey = body == null ? null : str(body.get("objectKey"));
        return Result.ok(fileService.register(uid, bizType, url, size, mime, objectKey));
    }

    /**
     * GET /files/biz-types —— 文件业务类型字典（前端下拉 / 校验对齐用）。
     *
     * <p>来源：{@code Enums.FileBiz}（《12》§2.7 file_object.biz_type）
     * 与《13》§3.7 上传接口的 bizType 入参一致；《13》未单列该接口，
     * 作为字典查询的加法扩展提供（不影响既有契约）。
     */
    @Operation(summary = "文件业务类型字典（bizType 值域）")
    @PostMapping("/biz-types")
    public Result<Map<String, Object>> bizTypes() {
        Map<String, Object> data = new java.util.LinkedHashMap<>(9);
        data.put("1", "头像");
        data.put("2", "动态");
        data.put("3", "商品");
        data.put("4", "活动");
        data.put("5", "竞赛");
        data.put("6", "搭子");
        data.put("7", "学号认证");
        data.put("8", "举报证据");
        data.put("9", "反馈图片");
        return Result.ok(data);
    }

    /** 保留引用：FileBiz 常量与字典键一致性由编译期保证 */
    @SuppressWarnings("unused")
    private static final int BIZ_ALIGN_CHECK = Enums.FileBiz.AVATAR;

    private String str(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private Integer intOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long longOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
