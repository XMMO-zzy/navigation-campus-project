/**
 * pages/notice/index —— 官方通知 / 规则更新 / 平台公告
 *
 * 来源：
 *   《10》FR-M2-09 官方通知/规则更新/平台公告信息推荐流
 *   《13》§3.2 GET /notices、GET /notices/{id}
 *
 * 单页双模式：
 *   - 无 id：通知列表（分类型 tab）
 *   - 带 id：通知详情（从消息/信息流跳入）
 */

const api = require('../../framework/api');
const util = require('../../framework/util');

const TYPE_TABS = [
  { key: '', label: '全部' },
  { key: 'notice', label: '平台公告' },
  { key: 'rule', label: '规则更新' },
  { key: 'system', label: '系统通知' }
];

Page({
  data: {
    id: null,
    isDetail: false,

    typeTabs: TYPE_TABS,
    activeType: '',

    list: [],
    page: 1,
    hasMore: true,
    loading: true,
    loadStatus: '',
    error: false,

    detail: null,
    detailLoading: false,
    detailError: false
  },

  onLoad(query) {
    const id = query && query.id;
    if (id) {
      this.setData({ id: id, isDetail: true });
      this.loadDetail(id);
    } else {
      this.loadList(true);
    }
  },

  onPullDownRefresh() {
    if (this.data.isDetail) {
      this.loadDetail(this.data.id).then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
    } else {
      this.loadList(true).then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
    }
  },

  onReachBottom() {
    if (!this.data.isDetail) this.loadMore();
  },

  /* ------------------------------------------------------------------ */
  loadList(reset) {
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ loading: reset, loadStatus: reset ? '' : 'loading', error: false });
    return api.notice.list(page, 20, this.data.activeType)
      .then((data) => {
        const raw = (data && data.list) || [];
        const incoming = raw.map(decorate);
        this.setData({
          list: reset ? incoming : this.data.list.concat(incoming),
          page: page,
          hasMore: raw.length >= 20,
          loading: false,
          loadStatus: raw.length >= 20 ? '' : 'nomore'
        });
      })
      .catch(() => this.setData({ loading: false, error: true }));
  },

  loadMore() {
    if (!this.data.hasMore || this.data.loadStatus === 'loading' || this.data.loading) return;
    this.loadList(false);
  },

  onSwitchType(e) {
    const t = e.currentTarget.dataset.key;
    if (t === this.data.activeType) return;
    this.setData({ activeType: t, page: 1 }, () => this.loadList(true));
  },

  loadDetail(id) {
    this.setData({ detailLoading: true, detailError: false });
    return api.notice.detail(id)
      .then((d) => this.setData({ detail: decorate(d), detailLoading: false }))
      .catch(() => this.setData({ detailLoading: false, detailError: true }));
  },

  onTapItem(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/notice/index?id=${id}` });
  },

  onRetry() {
    if (this.data.isDetail) this.loadDetail(this.data.id);
    else this.loadList(!this.data.list.length);
  },

  onShareAppMessage() {
    const d = this.data.detail || {};
    return { title: d.title || '平台公告', path: `/pages/notice/index?id=${this.data.id}` };
  },

  onOpenSource() {
    const url = this.data.detail && this.data.detail.sourceUrl;
    if (!url) return;
    wx.navigateTo({ url: `/pages/webview/index?url=${encodeURIComponent(url)}` });
  }
});

const TYPE_TEXT = { notice: '平台公告', rule: '规则更新', system: '系统通知' };

function decorate(item) {
  return Object.assign({}, item, {
    typeText: TYPE_TEXT[item.type] || item.typeText || '公告',
    timeText: util.relativeTime(item.createdAt),
    dateText: util.formatTime(item.createdAt, 'date')
  });
}
