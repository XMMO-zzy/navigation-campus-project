package com.tonghang.campus.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tonghang.campus.account.entity.User;
import com.tonghang.campus.account.entity.UserVerifyApply;
import com.tonghang.campus.account.mapper.UserMapper;
import com.tonghang.campus.account.mapper.UserVerifyApplyMapper;
import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学号认证服务（M1 / FR-M1-05、FR-M1-06）。
 *
 * <p>认证四态（阶段二决策之①，以《10》FR-M1-06 为准）：
 * <pre>
 *   0 未认证  → 未提交 / 可提交
 *   1 认证中  → 已提交，等待审核
 *   2 已认证  → 审核通过，user_type 同步置为 2（《11》§3.1 VERIFIED 口径）
 *   3 未通过  → 审核驳回，附 reject_reason
 * </pre>
 */
@Service
public class VerifyService {

    private static final Logger log = LoggerFactory.getLogger(VerifyService.class);

    private final UserMapper userMapper;
    private final UserVerifyApplyMapper applyMapper;

    public VerifyService(UserMapper userMapper, UserVerifyApplyMapper applyMapper) {
        this.userMapper = userMapper;
        this.applyMapper = applyMapper;
    }

    /**
     * 提交学号认证（FR-M1-05 / POST /verify/apply）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> apply(Long userId, Map<String, Object> body) {
        User user = requireUser(userId);

        if (user.getUserType() != null && user.getUserType() == Enums.UserType.VERIFIED) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "您已完成学号认证");
        }
        if (user.getVerifyStatus() != null && user.getVerifyStatus() == Enums.VerifyStatus.PENDING) {
            throw new BizException(ErrorCode.DUPLICATE, "认证申请审核中，请耐心等待");
        }

        String studentNo = str(body.get("studentNo"));
        String realName = str(body.get("realName"));
        Object cardFile = body.get("campusCardFileId");

        if (studentNo == null || !studentNo.matches("^\\d{6,20}$")) {
            throw BizException.param("学号格式不正确（6~20 位数字）");
        }
        if (realName == null || realName.length() < 2 || realName.length() > 32) {
            throw BizException.param("请输入真实姓名（2~32 个字符）");
        }
        if (cardFile == null) {
            throw BizException.param("请上传校园卡照片");
        }

        UserVerifyApply apply = new UserVerifyApply();
        apply.setUserId(userId);
        apply.setStudentNo(studentNo);
        apply.setRealName(realName);
        apply.setCampusCardFileId(Long.parseLong(String.valueOf(cardFile)));
        apply.setStatus(UserVerifyApply.STATUS_PENDING);
        applyMapper.insert(apply);

        user.setVerifyStatus(Enums.VerifyStatus.PENDING);
        userMapper.updateById(user);

        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("applyId", apply.getId());
        data.put("verifyStatus", Enums.VerifyStatus.PENDING);
        log.info("[M1] 学号认证提交 userId={} applyId={}", userId, apply.getId());
        return data;
    }

    /**
     * 认证四态 + 未通过原因（FR-M1-06 / GET /verify/status）。
     */
    public Map<String, Object> status(Long userId) {
        User user = requireUser(userId);
        int verifyStatus = user.getVerifyStatus() == null
                ? Enums.VerifyStatus.NONE : user.getVerifyStatus();

        Map<String, Object> data = new LinkedHashMap<>(8);
        data.put("verifyStatus", verifyStatus);
        data.put("statusText", verifyText(verifyStatus));
        data.put("userType", user.getUserType());
        data.put("verified", verifyStatus == Enums.VerifyStatus.PASSED);

        // 最新一条申请（用于回显学号/驳回原因）
        List<UserVerifyApply> applies = applyMapper.selectList(new LambdaQueryWrapper<UserVerifyApply>()
                .eq(UserVerifyApply::getUserId, userId)
                .orderByDesc(UserVerifyApply::getId)
                .last("LIMIT 1"));
        if (!applies.isEmpty()) {
            UserVerifyApply last = applies.get(0);
            data.put("studentNo", last.getStudentNo());
            data.put("realName", last.getRealName());
            data.put("appliedAt", last.getCreatedAt());
            data.put("reviewedAt", last.getReviewedAt());
            if (verifyStatus == Enums.VerifyStatus.REJECTED) {
                data.put("rejectReason", last.getRejectReason());
            }
        }
        return data;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private String verifyText(int status) {
        switch (status) {
            case Enums.VerifyStatus.PENDING:
                return "认证中";
            case Enums.VerifyStatus.PASSED:
                return "已认证";
            case Enums.VerifyStatus.REJECTED:
                return "未通过";
            default:
                return "未认证";
        }
    }

    private String str(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
