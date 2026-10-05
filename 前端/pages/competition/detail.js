/**
 * pages/competition/detail —— 竞赛详情 + 报名 + 组队
 *
 * 来源：
 *   《10》FR-M5-03 竞赛详情 / FR-M5-05 报名（可关联 team_id）/ FR-M5-07 成果提交
 *   《13》§3.5 GET /competitions/{id}、POST /competitions/{id}/enroll
 *   《12》§2.5 competition_enroll.status / result
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const C = require('../../framework/constants');

Page({
  data: {
    id: null,
    loading: true,
    error: false,
    cmp: null,
    isOwner: false,
    myEnroll: null,
    enrollStatusText: '',
    enrollStatusTag: '',
    resultText: '',
    showEnroll: false,
    enrollForm: { name: '', phone: '', teamId: '', remark: '' },
    teams: [],
    teamIndex: 0,
    teamLabels: ['个人参赛'],
    enrolling: false,
    myTeams: []
  },

  onLoad(query) {
    const id = query && query.id;
    if (!id) { this.setData({ loading: false, error: true }); return; }
    this.setData({ id });
    this.load();
    this.loadTeams();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
  },

  load() {
    return api.competition.detail(this.data.id).then((d) => {
      const me = auth.currentUser() || {};
      const statusMap = { 1: '报名中', 2: '进行中', 3: '已结束', 4: '已取消' };
      const statusTag = { 1: 'success', 2: 'warning', 3: 'gray', 4: 'gray' };
      const cmp = Object.assign({}, d, {
        levelText: C.labelOf(C.COMPETITION_LEVEL, d.level, ''),
        statusText: statusMap[d.status] || '报名中',
        statusTag: statusTag[d.status] || 'success',
        cover: d.cover || (Array.isArray(d.images) && d.images[0]) || '/static/placeholder.png',
        images: d.images || (d.cover ? [d.cover] : []),
        startText: util.formatTime(d.startAt || d.startTime, 'full'),
        endText: util.formatTime(d.endAt || d.endTime, 'full'),
        deadlineText: util.formatTime(d.enrollDeadline, 'full'),
        leftText: d.enrollDeadline ? util.deadlineLeft(d.enrollDeadline) : ''
      });
      const enroll = d.myEnroll || d.myEnrollStatus !== undefined
        ? Object.assign({}, d.myEnroll || {}, { status: d.myEnrollStatus })
        : null;
      const valid = enroll && enroll.status !== undefined;
      this.setData({
        loading: false,
        error: false,
        cmp,
        isOwner: !!(me.userId && String(d.userId || d.creatorId) === String(me.userId)),
        myEnroll: valid ? enroll : null,
        enrollStatusText: valid ? (C.ENROLL_STATUS_TEXT[enroll.status] || '') : '',
        enrollStatusTag: valid ? (C.ENROLL_STATUS_TAG[enroll.status] || 'info') : '',
        resultText: valid ? C.labelOf(C.AWARD_RESULT, enroll.result, '') : ''
      });
    }).catch(() => this.setData({ loading: false, error: true }));
  },

  /** 我关联的队伍（用于报名时关联 team_id，FR-M5-05）
   *  说明：《13》未定义"我的队伍列表"独立接口，组队信息由 GET /teams/{id} 提供。
   *      此处由详情返回的 myTeamIds 逐条拉取，不臆造列表接口。 */
  loadTeams() {
    this.setData({ myTeams: [] });
  },

  onRetry() { this.setData({ loading: true, error: false }); this.load(); },

  /* ------------------------------------------------------------------ */
  onPreviewCover() {
    const urls = (this.data.cmp && this.data.cmp.images) || [];
    if (!urls.length) return;
    wx.previewImage({ current: urls[0], urls });
  },

  onTapOwner() {
    const c = this.data.cmp || {};
    const uid = c.userId || c.creatorId || (c.creator && c.creator.id);
    if (!uid) return;
    wx.navigateTo({ url: `/pages/me/profile-edit?viewUserId=${uid}` });
  },

  onTapTeamDetail(e) {
    const id = e.currentTarget.dataset.id;
    if (!id) return;
    wx.navigateTo({ url: `/pages/team/detail?id=${id}` });
  },

  onTapApplyCenter() {
    wx.navigateTo({ url: '/pages/team/apply-center' });
  },

  /* ------------------------------------------------------------------ */
  /* 报名                                                                */
  /* ------------------------------------------------------------------ */
  onOpenEnroll() {
    if (!auth.requireAuth({ title: '报名需要先登录' })) return;
    if (!auth.requireVerified({ title: '竞赛报名' })) return;
    if (this.data.isOwner) { util.toast('你是本竞赛的发布者'); return; }
    if (this.data.myEnroll) {
      util.toast(`你已报名，当前状态：${this.data.enrollStatusText}`);
      return;
    }
    const c = this.data.cmp;
    if (c.status === 3 || c.status === 4) { util.toast('竞赛已结束或已取消'); return; }
    this.setData({ showEnroll: true, teamIndex: 0, teamLabels: ['个人参赛'] });
    this.loadMyTeams();
  },

  /** 拉取我关联的队伍（GET /teams/{id} 逐条查询由后端聚合） */
  loadMyTeams() {
    if (!this.data.cmp || !this.data.cmp.myTeamIds || !this.data.cmp.myTeamIds.length) return;
    const ids = this.data.cmp.myTeamIds;
    Promise.all(ids.map(id => api.team.detail(id).catch(() => null))).then((arr) => {
      const teams = arr.filter(Boolean).map(t => ({
        id: t.id,
        name: t.name || `队伍 #${t.id}`
      }));
      this.setData({
        teams,
        teamLabels: ['个人参赛'].concat(teams.map(t => t.name))
      });
    });
  },

  onCloseEnroll() { this.setData({ showEnroll: false }); },

  onEnrollInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['enrollForm.' + field]: e.detail.value });
  },

  onPickTeam(e) {
    const i = Number(e.detail.value);
    const team = this.data.teams[i - 1];
    this.setData({ teamIndex: i, 'enrollForm.teamId': team ? team.id : '' });
  },

  onSubmitEnroll() {
    if (this.data.enrolling) return;
    const f = this.data.enrollForm;
    if (!f.name || f.name.trim().length < 2) { util.toast('请填写真实姓名'); return; }
    if (!/^1[3-9]\d{9}$/.test((f.phone || '').trim())) { util.toast('请填写正确的手机号'); return; }

    this.setData({ enrolling: true });
    api.competition.enroll(this.data.id, {
      name: f.name.trim(),
      phone: f.phone.trim(),
      teamId: f.teamId || null,
      remark: (f.remark || '').trim()
    }).then(() => {
      this.setData({
        enrolling: false,
        showEnroll: false,
        myEnroll: { status: 0 },
        enrollStatusText: C.ENROLL_STATUS_TEXT[0],
        enrollStatusTag: C.ENROLL_STATUS_TAG[0]
      });
      util.toast('报名成功，等待审核', 'success');
      this.load();
    }).catch((err) => {
      this.setData({ enrolling: false });
      if (err && err.code === 40006) {
        util.toast('你已报名该竞赛');
        this.setData({ showEnroll: false });
        this.load();
      } else if (err && err.code === 40007) {
        util.toast('报名已截止');
        this.setData({ showEnroll: false });
      }
    });
  },

  /** 成果提交（FR-M5-07，P2） */
  onSubmitResult() {
    if (!this.data.myEnroll) return;
    const id = this.data.myEnroll.id;
    if (!id) { util.toast('暂无报名记录'); return; }
    wx.showModal({
      title: '提交竞赛成果',
      editable: true,
      placeholderText: '请填写获奖情况或作品链接',
      success: (res) => {
        if (!res.confirm || !res.content) return;
        api.competition.submitResult(id, { description: res.content }).then(() => {
          util.toast('成果已提交', 'success');
          this.load();
        }).catch(() => util.toast('提交失败，请稍后重试'));
      }
    });
  },

  onReport() {
    wx.navigateTo({ url: `/pages/me/reports?action=create&targetType=4&targetId=${this.data.id}` });
  },

  onShareAppMessage() {
    const c = this.data.cmp || {};
    return {
      title: c.title || '学科竞赛',
      path: `/pages/competition/detail?id=${this.data.id}`,
      imageUrl: c.cover || ''
    };
  },

  noop() {}
});
