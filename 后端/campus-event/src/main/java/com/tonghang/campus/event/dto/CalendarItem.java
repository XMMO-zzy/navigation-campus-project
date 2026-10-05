package com.tonghang.campus.event.dto;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 竞赛日程条目（《13-API接口文档》§3.5 GET /competitions/calendar?month=）。
 *
 * <p>一个竞赛可产生多条日程（报名开始 / 报名截止 / 初赛 / 决赛 / 结束），
 * 前端日历按 date 分组渲染。字段取自《12》§2.5 competition 的四个时间列。
 */
public class CalendarItem {

    /** 日程类型：1 报名开始 2 报名截止 3 初赛 4 决赛 5 开赛 6 结束 */
    public static final int TYPE_ENROLL_START = 1;
    public static final int TYPE_ENROLL_DEADLINE = 2;
    public static final int TYPE_PRELIMINARY = 3;
    public static final int TYPE_FINAL = 4;
    public static final int TYPE_START = 5;
    public static final int TYPE_END = 6;

    private Long competitionId;
    private String title;
    private Integer level;
    private Integer type;
    private String typeText;
    private LocalDateTime date;
    /** 是否已过期（早于当前时间） */
    private boolean past;

    public static CalendarItem of(Long competitionId, String title, Integer level,
                                 int type, LocalDateTime date, boolean past) {
        CalendarItem c = new CalendarItem();
        c.competitionId = competitionId;
        c.title = title;
        c.level = level;
        c.type = type;
        c.typeText = typeText(type);
        c.date = date;
        c.past = past;
        return c;
    }

    /** 日程类型文案 */
    public static String typeText(Integer type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case TYPE_ENROLL_START:
                return "报名开始";
            case TYPE_ENROLL_DEADLINE:
                return "报名截止";
            case TYPE_PRELIMINARY:
                return "初赛";
            case TYPE_FINAL:
                return "决赛";
            case TYPE_START:
                return "开赛";
            case TYPE_END:
                return "结束";
            default:
                return "日程";
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>(8);
        m.put("competitionId", competitionId);
        m.put("title", title);
        m.put("level", level);
        m.put("levelText", CompetitionItem.levelText(level));
        m.put("type", type);
        m.put("typeText", typeText);
        m.put("date", date);
        m.put("past", past);
        return m;
    }

    /* ---------------- getter / setter ---------------- */

    public Long getCompetitionId() { return competitionId; }
    public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public String getTypeText() { return typeText; }
    public void setTypeText(String typeText) { this.typeText = typeText; }
    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }
    public boolean isPast() { return past; }
    public void setPast(boolean past) { this.past = past; }
}
