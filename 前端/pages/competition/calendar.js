/**
 * pages/competition/calendar —— 竞赛日程日历（报名 / 初赛 / 决赛 / 截止）
 *
 * 来源：
 *   《10》FR-M5-04 竞赛日程日历
 *   《13》§3.5 GET /competitions/calendar?month=YYYY-MM
 */

const api = require('../../framework/api');
const util = require('../../framework/util');
const C = require('../../framework/constants');

const WEEK = ['日', '一', '二', '三', '四', '五', '六'];

Page({
  data: {
    week: WEEK,
    year: 0,
    month: 0,               // 1~12
    monthLabel: '',
    days: [],               // { day, date, inMonth, isToday, dots: [], count }
    selected: '',           // YYYY-MM-DD
    selectedList: [],
    loading: true,
    error: false,
    totalCount: 0,
    legend: [
      { key: 'enroll', label: '报名', color: '#1E6FE8' },
      { key: 'prelim', label: '初赛', color: '#F79009' },
      { key: 'final', label: '决赛', color: '#F04438' },
      { key: 'deadline', label: '截止', color: '#7A5AF8' }
    ]
  },

  onLoad() {
    const now = new Date();
    this.setData({ year: now.getFullYear(), month: now.getMonth() + 1 });
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
  },

  load() {
    this.setData({ loading: true, error: false });
    const month = `${this.data.year}-${pad(this.data.month)}`;
    return api.competition.calendar(month).then((data) => {
      // data 形如 { list: [ { date: 'YYYY-MM-DD', type: 'enroll|prelim|final|deadline', title, competitionId } ] }
      const events = (data && (data.list || data.events)) || [];
      this.setData({ loading: false, error: false });
      this.buildGrid(events);
    }).catch(() => this.setData({ loading: false, error: true }));
  },

  buildGrid(events) {
    const { year, month } = this.data;
    const byDate = {};
    events.forEach((e) => {
      const d = (e.date || e.at || '').slice(0, 10);
      if (!d) return;
      if (!byDate[d]) byDate[d] = [];
      byDate[d].push(e);
    });

    const firstDay = new Date(year, month - 1, 1).getDay();
    const daysInMonth = new Date(year, month, 0).getDate();
    const prevDays = new Date(year, month - 1, 0).getDate();
    const todayStr = todayString();
    const days = [];

    for (let i = firstDay - 1; i >= 0; i--) {
      days.push({ day: prevDays - i, inMonth: false, date: '', dots: [], count: 0 });
    }
    for (let d = 1; d <= daysInMonth; d++) {
      const date = `${year}-${pad(month)}-${pad(d)}`;
      const list = byDate[date] || [];
      const dots = [];
      const seen = {};
      list.forEach((e) => {
        const t = e.type || 'enroll';
        if (!seen[t]) { seen[t] = true; dots.push(t); }
      });
      days.push({
        day: d,
        inMonth: true,
        date,
        dots,
        count: list.length,
        isToday: date === todayStr
      });
    }
    while (days.length % 7 !== 0) {
      days.push({ day: days.length, inMonth: false, date: '', dots: [], count: 0 });
    }

    const selected = this.data.selected && this.data.selected.indexOf(`${year}-${pad(month)}`) === 0
      ? this.data.selected
      : (todayStr.indexOf(`${year}-${pad(month)}`) === 0 ? todayStr : '');

    this._byDate = byDate;   // 缓存当日事件，供点击日期时取用

    this.setData({
      days,
      monthLabel: `${year} 年 ${month} 月`,
      totalCount: events.length,
      selected,
      selectedList: this.decorateEvents(byDate[selected] || [])
    });
  },

  decorateEvents(list) {
    return (list || []).map((e) => {
      const typeText = { enroll: '报名', prelim: '初赛', final: '决赛', deadline: '报名截止' };
      const typeColor = { enroll: '#1E6FE8', prelim: '#F79009', final: '#F04438', deadline: '#7A5AF8' };
      const t = e.type || 'enroll';
      return Object.assign({}, e, {
        typeText: typeText[t] || '日程',
        typeColor: typeColor[t] || '#1E6FE8'
      });
    });
  },

  /* ------------------------------------------------------------------ */
  onPrevMonth() {
    let { year, month } = this.data;
    month -= 1;
    if (month < 1) { month = 12; year -= 1; }
    this.setData({ year, month, selected: '' }, () => this.load());
  },

  onNextMonth() {
    let { year, month } = this.data;
    month += 1;
    if (month > 12) { month = 1; year += 1; }
    this.setData({ year, month, selected: '' }, () => this.load());
  },

  onToday() {
    const now = new Date();
    this.setData({
      year: now.getFullYear(),
      month: now.getMonth() + 1,
      selected: todayString()
    }, () => this.load());
  },

  onPickDay(e) {
    const date = e.currentTarget.dataset.date;
    if (!date) return;
    const cached = (this._byDate && this._byDate[date]) || [];
    this.setData({
      selected: date,
      selectedList: this.decorateEvents(cached)
    });
  },

  onTapEvent(e) {
    const id = e.currentTarget.dataset.id;
    if (!id) return;
    wx.navigateTo({ url: `/pages/competition/detail?id=${id}` });
  },

  onTapPublish() { wx.navigateTo({ url: '/pages/competition/publish' }); },

  onRetry() { this.load(); }
});

function pad(n) { return n < 10 ? '0' + n : '' + n; }

function todayString() {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
