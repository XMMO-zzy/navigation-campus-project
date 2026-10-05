/**
 * pages/team/detail —— 队伍详情（成员 / 分工 / 公告 / 进度）
 *
 * 来源：
 *   《10》FR-M3-04 队伍详情
 *   《13》§3.3 GET /teams/{id}、PUT /teams/{teamId}/members/{uid}、POST /teams/{teamId}/apply
 *   《10》FR-M5-01 移除成员 / 转让队长（队长权限）
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const C = require('../../framework/constants');

const ROLE_TEXT = { 1: '队长', 2: '副队长', 3: '成员' };

Page({
  data: {
    id: null,
    loading: true,
    error: false,
    team: null,
    isLeader: false,
    isMember: false,
    applied: false,
    members: [],
    showApply: false,
    applyMessage: '',
    applying: false,
    showNotice: false,
    noticeDraft: ''
  },

  onLoad(query) {
    const id = query && query.id;
    if (!id) { this.setData({ loading: false, error: true }); return; }
    this.setData({ id });
    if (!auth.requireAuth({ title: '查看队伍需要先登录' })) {
      this.setData({ loading: false, error: true });
      return;
    }
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
  },

  load() {
    return api.team.detail(this.data.id).then((d) => {
      const me = auth.currentUser() || {};
      const myUid = me.userId;
      const members = (d.members || []).map((m) => Object.assign({}, m, {
        roleText: ROLE_TEXT[m.role] || '成员',
        isMe: !!(myUid && String(m.userId || m.id) === String(myUid))
      }));
      const mine = members.filter(m => m.isMe)[0];
      const leaderId = d.leaderId || (d.leader && d.leader.id) ||
        (members.filter(m => m.role === 1)[0] || {}).userId;
      this.setData({
        loading: false,
        error: false,
        team: Object.assign({}, d, {
          typeText: d.bizType === 2 ? '竞赛组队' : '活动组队',
          memberText: `${members.length}/${d.memberLimit || d.maxMembers || '不限'} 人`,
          createdAtText: util.formatTime(d.createdAt, 'date')
        }),
        members,
        isLeader: !!(myUid && String(leaderId) === String(myUid)),
        isMember: !!mine,
        applied: !!d.applied
      });
    }).catch(() => this.setData({ loading: false, error: true }));
  },

  onRetry() { this.setData({ loading: true, error: false }); this.load(); },

  /* ------------------------------------------------------------------ */
  onTapMember(e) {
    const uid = e.currentTarget.dataset.uid;
    if (!uid) return;
    wx.navigateTo({ url: `/pages/me/profile-edit?viewUserId=${uid}` });
  },

  /** 队长：成员管理（移除成员 / 转让队长）| FR-M5-01 */
  onManageMember(e) {
    if (!this.data.isLeader) return;
    const uid = e.currentTarget.dataset.uid;
    const member = this.data.members.filter(m => String(m.userId || m.id) === String(uid))[0];
    if (!member || member.isMe) return;

    const isLeaderMember = member.role === 1;
    const items = isLeaderMember ? ['转让队长'] : ['设为副队长', '转让队长', '移除成员'];

    wx.showActionSheet({
      itemList: items,
      success: (res) => {
        const label = items[res.tapIndex];
        if (label === '移除成员') {
          util.confirm(`确定将 ${member.nickname || '该成员'} 移出队伍？`, '移除成员', '移除').then((ok) => {
            if (!ok) return;
            this.updateMember(uid, { action: 'remove' }, '已移除成员');
          });
        } else if (label === '转让队长') {
          util.confirm(`转让后你将变为普通成员，确定将队长转让给 ${member.nickname || '该成员'}？`, '转让队长', '确认转让')
            .then((ok) => {
              if (!ok) return;
              this.updateMember(uid, { action: 'transfer' }, '已转让队长');
            });
        } else if (label === '设为副队长') {
          this.updateMember(uid, { action: 'setRole', role: 2 }, '已设为副队长');
        }
      },
      fail: () => {}
    });
  },

  updateMember(uid, data, tip) {
    wx.showLoading({ title: '处理中', mask: true });
    api.team.updateMember(this.data.id, uid, data).then(() => {
      wx.hideLoading();
      util.toast(tip, 'success');
      this.load();
    }).catch(() => wx.hideLoading());
  },

  /* ------------------------------------------------------------------ */
  /* 申请加入                                                            */
  /* ------------------------------------------------------------------ */
  onOpenApply() {
    if (this.data.isMember) { util.toast('你已是该队伍成员'); return; }
    if (this.data.applied) { util.toast('你已提交过申请，请等待处理'); return; }
    this.setData({ showApply: true, applyMessage: '' });
  },

  onCloseApply() { this.setData({ showApply: false }); },

  onApplyInput(e) { this.setData({ applyMessage: e.detail.value }); },

  onSubmitApply() {
    if (this.data.applying) return;
    const msg = this.data.applyMessage.trim();
    if (msg.length < 5) { util.toast('请至少填写 5 个字，说明你的优势'); return; }

    this.setData({ applying: true });
    api.team.apply(this.data.id, { message: msg }).then(() => {
      this.setData({ applying: false, showApply: false, applied: true });
      util.toast('申请已发送，等待队长确认', 'success');
    }).catch((err) => {
      this.setData({ applying: false });
      if (err && err.code === 40006) {
        this.setData({ showApply: false, applied: true });
        util.toast('你已提交过申请');
      }
    });
  },

  /* ------------------------------------------------------------------ */
  /* 公告（队长编辑）                                                      */
  /* ------------------------------------------------------------------ */
  onEditNotice() {
    if (!this.data.isLeader) return;
    this.setData({ showNotice: true, noticeDraft: (this.data.team && this.data.team.notice) || '' });
  },

  onCloseNotice() { this.setData({ showNotice: false }); },
  onNoticeInput(e) { this.setData({ noticeDraft: e.detail.value }); },

  onSubmitNotice() {
    const notice = this.data.noticeDraft.trim();
    if (notice.length > 300) { util.toast('公告不能超过 300 字'); return; }
    // 说明：《13》§3.3 未定义队伍公告更新接口；避免臆造路径，
    //      此处仅提示需在契约评审后启用。
    this.setData({ showNotice: false });
    util.toast('队伍公告编辑接口待契约评审后启用');
  },

  onTapApplyCenter() {
    wx.navigateTo({ url: `/pages/team/apply-center?direction=received` });
  },

  onShareAppMessage() {
    const t = this.data.team || {};
    return {
      title: `${t.name || '组队'} · 邀请你加入队伍`,
      path: `/pages/team/detail?id=${this.data.id}`
    };
  },

  noop() {}
});
