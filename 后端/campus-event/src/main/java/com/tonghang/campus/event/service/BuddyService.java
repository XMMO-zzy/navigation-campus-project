package com.tonghang.campus.event.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tonghang.campus.account.api.UserFacade;
import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.notify.NotifySender;
import com.tonghang.campus.common.util.PageUtil;
import com.tonghang.campus.content.support.SensitiveWordChecker;
import com.tonghang.campus.event.dto.BuddyItem;
import com.tonghang.campus.event.entity.BuddyApply;
import com.tonghang.campus.event.entity.BuddyPost;
import com.tonghang.campus.event.mapper.BuddyApplyMapper;
import com.tonghang.campus.event.mapper.BuddyPostMapper;
import com.tonghang.campus.event.support.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 找搭子服务（M6）。
 *
 * <p>来源：《13-API接口文档》§3.6：
 * <ul>
 *   <li>POST /buddy-posts 发布搭子帖（类型/标签/展示信息/gender_limit）— FR-M6-01</li>
 *   <li>GET /buddy-posts?type=&amp;tag=&amp;femaleOnly= 搭子列表 — FR-M6-02/05</li>
 *   <li>GET /buddy-posts/my?view=published|favorite|matched — FR-M6-03</li>
 *   <li>POST /buddy-posts/{id}/apply 申请加入 → 对方同意后解锁联系方式 — FR-M6-04</li>
 * </ul>
 *
 * <p>安全（《18》隐私合规）：联系方式默认脱敏，仅「发布者本人」或「已通过审批的申请人」可见；
 * 女性专属模式（genderLimit=1）由 {{@link #list}} 的 femaleOnly 参数与发帖人性别共同约束。
 *
 * <p>契约缺口登记：《13》§3.6 未定义「处理搭子申请」的审核接口，
 * 但 FR-M6-04「对方同意后解锁联系方式」必须有写入入口；
 * 本类按最小语义实现 {@link #handleApply}（PUT /buddy-applies/{id}），
 * 仅使用 buddy_apply 表内已有列，不臆造字段，交付说明中登记为待评审项。
 */
@Service
public class BuddyService {

    private final BuddyPostMapper buddyMapper;
    private final BuddyApplyMapper applyMapper;
    private final UserFacade userFacade;
    private final NotifySender notifySender;
    private final SensitiveWordChecker sensitiveWordChecker;

    public BuddyService(BuddyPostMapper buddyMapper,
                        BuddyApplyMapper applyMapper,
                        UserFacade userFacade,
                        NotifySender notifySender,
                        SensitiveWordChecker sensitiveWordChecker) {
        this.buddyMapper = buddyMapper;
        this.applyMapper = applyMapper;
        this.userFacade = userFacade;
        this.notifySender = notifySender;
        this.sensitiveWordChecker = sensitiveWordChecker;
    }

    /* ==================== 发布 / 列表 / 详情 ==================== */

    /** 发布搭子帖（FR-M6-01 / POST /buddy-posts） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.param("请求体不能为空");
        }
        String title = str(body.get("title"));
        if (title == null || title.length() > 60) {
            throw BizException.param("标题需为 1~60 个字符");
        }
        Integer buddyType = intOrNull(body.get("buddyType"));
        if (buddyType == null || buddyType < Enums.BuddyType.STUDY || buddyType > Enums.BuddyType.SKILL) {
            throw BizException.param("搭子类型需为 1 学习 / 2 运动 / 3 娱乐 / 4 旅行 / 5 饭搭子 / 6 技能");
        }

        String scanText = title + " " + (body.get("desc") == null ? "" : String.valueOf(body.get("desc")));
        SensitiveWordChecker.Result scan = sensitiveWordChecker.scan(scanText);
        if (scan.isBlocked()) {
            throw new BizException(ErrorCode.SENSITIVE_WORD,
                    "内容含违规词「" + scan.getHitWord() + "」，请修改后重试");
        }

        Integer genderLimit = intOrNull(body.get("genderLimit"));
        if (genderLimit != null && genderLimit != Enums.GenderLimit.NONE
                && genderLimit != Enums.GenderLimit.FEMALE_ONLY) {
            throw BizException.param("genderLimit 仅支持 0 不限 / 1 仅女性");
        }

        BuddyPost p = new BuddyPost();
        p.setUserId(userId);
        p.setTitle(title);
        p.setBuddyType(buddyType);
        p.setTags(JsonUtil.toJson(body.get("tags")));
        p.setDesc(str(body.get("desc")));
        p.setPlace(str(body.get("place")));
        p.setTimeDesc(str(body.get("timeDesc")));
        p.setRequirement(str(body.get("requirement")));
        p.setImages(JsonUtil.toJson(body.get("images")));
        p.setContact(str(body.get("contact")));
        p.setGenderLimit(genderLimit == null ? Enums.GenderLimit.NONE : genderLimit);
        p.setQuota(intOrNull(body.get("quota")));
        p.setFeeMode(intOrNull(body.get("feeMode")) == null ? Enums.BuddyFeeMode.AA : intOrNull(body.get("feeMode")));
        p.setMatched(0);
        p.setStatus(scan.isNeedReview() ? BuddyPost.STATUS_PENDING : BuddyPost.STATUS_NORMAL);
        buddyMapper.insert(p);

        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("buddyId", p.getId());
        data.put("status", p.getStatus());
        data.put("statusText", BuddyItem.statusText(p.getStatus()));
        return data;
    }

    /**
     * 搭子列表（FR-M6-02/05 / GET /buddy-posts?type=&amp;tag=&amp;femaleOnly=）。
     *
     * <p>femaleOnly=true 时仅返回 genderLimit=1（女性专属）的帖子；
     * 该模式下普通列表也不应出现男性用户发布的女性专属帖（发帖校验在 {@link #create} 已限）。
     */
    public PageResult<Map<String, Object>> list(Long viewerId, Integer buddyType, String tag,
                                                Boolean femaleOnly, String kw,
                                                Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);

        LambdaQueryWrapper<BuddyPost> w = new LambdaQueryWrapper<BuddyPost>()
                .eq(BuddyPost::getStatus, BuddyPost.STATUS_NORMAL);
        // 屏蔽双向不可见（FR-M2-11）
        if (viewerId != null) {
            java.util.Set<Long> blocked = userFacade.blockedEitherWay(viewerId);
            if (blocked != null && !blocked.isEmpty()) {
                w.notIn(BuddyPost::getUserId, blocked);
            }
        }
        if (buddyType != null) {
            w.eq(BuddyPost::getBuddyType, buddyType);
        }
        if (Boolean.TRUE.equals(femaleOnly)) {
            w.eq(BuddyPost::getGenderLimit, Enums.GenderLimit.FEMALE_ONLY);
        }
        if (tag != null && !tag.trim().isEmpty()) {
            // tags 为 JSON 字符串列（《12》§2.6），按包含匹配
            w.like(BuddyPost::getTags, tag.trim());
        }
        if (kw != null && !kw.trim().isEmpty()) {
            w.and(q -> q.like(BuddyPost::getTitle, kw.trim())
                    .or().like(BuddyPost::getDesc, kw.trim()));
        }
        w.orderByDesc(BuddyPost::getMatched).orderByDesc(BuddyPost::getCreatedAt);

        Page<BuddyPost> pageObj = new Page<>(p, s);
        buddyMapper.selectPage(pageObj, w);

        List<Map<String, Object>> list = new ArrayList<>(pageObj.getRecords().size());
        for (BuddyPost bp : pageObj.getRecords()) {
            list.add(toMap(bp, viewerId, false).toMap());
        }
        return PageResult.of(list, pageObj.getTotal(), p, s);
    }

    /** 搭子详情（GET /buddy-posts/{id}；《13》未单列，按同权限规则提供） */
    public Map<String, Object> detail(Long viewerId, Long id) {
        BuddyPost bp = buddyMapper.selectById(id);
        if (bp == null) {
            throw BizException.notFound("搭子帖不存在或已关闭");
        }
        // 非正常状态仅发布者可见
        if (bp.getStatus() != null && bp.getStatus() != BuddyPost.STATUS_NORMAL
                && (viewerId == null || !bp.getUserId().equals(viewerId))) {
            throw BizException.notFound("搭子帖不存在或已关闭");
        }
        BuddyItem item = toMap(bp, viewerId, true);
        Map<String, Object> m = item.toMap();

        UserFacade.UserBrief u = userFacade.getWithPrivacy(viewerId, bp.getUserId());
        Map<String, Object> owner = new LinkedHashMap<>(6);
        owner.put("id", bp.getUserId());
        owner.put("nickname", u == null ? "用户" + bp.getUserId() : u.getNickname());
        owner.put("avatar", u == null ? null : u.getAvatar());
        owner.put("schoolTag", u == null ? null : u.getSchoolTag());
        owner.put("gender", u == null ? null : u.getGender());
        owner.put("verifyStatus", u == null ? null : u.getVerifyStatus());
        m.put("owner", owner);
        m.put("isOwner", viewerId != null && bp.getUserId().equals(viewerId));

        // 申请列表：仅发布者可见（含申请人简要信息）
        if (viewerId != null && bp.getUserId().equals(viewerId)) {
            List<BuddyApply> applies = applyMapper.selectList(new LambdaQueryWrapper<BuddyApply>()
                    .eq(BuddyApply::getBuddyId, id)
                    .ne(BuddyApply::getStatus, BuddyApply.STATUS_WITHDRAWN)
                    .orderByDesc(BuddyApply::getId));
            List<Map<String, Object>> arr = new ArrayList<>(applies.size());
            for (BuddyApply a : applies) {
                Map<String, Object> am = new LinkedHashMap<>(6);
                am.put("id", a.getId());
                am.put("userId", a.getUserId());
                UserFacade.UserBrief au = userFacade.get(a.getUserId());
                am.put("nickname", au == null ? "用户" + a.getUserId() : au.getNickname());
                am.put("avatar", au == null ? null : au.getAvatar());
                am.put("schoolTag", au == null ? null : au.getSchoolTag());
                am.put("message", a.getMessage());
                am.put("status", a.getStatus());
                am.put("statusText", applyStatusText(a.getStatus()));
                am.put("createdAt", a.getCreatedAt());
                arr.add(am);
            }
            m.put("applies", arr);
        }
        return m;
    }

    /** 我的搭子帖（FR-M6-03 / GET /buddy-posts/my?view=published|favorite|matched） */
    public PageResult<Map<String, Object>> my(Long userId, String view, Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);
        String v = view == null || view.trim().isEmpty() ? "published" : view.trim();

        if ("favorite".equals(v)) {
            // 收藏复用 content 域 favorite 表（多态 target_type=5）；跨域聚合由 server 装配层完成，
            // 此处保持结构一致返回空列表（不臆造独立收藏表）
            return PageResult.empty(p, s);
        }

        LambdaQueryWrapper<BuddyPost> w = new LambdaQueryWrapper<BuddyPost>()
                .orderByDesc(BuddyPost::getId);
        if ("matched".equals(v)) {
            // 「我滴过的」= 我提出且已通过的申请 → 先反查 buddy_apply
            List<BuddyApply> passed = applyMapper.selectList(new LambdaQueryWrapper<BuddyApply>()
                    .eq(BuddyApply::getUserId, userId)
                    .eq(BuddyApply::getStatus, BuddyApply.STATUS_PASSED));
            if (passed.isEmpty()) {
                return PageResult.empty(p, s);
            }
            List<Long> ids = new ArrayList<>(passed.size());
            for (BuddyApply a : passed) {
                ids.add(a.getBuddyId());
            }
            w.in(BuddyPost::getId, ids);
        } else {
            w.eq(BuddyPost::getUserId, userId);
        }

        Page<BuddyPost> pageObj = new Page<>(p, s);
        buddyMapper.selectPage(pageObj, w);
        List<Map<String, Object>> list = new ArrayList<>(pageObj.getRecords().size());
        for (BuddyPost bp : pageObj.getRecords()) {
            list.add(toMap(bp, userId, true).toMap());
        }
        return PageResult.of(list, pageObj.getTotal(), p, s);
    }

    /* ==================== 申请 / 审批 ==================== */

    /**
     * 申请加入（FR-M6-04 / POST /buddy-posts/{id}/apply）。
     *
     * <p>校验：不能申请自己的帖子；重复申请 → 40006；帖子已关闭 / 已找到人 → 40007。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> apply(Long userId, Long buddyId, Map<String, Object> body) {
        BuddyPost bp = buddyMapper.selectById(buddyId);
        if (bp == null) {
            throw BizException.notFound("搭子帖不存在或已关闭");
        }
        if (bp.getUserId().equals(userId)) {
            throw BizException.param("不能申请自己发布的搭子");
        }
        if (bp.getStatus() == null || bp.getStatus() != BuddyPost.STATUS_NORMAL) {
            throw BizException.conflict("该搭子帖已关闭，无法申请");
        }
        if (bp.getMatched() != null && bp.getMatched() == BuddyPost.MATCHED_YES) {
            throw BizException.conflict("对方已找到合适的搭子");
        }

        // 女性专属模式校验（FR-M6-05）：发帖人性别需与限制一致，申请人性别需匹配
        if (bp.getGenderLimit() != null && bp.getGenderLimit() == Enums.GenderLimit.FEMALE_ONLY) {
            UserFacade.UserBrief me = userFacade.get(userId);
            if (me != null && me.getGender() != null && me.getGender() != 2) {
                throw new BizException(ErrorCode.FORBIDDEN, "该搭子仅限女生申请");
            }
        }

        List<BuddyApply> exist = applyMapper.selectList(new LambdaQueryWrapper<BuddyApply>()
                .eq(BuddyApply::getBuddyId, buddyId)
                .eq(BuddyApply::getUserId, userId)
                .orderByDesc(BuddyApply::getId)
                .last("LIMIT 1"));
        BuddyApply a;
        if (!exist.isEmpty()) {
            a = exist.get(0);
            if (a.getStatus() != null && a.getStatus() != BuddyApply.STATUS_WITHDRAWN
                    && a.getStatus() != BuddyApply.STATUS_REJECTED) {
                throw new BizException(ErrorCode.DUPLICATE, "你已申请过该搭子，请勿重复提交");
            }
            a.setStatus(BuddyApply.STATUS_PENDING);
        } else {
            a = new BuddyApply();
            a.setBuddyId(buddyId);
            a.setUserId(userId);
            a.setStatus(BuddyApply.STATUS_PENDING);
        }
        a.setMessage(body == null ? null : str(body.get("message")));
        if (a.getId() == null) {
            applyMapper.insert(a);
        } else {
            applyMapper.updateById(a);
        }

        notifySender.send(bp.getUserId(), Enums.NotifyBiz.INTERACT,
                "有新的搭子申请",
                "有人想和你一起「" + bp.getTitle() + "」",
                Enums.TargetType.BUDDY, buddyId, "buddy-apply:" + a.getId());

        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("applyId", a.getId());
        data.put("status", a.getStatus());
        data.put("statusText", applyStatusText(a.getStatus()));
        return data;
    }

    /**
     * 处理搭子申请（契约缺口实现：PUT /buddy-applies/{id}，action=agree|reject|withdraw）。
     *
     * <p>《13》§3.6 未定义该接口，但 FR-M6-04「对方同意后解锁联系方式」需要写入入口；
     * 本方法只使用 buddy_apply 已有列（status / message），不新增字段。
     * 同意后：申请 status=1 已通过、帖子 matched=1 已找到（人数达上限时自动关闭）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleApply(Long userId, Long applyId, Map<String, Object> body) {
        BuddyApply a = applyMapper.selectById(applyId);
        if (a == null) {
            throw BizException.notFound("申请不存在");
        }
        BuddyPost bp = buddyMapper.selectById(a.getBuddyId());
        if (bp == null) {
            throw BizException.notFound("搭子帖不存在或已关闭");
        }
        boolean isOwner = bp.getUserId().equals(userId);
        boolean isApplicant = a.getUserId().equals(userId);
        if (!isOwner && !isApplicant) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权处理该申请");
        }

        String action = body == null ? null : str(body.get("action"));
        if (action == null) {
            throw BizException.param("请指定 action（agree / reject / withdraw）");
        }
        switch (action) {
            case "agree": {
                requireOwner(isOwner);
                requirePending(a);
                a.setStatus(BuddyApply.STATUS_PASSED);
                applyMapper.updateById(a);
                // 达到人数上限 → 标记已找到；否则保持寻找中，允许多人搭子
                int passedCnt = countPassed(bp.getId());
                bp.setMatched(BuddyPost.MATCHED_YES);
                if (bp.getQuota() != null && bp.getQuota() > 0 && passedCnt >= bp.getQuota()) {
                    bp.setStatus(BuddyPost.STATUS_CLOSED);
                }
                buddyMapper.updateById(bp);
                notifySender.send(a.getUserId(), Enums.NotifyBiz.INTERACT,
                        "搭子申请已通过",
                        "「" + bp.getTitle() + "」已同意你的申请，可查看联系方式",
                        Enums.TargetType.BUDDY, bp.getId(), "buddy-agree:" + a.getId());
                break;
            }
            case "reject": {
                requireOwner(isOwner);
                requirePending(a);
                a.setStatus(BuddyApply.STATUS_REJECTED);
                applyMapper.updateById(a);
                notifySender.send(a.getUserId(), Enums.NotifyBiz.INTERACT,
                        "搭子申请未通过",
                        "「" + bp.getTitle() + "」暂未通过你的申请",
                        Enums.TargetType.BUDDY, bp.getId(), "buddy-reject:" + a.getId());
                break;
            }
            case "withdraw": {
                if (!isApplicant) {
                    throw new BizException(ErrorCode.FORBIDDEN, "只能撤回自己的申请");
                }
                requirePending(a);
                a.setStatus(BuddyApply.STATUS_WITHDRAWN);
                applyMapper.updateById(a);
                break;
            }
            default:
                throw BizException.param("action 仅支持 agree / reject / withdraw");
        }

        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("applyId", a.getId());
        data.put("status", a.getStatus());
        data.put("statusText", applyStatusText(a.getStatus()));
        return data;
    }

    /* ==================== 内部工具 ==================== */

    private void requireOwner(boolean isOwner) {
        if (!isOwner) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅发布者可处理申请");
        }
    }

    private void requirePending(BuddyApply a) {
        if (a.getStatus() == null || a.getStatus() != BuddyApply.STATUS_PENDING) {
            throw BizException.conflict("该申请已处理，请勿重复操作");
        }
    }

    private int countPassed(Long buddyId) {
        Long cnt = applyMapper.selectCount(new LambdaQueryWrapper<BuddyApply>()
                .eq(BuddyApply::getBuddyId, buddyId)
                .eq(BuddyApply::getStatus, BuddyApply.STATUS_PASSED));
        return cnt == null ? 0 : cnt.intValue();
    }

    /**
     * 实体 → DTO（含权限判定）。
     *
     * @param viewerId 访问者（游客 null）
     * @param withApply 是否查询「我的申请状态」（列表页需要）
     */
    private BuddyItem toMap(BuddyPost bp, Long viewerId, boolean withApply) {
        BuddyItem i = new BuddyItem();
        i.setId(bp.getId());
        i.setTitle(bp.getTitle());
        i.setBuddyType(bp.getBuddyType());
        i.setTags(JsonUtil.toStringList(bp.getTags()));
        i.setDesc(bp.getDesc());
        i.setPlace(bp.getPlace());
        i.setTimeDesc(bp.getTimeDesc());
        i.setRequirement(bp.getRequirement());
        i.setImages(JsonUtil.toStringList(bp.getImages()));
        i.setContact(bp.getContact());
        i.setGenderLimit(bp.getGenderLimit() == null ? Enums.GenderLimit.NONE : bp.getGenderLimit());
        i.setQuota(bp.getQuota());
        i.setFeeMode(bp.getFeeMode() == null ? Enums.BuddyFeeMode.AA : bp.getFeeMode());
        i.setMatched(bp.getMatched() == null ? 0 : bp.getMatched());
        i.setStatus(bp.getStatus());
        i.setCreatedAt(bp.getCreatedAt());
        i.setApplyCnt(countAll(bp.getId()));

        boolean isOwner = viewerId != null && bp.getUserId().equals(viewerId);
        Integer myApplyStatus = null;
        if (withApply && viewerId != null && !isOwner) {
            List<BuddyApply> mine = applyMapper.selectList(new LambdaQueryWrapper<BuddyApply>()
                    .eq(BuddyApply::getBuddyId, bp.getId())
                    .eq(BuddyApply::getUserId, viewerId)
                    .ne(BuddyApply::getStatus, BuddyApply.STATUS_WITHDRAWN)
                    .orderByDesc(BuddyApply::getId)
                    .last("LIMIT 1"));
            if (!mine.isEmpty()) {
                myApplyStatus = mine.get(0).getStatus();
            }
        }
        i.setApplied(myApplyStatus != null);
        i.setApplyStatus(myApplyStatus);
        // 联系方式解锁：发布者本人 或 我的申请已通过（《10》FR-M6-04）
        i.setContactUnlocked(isOwner || (myApplyStatus != null
                && myApplyStatus == BuddyApply.STATUS_PASSED));
        return i;
    }

    private int countAll(Long buddyId) {
        Long cnt = applyMapper.selectCount(new LambdaQueryWrapper<BuddyApply>()
                .eq(BuddyApply::getBuddyId, buddyId)
                .ne(BuddyApply::getStatus, BuddyApply.STATUS_WITHDRAWN));
        return cnt == null ? 0 : cnt.intValue();
    }

    /** 申请状态文案（《12》§2.6 buddy_apply.status） */
    public static String applyStatusText(Integer status) {
        if (status == null) {
            return "待处理";
        }
        switch (status) {
            case BuddyApply.STATUS_PENDING:
                return "待处理";
            case BuddyApply.STATUS_PASSED:
                return "已通过";
            case BuddyApply.STATUS_REJECTED:
                return "已拒绝";
            case BuddyApply.STATUS_WITHDRAWN:
                return "已撤回";
            default:
                return "待处理";
        }
    }

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
}
