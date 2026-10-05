/**
 * pages/me/records —— 我的记录（活动 / 竞赛 / 内容）
 *
 * 来源：
 *   《04》4.2 记录区：商品订单 / 活动记录 / 竞赛参赛记录与成果
 *   《13》§3.5 GET /activities/my-enrolls、GET /activities/my、
 *              GET /competitions/my-enrolls、GET /competitions/my
 *   《13》§3.4 GET /goods/my?view=published（内容管理）
 *
 * tab=activity | competition | content
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const C = require('../../framework/constants');

const TABS = [
  { key: 'activity', label: '活动记录' },
  { key: 'competition', label: '竞赛记录' },
  { key: 'content', label: '内容管理' }
];

const ACTIVITY_SUB = [
  { key: 'enroll', label: '我参与的' },
  { key: 'publish', label: '我发布的' }
];

const COMP_SUB = [
  { key: 'enroll', label: '我参赛的' },
  { key: 'publish', label: '我发布的' }
];

const CONTENT_SUB = [
  { key: 'post', label: '我的动态' },
  { key: 'goods', label: '我的商品' },
  { key: 'buddy', label: '我的搭子' }
];

Page({
  data: {
    tab: 'activity',
    sub: 'enroll',
    tabs: TABS,
    subTabs: ACTIVITY_SUB,

    list: [],
    page: 1,
    hasMore: true,
    loading: false,
    loaded: false,
    loadStatus: '',
    error: false
  },

  onLoad(query) {
    const tab = (query && ['activity', 'competition', 'content'].indexOf(query.tab) > -1) ? query.tab : 'activity';
    const sub = (query && query.sub) || this.defaultSub(tab);
    this.setData({ tab, sub, subTabs: this.subTabsOf(tab) });
    wx.setNavigationBarTitle({ title: '我的记录' });
  },

  onShow() {
    if (!auth.isLogin()) {
      auth.requireAuth({ title: '查看记录' });
      return;
    }
    this.reload();
  },

  defaultSub(tab) {
    if (tab === 'content') return 'post';
    return 'enroll';
  },

  subTabsOf(tab) {
    if (tab === 'competition') return COMP_SUB;
    if (tab === 'content') return CONTENT_SUB;
    return ACTIVITY_SUB;
  },

  onSwitchTab(e) {
    const tab = e.currentTarget.dataset.key;
    if (tab === this.data.tab) return;
    this.setData({
      tab,
      sub: this.defaultSub(tab),
      subTabs: this.subTabsOf(tab),
      list: [], loaded: false
    }, () => this.reload());
  },

  onSwitchSub(e) {
    const sub = e.currentTarget.dataset.key;
    if (sub === this.data.sub) return;
    this.setData({ sub, list: [], loaded: false }, () => this.reload());
  },

  reload() {
    return this.fetch(true);
  },

  loadMore() {
    if (!this.data.hasMore || this.data.loading) return;
    this.fetch(false);
  },

  onReachBottom() { this.loadMore(); },

  onPullDownRefresh() {
    this.reload().then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
  },

  fetch(reset) {
    if (this.data.loading) return Promise.resolve();
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ loading: true, error: false, loadStatus: reset ? '' : 'loading' });

    const req = this.buildRequest(page);
    if (!req) return Promise.resolve();

    return req.then((data) => {
      const raw = (data && (data.list || data.records)) || (Array.isArray(data) ? data : []);
      const incoming = raw.map((i) => this.decorate(i));
      this.setData({
        list: reset ? incoming : this.data.list.concat(incoming),
        page,
        hasMore: raw.length >= 20,
        loading: false,
        loaded: true,
        loadStatus: raw.length >= 20 ? '' : 'nomore'
      });
    }).catch(() => {
      this.setData({ loading: false, loaded: true, error: true, loadStatus: '' });
    });
  },

  buildRequest(page) {
    const { tab, sub } = this.data;
    if (tab === 'activity') {
      return sub === 'enroll' ? api.activity.myEnrolls() : api.activity.myPublished(page, 20);
    }
    if (tab === 'competition') {
      return sub === 'enroll' ? api.competition.myEnrolls() : api.competition.myPublished(page, 20);
    }
    if (tab === 'content') {
      if (sub === 'goods') return api.goods.my('published', page, 20);
      if (sub === 'buddy') return api.buddy.my('published', page, 20);
      // 我的动态：复用信息流接口按作者过滤的约定字段
      return api.feed.list('campus', page, 20, { mine: 1 });
    }
    return null;
  },

  decorate(item) {
    const { tab } = this.data;
    const o = Object.assign({}, item);
    o.image = o.image || (Array.isArray(o.images) && o.images[0]) || '';
    o.timeText = util.relativeTime(o.createdAt);
    o.title = o.title || o.name || o.content || '';

    if (tab === 'activity' && this.data.sub === 'enroll') {
      o.statusText = C.ENROLL_STATUS_TEXT[o.status] || '—';
      o.statusTag = C.ENROLL_STATUS_TAG[o.status] || 'tag';
    } else if (tab === 'competition' && this.data.sub === 'enroll') {
      o.statusText = C.ENROLL_STATUS_TEXT[o.status] || '—';
      o.statusTag = C.ENROLL_STATUS_TAG[o.status] || 'tag';
      if (o.result !== undefined && o.result !== null && o.result > 0) {
        o.resultText = C.labelOf(C.AWARD_RESULT, o.result, '');
      }
    } else if (tab === 'content' && this.data.sub === 'goods') {
      o.priceText = util.formatPrice(o.price);
      o.statusText = o.status === 1 ? '在售' : (o.status === 2 ? '已下架' : '已售出');
    } else if (tab === 'content' && this.data.sub === 'buddy') {
      o.statusText = o.status === 1 ? '招募中' : '已结束';
    }
    return o;
  },

  onTapItem(e) {
    const item = e.currentTarget.dataset.item;
    const { tab, sub } = this.data;
    if (tab === 'activity') {
      const id = item.activityId || item.id;
      if (sub === 'enroll') {
        wx.navigateTo({ url: `/pages/activity/detail?id=${id}` });
      } else {
        wx.navigateTo({ url: `/pages/activity/detail?id=${item.id}` });
      }
    } else if (tab === 'competition') {
      const id = item.competitionId || item.id;
      wx.navigateTo({ url: `/pages/competition/detail?id=${id}` });
    } else {
      if (sub === 'goods') wx.navigateTo({ url: `/pages/goods/detail?id=${item.id}` });
      else if (sub === 'buddy') wx.navigateTo({ url: `/pages/buddy/detail?id=${item.id}` });
      else wx.navigateTo({ url: `/pages/post/detail?id=${item.id}` });
    }
  },

  onRetry() {
    this.setData({ error: false, loaded: false });
    this.reload();
  }
});
