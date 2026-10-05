package com.tonghang.campus.event.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tonghang.campus.account.api.UserFacade;
import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.notify.NotifySender;
import com.tonghang.campus.common.util.PageUtil;
import com.tonghang.campus.content.support.SensitiveWordChecker;
import com.tonghang.campus.event.dto.ActivityItem;
import com.tonghang.campus.event.entity.Activity;
import com.tonghang.campus.event.entity.ActivityEnroll;
import com.tonghang.campus.event.mapper.ActivityEnrollMapper;
import com.tonghang.campus.event.mapper.ActivityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 活动服务（M5）。
 *
 * <p>来源：《13-API接口文档》§3.5：
 * <ul>
 *   <li>POST /activities 发布活动（仅认证用户）— FR-M5-01</li>
 *   <li>GET /activities?category=&amp;status= 活动列表 — FR-M5-02</li>
 *   <li>GET /activities/{id} 活动详情（时间/人数/地点/要求/规则）— FR-M5-02</li>
 *   <li>POST /activities/{id}/enroll 报名（重复 40006、截止 40007）— FR-M5-05</li>
 *   <li>PUT /enrolls/{id} 组织者审核 approve/reject — FR-M5-02</li>
 *   <li>GET /activities/my-enrolls 我参与的活动 — FR-M7-07</li>
 * </ul>
 *
 * <p>权限（《11》§3.1）：发布活动 / 审核报名 = {@code @RequireRole(VERIFIED)}；
 * 报名 = 登录即可（普通用户可报名，见《10》§3.1）。
 */
@Service
public class ActivityService {

    private final ActivityMapper activityMapper;
    private final ActivityEnrollMapper enrollMapper;
    private final UserFacade userFacade;
    private final NotifySender notifySender;
    private final SensitiveWordChecker sensitiveWordChecker;

    public ActivityService(ActivityMapper activityMapper,
                           ActivityEnrollMapper enrollMapper,
                           UserFacade userFacade,
                           NotifySender notifySender,
                           SensitiveWordChecker sensitiveWordChecker) {
        this.activityMapper = activityMapper;
        this.enrollMapper = enrollMapper;
        this.userFacade = userFacade;
        this.notifySender = notifySender;
        this.sensitiveWordChecker = sensitiveWordChecker;
    }

    /* ==================== 发布 / 详情 ==================== */

    /**
     * 发布活动（FR-M5-01 / POST /activities）。
     *
     * <p>字段取自《13》§4「◆ POST /activities」请求示例；
     * 状态机：机审通过且已到报名开始时间 → 1 报名中，否则 → 0 待审。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.param("请求体不能为空");
        }
        String title = str(body.get("title"));
        if (title == null || title.length() > 80) {
            throw BizException.param("活动标题需为 1~80 个字符");
        }
        Integer category = intOrNull(body.get("category"));
        if (category == null || category < Activity.CATEGORY_CAMPUS || category > Activity.CATEGORY_RESEARCH) {
            throw BizException.param("活动分类需为 1 校园活动 / 2 学术讲座 / 3 学术调研");
        }
        LocalDateTime startAt = dateOf(body.get("startAt"));
        LocalDateTime endAt = dateOf(body.get("endAt"));
        LocalDateTime enrollStart = dateOf(body.get("enrollStart"));
        LocalDateTime enrollDeadline = dateOf(body.get("enrollDeadline"));
        if (startAt == null) {
            throw BizException.param("请填写活动开始时间");
        }
        if (enrollStart != null && enrollDeadline != null && enrollStart.isAfter(enrollDeadline)) {
            throw BizException.param("报名开始时间不能晚于报名截止时间");
        }

        String scanText = title + " " + (body.get("content") == null ? "" : String.valueOf(body.get("content")));
        SensitiveWordChecker.Result scan = sensitiveWordChecker.scan(scanText);
        if (scan.isBlocked()) {
            throw new BizException(ErrorCode.SENSITIVE_WORD,
                    "内容含违规词「" + scan.getHitWord() + "」，请修改后重试");
        }

        Activity a = new Activity();
        a.setPublisherId(userId);
        a.setTitle(title);
        a.setCover(str(body.get("cover")));
        a.setContent(str(body.get("content")));
        a.setCategory(category);
        a.setOrganizer(str(body.get("organizer")));
        a.setContact(str(body.get("contact")));
        a.setPlace(str(body.get("place")));
        a.setCapacity(intOrNull(body.get("capacity")));
        a.setEnrolledCnt(0);
        a.setStartAt(startAt);
        a.setEndAt(endAt);
        a.setEnrollStart(enrollStart);
        a.setEnrollDeadline(enrollDeadline);
        a.setNeedReview(boolInt(body.get("needReview")));
        a.setRequirement(str(body.get("requirement")));
        a.setRule(str(body.get("rule")));
        // 机审命中复审级 → 0 待审；否则按报名窗口判定
        a.setStatus(scan.isNeedReview() ? Activity.STATUS_PENDING : initialStatus(enrollStart, enrollDeadline));
        activityMapper.insert(a);

        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("activityId", a.getId());
        data.put("status", a.getStatus());
        data.put("statusText", ActivityItem.statusText(a.getStatus()));
        return data;
    }

    /** 活动列表（FR-M5-02 / GET /activities?category=&amp;status=） */
    public PageResult<Map<String, Object>> list(Long viewerId, Integer category, Integer status,
                                                String kw, Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);

        LambdaQueryWrapper<Activity> w = new LambdaQueryWrapper<>();
        if (category != null) {
            w.eq(Activity::getCategory, category);
        }
        if (status != null) {
            w.eq(Activity::getStatus, status);
        } else {
            // 默认不展示已取消（《13》未定义默认口径，按业务惯例过滤）
            w.ne(Activity::getStatus, Activity.STATUS_CANCELED);
        }
        if (kw != null && !kw.trim().isEmpty()) {
            w.like(Activity::getTitle, kw.trim());
        }
        w.orderByDesc(Activity::getStartAt);

        Page<Activity> pageObj = new Page<>(p, s);
        activityMapper.selectPage(pageObj, w);

        Set<Long> enrolledIds = myEnrolledActivityIds(viewerId,
                collectIds(pageObj.getRecords()));
        List<Map<String, Object>> list = new ArrayList<>(pageObj.getRecords().size());
        for (Activity a : pageObj.getRecords()) {
            list.add(toItem(a, enrolledIds.contains(a.getId())).toMap());
        }
        return PageResult.of(list, pageObj.getTotal(), p, s);
    }

    /**
     * 活动详情（FR-M5-02 / GET /activities/{id}）。
     *
     * <p>详情补齐 content / requirement / rule / contact（联系方式仅组织者可见，
     * 见《10》FR-M5-02「联系信息仅报名者可见」）。
     */
    public Map<String, Object> detail(Long viewerId, Long id) {
        Activity a = activityMapper.selectById(id);
        if (a == null) {
            throw BizException.notFound("活动不存在或已下架");
        }
        boolean owner = viewerId != null && a.getPublisherId().equals(viewerId);
        ActivityEnroll myEnroll = viewerId == null ? null : myEnroll(viewerId, id);
        boolean enrolled = myEnroll != null
                && myEnroll.getStatus() != null
                && myEnroll.getStatus() != ActivityEnroll.STATUS_CANCELED;
        boolean contactVisible = owner || enrolled;

        Map<String, Object> m = toItem(a, enrolled).toMap();
        m.put("content", a.getContent());
        m.put("requirement", a.getRequirement());
        m.put("rule", a.getRule());
        m.put("contactVisible", contactVisible);
        m.put("contact", contactVisible ? a.getContact() : null);
        m.put("owner", owner);

        UserFacade.UserBrief u = userFacade.get(a.getPublisherId());
        Map<String, Object> publisher = new LinkedHashMap<>(4);
        publisher.put("id", a.getPublisherId());
        publisher.put("nickname", u == null ? "用户" + a.getPublisherId() : u.getNickname());
        publisher.put("avatar", u == null ? null : u.getAvatar());
        publisher.put("schoolTag", u == null ? null : u.getSchoolTag());
        m.put("publisher", publisher);

        // 我的报名状态（未报名为 null，游客同为 null）
        if (myEnroll != null) {
            Map<String, Object> mine = new LinkedHashMap<>(4);
            mine.put("enrollId", myEnroll.getId());
            mine.put("status", myEnroll.getStatus());
            mine.put("statusText", enrollStatusText(myEnroll.getStatus()));
            mine.put("applyMsg", myEnroll.getApplyMsg());
            m.put("myEnroll", mine);
        } else {
            m.put("myEnroll", null);
        }
        return m;
    }

    /* ==================== 报名 / 审核 ==================== */

    /**
     * 报名活动（FR-M5-05 / POST /activities/{id}/enroll）。
     *
     * <p>校验（《13》§3.5 括号说明）：
     * <ul>
     *   <li>重复报名 → 40006</li>
     *   <li>报名未开始 / 已截止 / 已满员 / 活动非报名中 → 40007</li>
     *   <li>活动需审核 → 报名记录 status=0 待审；否则直接 1 通过</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> enroll(Long userId, Long activityId, Map<String, Object> body) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null) {
            throw BizException.notFound("活动不存在或已下架");
        }
        if (!isEnrollable(a)) {
            throw BizException.conflict("当前不在报名时间内，无法报名");
        }
        if (a.getCapacity() != null && a.getCapacity() > 0
                && nvl(a.getEnrolledCnt()) >= a.getCapacity()) {
            throw BizException.conflict("报名人数已满");
        }

        ActivityEnroll exist = myEnroll(userId, activityId);
        if (exist != null && exist.getStatus() != null
                && exist.getStatus() != ActivityEnroll.STATUS_CANCELED
                && exist.getStatus() != ActivityEnroll.STATUS_REJECTED) {
            throw new BizException(ErrorCode.DUPLICATE, "你已报名该活动，请勿重复提交");
        }

        boolean needReview = a.getNeedReview() != null && a.getNeedReview() == 1;
        ActivityEnroll e;
        if (exist != null) {
            // 曾被拒 / 已取消 → 复用原记录重新提交（避免 uk(activity_id, user_id) 冲突）
            e = exist;
            e.setStatus(needReview ? ActivityEnroll.STATUS_PENDING : ActivityEnroll.STATUS_PASSED);
        } else {
            e = new ActivityEnroll();
            e.setActivityId(activityId);
            e.setUserId(userId);
            e.setStatus(needReview ? ActivityEnroll.STATUS_PENDING : ActivityEnroll.STATUS_PASSED);
        }
        if (body != null) {
            e.setName(str(body.get("name")));
            e.setPhone(str(body.get("phone")));
            e.setApplyMsg(str(body.get("applyMsg")));
        }
        if (e.getId() == null) {
            enrollMapper.insert(e);
        } else {
            enrollMapper.updateById(e);
        }

        if (!needReview) {
            countUp(activityId);
        }
        if (needReview) {
            notifySender.send(a.getPublisherId(), Enums.NotifyBiz.EVENT,
                    "新的活动报名待审核",
                    "「" + a.getTitle() + "」有 1 条新报名",
                    Enums.TargetType.ACTIVITY, activityId, "activity-enroll:" + e.getId());
        }

        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("enrollId", e.getId());
        data.put("status", e.getStatus());
        data.put("statusText", enrollStatusText(e.getStatus()));
        return data;
    }

    /** 组织者审核报名（FR-M5-02 / PUT /enrolls/{id}，action=approve|reject） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleEnroll(Long userId, Long enrollId, Map<String, Object> body) {
        ActivityEnroll e = enrollMapper.selectById(enrollId);
        if (e == null) {
            throw BizException.notFound("报名记录不存在");
        }
        Activity a = activityMapper.selectById(e.getActivityId());
        if (a == null) {
            throw BizException.notFound("活动不存在或已下架");
        }
        if (!a.getPublisherId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅活动组织者可审核报名");
        }
        if (e.getStatus() == null || e.getStatus() != ActivityEnroll.STATUS_PENDING) {
            throw BizException.conflict("该报名已处理，请勿重复操作");
        }

        String action = body == null ? null : str(body.get("action"));
        if (action == null) {
            throw BizException.param("请指定 action（approve / reject）");
        }
        switch (action) {
            case "approve": {
                if (a.getCapacity() != null && a.getCapacity() > 0
                        && nvl(a.getEnrolledCnt()) >= a.getCapacity()) {
                    throw BizException.conflict("活动人数已满，无法通过更多报名");
                }
                e.setStatus(ActivityEnroll.STATUS_PASSED);
                enrollMapper.updateById(e);
                countUp(a.getId());
                notifySender.send(e.getUserId(), Enums.NotifyBiz.EVENT,
                        "活动报名已通过", "你报名的「" + a.getTitle() + "」已通过审核",
                        Enums.TargetType.ACTIVITY, a.getId(), "activity-approve:" + e.getId());
                break;
            }
            case "reject": {
                e.setStatus(ActivityEnroll.STATUS_REJECTED);
                enrollMapper.updateById(e);
                notifySender.send(e.getUserId(), Enums.NotifyBiz.EVENT,
                        "活动报名未通过", "「" + a.getTitle() + "」暂未通过你的报名",
                        Enums.TargetType.ACTIVITY, a.getId(), "activity-reject:" + e.getId());
                break;
            }
            default:
                throw BizException.param("action 仅支持 approve / reject");
        }

        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("enrollId", e.getId());
        data.put("status", e.getStatus());
        data.put("statusText", enrollStatusText(e.getStatus()));
        return data;
    }

    /** 我参与的活动（FR-M7-07 / GET /activities/my-enrolls，view=ongoing|history） */
    public PageResult<Map<String, Object>> myEnrolls(Long userId, String view, Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);
        String v = view == null || view.trim().isEmpty() ? "history" : view.trim();

        // 先查我的报名记录（排除已取消）
        List<ActivityEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<ActivityEnroll>()
                .eq(ActivityEnroll::getUserId, userId)
                .ne(ActivityEnroll::getStatus, ActivityEnroll.STATUS_CANCELED)
                .orderByDesc(ActivityEnroll::getId));
        if (enrolls.isEmpty()) {
            return PageResult.empty(p, s);
        }
        Map<Long, ActivityEnroll> enrollByActivity = new LinkedHashMap<>(enrolls.size());
        for (ActivityEnroll e : enrolls) {
            enrollByActivity.put(e.getActivityId(), e);
        }
        List<Activity> activities = activityMapper.selectBatchIds(enrollByActivity.keySet());
        if (activities == null || activities.isEmpty()) {
            return PageResult.empty(p, s);
        }

        List<Map<String, Object>> all = new ArrayList<>(activities.size());
        LocalDateTime now = LocalDateTime.now();
        for (Activity a : activities) {
            ActivityEnroll e = enrollByActivity.get(a.getId());
            Map<String, Object> m = toItem(a, true).toMap();
            m.put("myEnrollStatus", e == null ? null : e.getStatus());
            m.put("myEnrollStatusText", e == null ? null : enrollStatusText(e.getStatus()));
            // ongoing：进行中或未来开始；history：已结束
            boolean ongoing = a.getEndAt() == null || !a.getEndAt().isBefore(now);
            m.put("ongoing", ongoing);
            all.add(m);
        }
        boolean wantOngoing = "ongoing".equals(v);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> m : all) {
            boolean ongoing = Boolean.TRUE.equals(m.get("ongoing"));
            if (ongoing == wantOngoing) {
                filtered.add(m);
            }
        }
        return paginate(filtered, p, s);
    }

    /* ==================== 内部工具 ==================== */

    /** 报名窗口 + 活动状态双判定（《10》FR-M5-05 截止拦截） */
    private boolean isEnrollable(Activity a) {
        LocalDateTime now = LocalDateTime.now();
        if (a.getStatus() == null
                || (a.getStatus() != Activity.STATUS_ENROLLING && a.getStatus() != Activity.STATUS_PENDING)) {
            return false;
        }
        if (a.getEnrollStart() != null && now.isBefore(a.getEnrollStart())) {
            return false;
        }
        if (a.getEnrollDeadline() != null && now.isAfter(a.getEnrollDeadline())) {
            return false;
        }
        return true;
    }

    /** 报名上限 +1（并发安全，交给 DB 自增） */
    private void countUp(Long activityId) {
        activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .setSql("enrolled_cnt = enrolled_cnt + 1"));
    }

    private ActivityEnroll myEnroll(Long userId, Long activityId) {
        if (userId == null) {
            return null;
        }
        List<ActivityEnroll> list = enrollMapper.selectList(new LambdaQueryWrapper<ActivityEnroll>()
                .eq(ActivityEnroll::getActivityId, activityId)
                .eq(ActivityEnroll::getUserId, userId)
                .orderByDesc(ActivityEnroll::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 我在给定活动集合中已报名的活动 id（列表页批量标注） */
    private Set<Long> myEnrolledActivityIds(Long userId, List<Long> activityIds) {
        if (userId == null || activityIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<ActivityEnroll> list = enrollMapper.selectList(new LambdaQueryWrapper<ActivityEnroll>()
                .eq(ActivityEnroll::getUserId, userId)
                .in(ActivityEnroll::getActivityId, activityIds)
                .ne(ActivityEnroll::getStatus, ActivityEnroll.STATUS_CANCELED));
        Set<Long> ids = new HashSet<>(list.size());
        for (ActivityEnroll e : list) {
            ids.add(e.getActivityId());
        }
        return ids;
    }

    private List<Long> collectIds(List<Activity> list) {
        List<Long> ids = new ArrayList<>(list.size());
        for (Activity a : list) {
            ids.add(a.getId());
        }
        return ids;
    }

    private ActivityItem toItem(Activity a, boolean enrolled) {
        ActivityItem i = new ActivityItem();
        i.setId(a.getId());
        i.setTitle(a.getTitle());
        i.setCover(a.getCover());
        i.setCategory(a.getCategory());
        i.setOrganizer(a.getOrganizer());
        i.setPlace(a.getPlace());
        i.setCapacity(a.getCapacity());
        i.setEnrolledCnt(nvl(a.getEnrolledCnt()));
        i.setStartAt(a.getStartAt());
        i.setEndAt(a.getEndAt());
        i.setEnrollStart(a.getEnrollStart());
        i.setEnrollDeadline(a.getEnrollDeadline());
        i.setNeedReview(nvl(a.getNeedReview()));
        i.setStatus(a.getStatus());
        i.setCreatedAt(a.getCreatedAt());
        i.setEnrolled(enrolled);
        return i;
    }

    /** 报名状态文案（《12》§2.5 activity_enroll.status） */
    public static String enrollStatusText(Integer status) {
        if (status == null) {
            return "待审核";
        }
        switch (status) {
            case ActivityEnroll.STATUS_PENDING:
                return "待审核";
            case ActivityEnroll.STATUS_PASSED:
                return "已通过";
            case ActivityEnroll.STATUS_REJECTED:
                return "未通过";
            case ActivityEnroll.STATUS_SIGNED:
                return "已签到";
            case ActivityEnroll.STATUS_CANCELED:
                return "已取消";
            default:
                return "待审核";
        }
    }

    /** 首次发布时的初始状态：报名窗口已开 → 报名中，否则待审 */
    private int initialStatus(LocalDateTime enrollStart, LocalDateTime enrollDeadline) {
        LocalDateTime now = LocalDateTime.now();
        if (enrollStart != null && now.isBefore(enrollStart)) {
            return Activity.STATUS_PENDING;
        }
        if (enrollDeadline != null && now.isAfter(enrollDeadline)) {
            return Activity.STATUS_ENDED;
        }
        return Activity.STATUS_ENROLLING;
    }

    private static <T> PageResult<T> paginate(List<T> all, int p, int s) {
        int from = (p - 1) * s;
        if (from >= all.size()) {
            return PageResult.of(Collections.emptyList(), all.size(), p, s);
        }
        int to = Math.min(from + s, all.size());
        return PageResult.of(new ArrayList<>(all.subList(from, to)), all.size(), p, s);
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private Integer boolInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? 1 : 0;
        }
        if ("true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v))) {
            return 1;
        }
        return 0;
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

    /** ISO-8601 字符串（含 +08:00 时区）→ LocalDateTime */
    private LocalDateTime dateOf(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(s);
            return odt.toLocalDateTime();
        } catch (Exception ignore) {
            // 继续尝试其它格式
        }
        try {
            return LocalDateTime.parse(s);
        } catch (Exception ignore) {
            // 继续尝试紧凑格式
        }
        try {
            return LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignore) {
            return null;
        }
    }
}
