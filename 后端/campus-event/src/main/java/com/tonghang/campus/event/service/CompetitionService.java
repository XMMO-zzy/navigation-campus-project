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
import com.tonghang.campus.common.team.TeamQueryFacade;
import com.tonghang.campus.common.util.PageUtil;
import com.tonghang.campus.content.support.SensitiveWordChecker;
import com.tonghang.campus.event.dto.CalendarItem;
import com.tonghang.campus.event.dto.CompetitionItem;
import com.tonghang.campus.event.entity.Competition;
import com.tonghang.campus.event.entity.CompetitionEnroll;
import com.tonghang.campus.event.mapper.CompetitionEnrollMapper;
import com.tonghang.campus.event.mapper.CompetitionMapper;
import com.tonghang.campus.event.support.JsonUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 竞赛服务（M5）。
 *
 * <p>来源：《13-API接口文档》§3.5：
 * <ul>
 *   <li>POST /competitions 发布竞赛（仅认证用户）— FR-M5-03</li>
 *   <li>GET /competitions?level=&amp;status= 竞赛列表 + 多维筛选 — FR-M5-04</li>
 *   <li>GET /competitions/calendar?month= 日程日历 — FR-M5-04</li>
 *   <li>POST /competitions/{id}/enroll 报名（可关联 team_id）— FR-M5-05</li>
 *   <li>PUT /competition-enrolls/{id}/result 提交成果（P2）— FR-M5-07</li>
 *   <li>GET /competitions/my-enrolls 我的参赛记录 + 成果 — FR-M7-07</li>
 * </ul>
 *
 * <p>团队报名（{@code teamId}）：队伍归属 campus-message 的 team 表（《11》§2.1 禁止跨域 Mapper），
 * 故通过 {@link TeamQueryFacade} 校验；未装配时（P0 单模块启动）跳过队伍校验并记录契约缺口。
 */
@Service
public class CompetitionService {

    private final CompetitionMapper competitionMapper;
    private final CompetitionEnrollMapper enrollMapper;
    private final UserFacade userFacade;
    private final NotifySender notifySender;
    private final SensitiveWordChecker sensitiveWordChecker;
    private final TeamQueryFacade teamQueryFacade;

    public CompetitionService(CompetitionMapper competitionMapper,
                              CompetitionEnrollMapper enrollMapper,
                              UserFacade userFacade,
                              NotifySender notifySender,
                              SensitiveWordChecker sensitiveWordChecker,
                              ObjectProvider<TeamQueryFacade> teamQueryFacadeProvider) {
        this.competitionMapper = competitionMapper;
        this.enrollMapper = enrollMapper;
        this.userFacade = userFacade;
        this.notifySender = notifySender;
        this.sensitiveWordChecker = sensitiveWordChecker;
        this.teamQueryFacade = teamQueryFacadeProvider.getIfAvailable();
    }

    /* ==================== 发布 / 列表 / 详情 ==================== */

    /** 发布竞赛（FR-M5-03 / POST /competitions，仅认证用户） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.param("请求体不能为空");
        }
        String title = str(body.get("title"));
        if (title == null || title.length() > 80) {
            throw BizException.param("竞赛名称需为 1~80 个字符");
        }
        Integer level = intOrNull(body.get("level"));
        if (level == null || level < Competition.LEVEL_SCHOOL || level > Competition.LEVEL_NATION) {
            throw BizException.param("竞赛级别需为 1 校级 / 2 省级 / 3 国家级");
        }
        LocalDateTime enrollStart = dateOf(body.get("enrollStart"));
        LocalDateTime enrollDeadline = dateOf(body.get("enrollDeadline"));
        if (enrollStart != null && enrollDeadline != null && enrollStart.isAfter(enrollDeadline)) {
            throw BizException.param("报名开始时间不能晚于报名截止时间");
        }

        String scanText = title + " " + (body.get("content") == null ? "" : String.valueOf(body.get("content")));
        SensitiveWordChecker.Result scan = sensitiveWordChecker.scan(scanText);
        if (scan.isBlocked()) {
            throw new BizException(ErrorCode.SENSITIVE_WORD,
                    "内容含违规词「" + scan.getHitWord() + "」，请修改后重试");
        }

        Competition c = new Competition();
        c.setPublisherId(userId);
        c.setTitle(title);
        c.setCover(str(body.get("cover")));
        c.setContent(str(body.get("content")));
        c.setLevel(level);
        c.setOrganizer(str(body.get("organizer")));
        c.setContact(str(body.get("contact")));
        c.setPlace(str(body.get("place")));
        c.setCapacity(intOrNull(body.get("capacity")));
        c.setEnrolledCnt(0);
        c.setRequirement(str(body.get("requirement")));
        c.setAwards(str(body.get("awards")));
        c.setRule(str(body.get("rule")));
        c.setEnrollStart(enrollStart);
        c.setEnrollDeadline(enrollDeadline);
        c.setPreliminaryAt(dateOf(body.get("preliminaryAt")));
        c.setFinalAt(dateOf(body.get("finalAt")));
        c.setStartAt(dateOf(body.get("startAt")));
        c.setEndAt(dateOf(body.get("endAt")));
        c.setStatus(initialStatus(enrollStart, enrollDeadline));
        competitionMapper.insert(c);

        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("competitionId", c.getId());
        data.put("status", c.getStatus());
        data.put("statusText", CompetitionItem.statusText(c.getStatus()));
        return data;
    }

    /** 竞赛列表（FR-M5-04 / GET /competitions?level=&amp;status=） */
    public PageResult<Map<String, Object>> list(Long viewerId, Integer level, Integer status,
                                                String kw, Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);

        LambdaQueryWrapper<Competition> w = new LambdaQueryWrapper<>();
        if (level != null) {
            w.eq(Competition::getLevel, level);
        }
        if (status != null) {
            w.eq(Competition::getStatus, status);
        } else {
            w.ne(Competition::getStatus, Competition.STATUS_CANCELED);
        }
        if (kw != null && !kw.trim().isEmpty()) {
            w.like(Competition::getTitle, kw.trim());
        }
        w.orderByDesc(Competition::getEnrollDeadline);

        Page<Competition> pageObj = new Page<>(p, s);
        competitionMapper.selectPage(pageObj, w);

        Set<Long> enrolledIds = myEnrolledIds(viewerId, collectIds(pageObj.getRecords()));
        List<Map<String, Object>> list = new ArrayList<>(pageObj.getRecords().size());
        for (Competition c : pageObj.getRecords()) {
            list.add(toItem(c, enrolledIds.contains(c.getId())).toMap());
        }
        return PageResult.of(list, pageObj.getTotal(), p, s);
    }

    /** 竞赛详情（GET /competitions/{id}；《13》未单列但列表需跳转，按同规则提供） */
    public Map<String, Object> detail(Long viewerId, Long id) {
        Competition c = competitionMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("竞赛不存在或已下架");
        }
        boolean owner = viewerId != null && c.getPublisherId().equals(viewerId);
        CompetitionEnroll myEnroll = viewerId == null ? null : myEnroll(viewerId, id);
        boolean enrolled = myEnroll != null
                && myEnroll.getStatus() != null
                && myEnroll.getStatus() != CompetitionEnroll.STATUS_CANCELED;
        boolean contactVisible = owner || enrolled;

        Map<String, Object> m = toItem(c, enrolled).toMap();
        m.put("content", c.getContent());
        m.put("requirement", c.getRequirement());
        m.put("awards", c.getAwards());
        m.put("rule", c.getRule());
        m.put("contactVisible", contactVisible);
        m.put("contact", contactVisible ? c.getContact() : null);
        m.put("owner", owner);

        UserFacade.UserBrief u = userFacade.get(c.getPublisherId());
        Map<String, Object> publisher = new LinkedHashMap<>(4);
        publisher.put("id", c.getPublisherId());
        publisher.put("nickname", u == null ? "用户" + c.getPublisherId() : u.getNickname());
        publisher.put("avatar", u == null ? null : u.getAvatar());
        publisher.put("schoolTag", u == null ? null : u.getSchoolTag());
        m.put("publisher", publisher);

        if (myEnroll != null) {
            Map<String, Object> mine = new LinkedHashMap<>(6);
            mine.put("enrollId", myEnroll.getId());
            mine.put("status", myEnroll.getStatus());
            mine.put("statusText", enrollStatusText(myEnroll.getStatus()));
            mine.put("teamId", myEnroll.getTeamId());
            mine.put("result", myEnroll.getResult());
            mine.put("resultText", resultText(myEnroll.getResult()));
            m.put("myEnroll", mine);
        } else {
            m.put("myEnroll", null);
        }
        return m;
    }

    /**
     * 竞赛日程日历（FR-M5-04 / GET /competitions/calendar?month=yyyy-MM）。
     *
     * <p>一个月可展开 6 类日程（报名开始/截止、初赛、决赛、开赛、结束），
     * 前端按 date 分桶渲染；month 缺省取当前月。
     */
    public Map<String, Object> calendar(String month, Integer level) {
        YearMonth ym;
        try {
            ym = (month == null || month.trim().isEmpty()) ? YearMonth.now() : YearMonth.parse(month.trim());
        } catch (Exception e) {
            throw BizException.param("month 格式需为 yyyy-MM");
        }
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay();

        LambdaQueryWrapper<Competition> w = new LambdaQueryWrapper<Competition>()
                .ne(Competition::getStatus, Competition.STATUS_CANCELED);
        if (level != null) {
            w.eq(Competition::getLevel, level);
        }
        // 与本月有交集：结束时间（无则报名截止）>= 月初，且某一时间点 < 次月初
        w.and(q -> q.ge(Competition::getEnrollDeadline, from)
                .or().ge(Competition::getPreliminaryAt, from)
                .or().ge(Competition::getFinalAt, from)
                .or().ge(Competition::getStartAt, from)
                .or().ge(Competition::getEndAt, from));
        w.lt(Competition::getEnrollStart, to);
        w.orderByAsc(Competition::getEnrollStart);

        List<Competition> list = competitionMapper.selectList(w);
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Competition c : list) {
            addCalendar(items, c, CalendarItem.TYPE_ENROLL_START, c.getEnrollStart(), from, to, now);
            addCalendar(items, c, CalendarItem.TYPE_ENROLL_DEADLINE, c.getEnrollDeadline(), from, to, now);
            addCalendar(items, c, CalendarItem.TYPE_PRELIMINARY, c.getPreliminaryAt(), from, to, now);
            addCalendar(items, c, CalendarItem.TYPE_FINAL, c.getFinalAt(), from, to, now);
            addCalendar(items, c, CalendarItem.TYPE_START, c.getStartAt(), from, to, now);
            addCalendar(items, c, CalendarItem.TYPE_END, c.getEndAt(), from, to, now);
        }
        items.sort((x, y) -> {
            LocalDateTime dx = (LocalDateTime) x.get("date");
            LocalDateTime dy = (LocalDateTime) y.get("date");
            return dx.compareTo(dy);
        });

        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("month", ym.toString());
        data.put("list", items);
        data.put("total", items.size());
        return data;
    }

    /* ==================== 报名 / 成果 ==================== */

    /**
     * 报名竞赛（FR-M5-05 / POST /competitions/{id}/enroll，可关联 team_id）。
     *
     * <p>校验同活动：重复 → 40006；未开始/已截止/已满 → 40007。
     * <p>团队报名：teamId 非空时校验队伍存在且我为其成员（跨域走 {@link TeamQueryFacade}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> enroll(Long userId, Long competitionId, Map<String, Object> body) {
        Competition c = competitionMapper.selectById(competitionId);
        if (c == null) {
            throw BizException.notFound("竞赛不存在或已下架");
        }
        if (!isEnrollable(c)) {
            throw BizException.conflict("当前不在报名时间内，无法报名");
        }
        if (c.getCapacity() != null && c.getCapacity() > 0
                && nvl(c.getEnrolledCnt()) >= c.getCapacity()) {
            throw BizException.conflict("报名人数已满");
        }

        Long teamId = body == null ? null : longOrNull(body.get("teamId"));
        if (teamId != null) {
            if (teamQueryFacade == null) {
                throw new BizException(ErrorCode.THIRD_PARTY_ERROR,
                        "组队服务未就绪，暂不支持团队报名（契约缺口登记：team 校验跨模块装配）");
            }
            if (!teamQueryFacade.isMember(teamId, userId)) {
                throw new BizException(ErrorCode.FORBIDDEN, "你不是该队伍成员，无法以队伍名义报名");
            }
        }

        CompetitionEnroll exist = myEnroll(userId, competitionId);
        if (exist != null && exist.getStatus() != null
                && exist.getStatus() != CompetitionEnroll.STATUS_CANCELED
                && exist.getStatus() != CompetitionEnroll.STATUS_REJECTED) {
            throw new BizException(ErrorCode.DUPLICATE, "你已报名该竞赛，请勿重复提交");
        }

        CompetitionEnroll e;
        if (exist != null) {
            e = exist;
        } else {
            e = new CompetitionEnroll();
            e.setCompetitionId(competitionId);
            e.setUserId(userId);
        }
        e.setStatus(CompetitionEnroll.STATUS_PASSED);
        e.setResult(CompetitionEnroll.RESULT_NONE);
        e.setTeamId(teamId);
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
        countUp(competitionId);

        notifySender.send(c.getPublisherId(), Enums.NotifyBiz.EVENT,
                "新的竞赛报名",
                "「" + c.getTitle() + "」有 1 条新报名",
                Enums.TargetType.COMPETITION, competitionId, "competition-enroll:" + e.getId());

        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("enrollId", e.getId());
        data.put("status", e.getStatus());
        data.put("statusText", enrollStatusText(e.getStatus()));
        return data;
    }

    /**
     * 提交成果（FR-M5-07 / PUT /competition-enrolls/{id}/result，P2）。
     *
     * <p>仅报名者本人可提交；result 值域见《12》§2.5 competition_enroll.result。
     * <p>获奖（result 1~4）触发信用分变动由 M7 治理模块处理，本方法只落库 + 通知。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitResult(Long userId, Long enrollId, Map<String, Object> body) {
        CompetitionEnroll e = enrollMapper.selectById(enrollId);
        if (e == null) {
            throw BizException.notFound("报名记录不存在");
        }
        if (!e.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能提交自己的参赛成果");
        }
        if (e.getStatus() == null || e.getStatus() != CompetitionEnroll.STATUS_PASSED) {
            throw BizException.conflict("报名未通过，暂不能提交成果");
        }
        if (e.getResult() != null && e.getResult() != CompetitionEnroll.RESULT_NONE) {
            throw BizException.conflict("成果已提交，如需修改请联系赛事主办方");
        }

        Integer result = body == null ? null : intOrNull(body.get("result"));
        if (result == null || result < CompetitionEnroll.RESULT_NONE
                || result > CompetitionEnroll.RESULT_FINALIST) {
            throw BizException.param("成果取值需为 0 无 / 1 一等奖 / 2 二等奖 / 3 三等奖 / 4 优秀奖 / 5 入围");
        }
        e.setResult(result);
        if (body != null) {
            e.setAwardDesc(str(body.get("awardDesc")));
            e.setWorkIntro(str(body.get("workIntro")));
            e.setWorkLink(str(body.get("workLink")));
            Object files = body.get("workFileIds");
            e.setWorkFiles(files == null ? null : JsonUtil.toJson(files));
        }
        enrollMapper.updateById(e);

        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("enrollId", e.getId());
        data.put("result", e.getResult());
        data.put("resultText", resultText(e.getResult()));
        return data;
    }

    /** 我的参赛记录 + 成果展示（FR-M7-07 / GET /competitions/my-enrolls） */
    public PageResult<Map<String, Object>> myEnrolls(Long userId, Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);

        List<CompetitionEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<CompetitionEnroll>()
                .eq(CompetitionEnroll::getUserId, userId)
                .ne(CompetitionEnroll::getStatus, CompetitionEnroll.STATUS_CANCELED)
                .orderByDesc(CompetitionEnroll::getId));
        if (enrolls.isEmpty()) {
            return PageResult.empty(p, s);
        }
        Map<Long, CompetitionEnroll> byCompetition = new LinkedHashMap<>(enrolls.size());
        for (CompetitionEnroll e : enrolls) {
            byCompetition.put(e.getCompetitionId(), e);
        }
        List<Competition> list = competitionMapper.selectBatchIds(byCompetition.keySet());
        if (list == null || list.isEmpty()) {
            return PageResult.empty(p, s);
        }
        List<Map<String, Object>> all = new ArrayList<>(list.size());
        for (Competition c : list) {
            CompetitionEnroll e = byCompetition.get(c.getId());
            Map<String, Object> m = toItem(c, true).toMap();
            m.put("myEnrollId", e == null ? null : e.getId());
            m.put("myEnrollStatus", e == null ? null : e.getStatus());
            m.put("myEnrollStatusText", e == null ? null : enrollStatusText(e.getStatus()));
            m.put("result", e == null ? null : e.getResult());
            m.put("resultText", e == null ? null : resultText(e.getResult()));
            m.put("awardDesc", e == null ? null : e.getAwardDesc());
            m.put("workIntro", e == null ? null : e.getWorkIntro());
            m.put("workLink", e == null ? null : e.getWorkLink());
            all.add(m);
        }
        return paginate(all, p, s);
    }

    /* ==================== 内部工具 ==================== */

    private void addCalendar(List<Map<String, Object>> out, Competition c, int type,
                             LocalDateTime date, LocalDateTime from, LocalDateTime to,
                             LocalDateTime now) {
        if (date == null) {
            return;
        }
        if (date.isBefore(from) || !date.isBefore(to)) {
            return;
        }
        out.add(CalendarItem.of(c.getId(), c.getTitle(), c.getLevel(), type, date, date.isBefore(now)).toMap());
    }

    private boolean isEnrollable(Competition c) {
        LocalDateTime now = LocalDateTime.now();
        if (c.getStatus() == null
                || (c.getStatus() != Competition.STATUS_ENROLLING && c.getStatus() != Competition.STATUS_ONGOING)) {
            return false;
        }
        if (c.getEnrollStart() != null && now.isBefore(c.getEnrollStart())) {
            return false;
        }
        if (c.getEnrollDeadline() != null && now.isAfter(c.getEnrollDeadline())) {
            return false;
        }
        return true;
    }

    private void countUp(Long competitionId) {
        competitionMapper.update(null, new LambdaUpdateWrapper<Competition>()
                .eq(Competition::getId, competitionId)
                .setSql("enrolled_cnt = enrolled_cnt + 1"));
    }

    private CompetitionEnroll myEnroll(Long userId, Long competitionId) {
        if (userId == null) {
            return null;
        }
        List<CompetitionEnroll> list = enrollMapper.selectList(new LambdaQueryWrapper<CompetitionEnroll>()
                .eq(CompetitionEnroll::getCompetitionId, competitionId)
                .eq(CompetitionEnroll::getUserId, userId)
                .orderByDesc(CompetitionEnroll::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private Set<Long> myEnrolledIds(Long userId, List<Long> competitionIds) {
        if (userId == null || competitionIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<CompetitionEnroll> list = enrollMapper.selectList(new LambdaQueryWrapper<CompetitionEnroll>()
                .eq(CompetitionEnroll::getUserId, userId)
                .in(CompetitionEnroll::getCompetitionId, competitionIds)
                .ne(CompetitionEnroll::getStatus, CompetitionEnroll.STATUS_CANCELED));
        Set<Long> ids = new HashSet<>(list.size());
        for (CompetitionEnroll e : list) {
            ids.add(e.getCompetitionId());
        }
        return ids;
    }

    private List<Long> collectIds(List<Competition> list) {
        List<Long> ids = new ArrayList<>(list.size());
        for (Competition c : list) {
            ids.add(c.getId());
        }
        return ids;
    }

    private CompetitionItem toItem(Competition c, boolean enrolled) {
        CompetitionItem i = new CompetitionItem();
        i.setId(c.getId());
        i.setTitle(c.getTitle());
        i.setCover(c.getCover());
        i.setLevel(c.getLevel());
        i.setOrganizer(c.getOrganizer());
        i.setPlace(c.getPlace());
        i.setCapacity(c.getCapacity());
        i.setEnrolledCnt(nvl(c.getEnrolledCnt()));
        i.setEnrollStart(c.getEnrollStart());
        i.setEnrollDeadline(c.getEnrollDeadline());
        i.setPreliminaryAt(c.getPreliminaryAt());
        i.setFinalAt(c.getFinalAt());
        i.setStartAt(c.getStartAt());
        i.setEndAt(c.getEndAt());
        i.setStatus(c.getStatus());
        i.setCreatedAt(c.getCreatedAt());
        i.setEnrolled(enrolled);
        return i;
    }

    /** 竞赛报名状态文案（《12》§2.5 competition_enroll.status） */
    public static String enrollStatusText(Integer status) {
        if (status == null) {
            return "待审核";
        }
        switch (status) {
            case CompetitionEnroll.STATUS_PENDING:
                return "待审核";
            case CompetitionEnroll.STATUS_PASSED:
                return "已通过";
            case CompetitionEnroll.STATUS_REJECTED:
                return "未通过";
            case CompetitionEnroll.STATUS_CANCELED:
                return "已取消";
            default:
                return "待审核";
        }
    }

    /** 成果文案（《12》§2.5 competition_enroll.result） */
    public static String resultText(Integer result) {
        if (result == null) {
            return null;
        }
        switch (result) {
            case CompetitionEnroll.RESULT_FIRST:
                return "一等奖";
            case CompetitionEnroll.RESULT_SECOND:
                return "二等奖";
            case CompetitionEnroll.RESULT_THIRD:
                return "三等奖";
            case CompetitionEnroll.RESULT_EXCELLENT:
                return "优秀奖";
            case CompetitionEnroll.RESULT_FINALIST:
                return "入围";
            default:
                return "无";
        }
    }

    private int initialStatus(LocalDateTime enrollStart, LocalDateTime enrollDeadline) {
        LocalDateTime now = LocalDateTime.now();
        if (enrollStart != null && now.isBefore(enrollStart)) {
            return Competition.STATUS_ENROLLING;
        }
        if (enrollDeadline != null && now.isAfter(enrollDeadline)) {
            return Competition.STATUS_ENDED;
        }
        return Competition.STATUS_ENROLLING;
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

    private LocalDateTime dateOf(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return java.time.OffsetDateTime.parse(s).toLocalDateTime();
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
