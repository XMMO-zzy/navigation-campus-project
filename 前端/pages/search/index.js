/**
 * pages/search/index —— 全局搜索
 *
 * 来源：
 *   《10》FR-M2-07 搜索：全局搜索（动态/帖子/重要消息/校园话题四类聚合）、
 *              搜索历史（单条删除 + 一键清空二次确认）、热门搜索
 *   《13》§3.2 GET /search?kw=&type=、GET/DELETE /search/history、GET /search/hot
 */

const api = require('../../framework/api');
const util = require('../../framework/util');
const store = require('../../framework/storage');
const { contentTypeRoute, CONTENT_TYPE } = require('../../framework/constants');

const TYPE_TABS = [
  { key: '', label: '综合' },
  { key: 'post', label: '动态' },
  { key: 'topic', label: '话题' },
  { key: 'notice', label: '公告' },
  { key: 'message', label: '消息' }
];

Page({
  data: {
    kw: '',
    activeType: '',
    typeTabs: TYPE_TABS,

    history: [],
    hot: [],

    results: [],
    page: 1,
    total: 0,
    hasMore: false,
    loading: false,
    searched: false,
    loadStatus: '',
    error: false
  },

  onLoad() {
    this.loadHistory();
    this.loadHot();
  },

  /* ------------------------------------------------------------------ */
  loadHistory() {
    // 服务端（Redis）为主；为空或不可用时退化到本地缓存，保证历史不会"凭空消失"
    const local = () => this.setData({
      history: store.searchHistory.list().map(kw => ({ id: kw, kw: kw }))
    });
    api.search.history().then((data) => {
      const list = (data && (data.list || data)) || [];
      if (list.length) {
        this.setData({ history: list.map(i => (typeof i === 'string' ? { id: i, kw: i } : i)) });
      } else {
        local();
      }
    }).catch(local);
  },

  loadHot() {
    api.search.hot().then((data) => {
      const list = (data && (data.list || data)) || [];
      this.setData({ hot: list.map(i => (typeof i === 'string' ? i : (i.kw || i.word || ''))) });
    }).catch(() => {});
  },

  /* ------------------------------------------------------------------ */
  onInput(e) {
    this.setData({ kw: e.detail.value });
  },

  onClearInput() {
    this.setData({ kw: '', results: [], searched: false });
  },

  onConfirm() {
    this.search(true);
  },

  onTapKeyword(e) {
    const kw = e.currentTarget.dataset.kw;
    this.setData({ kw: kw }, () => this.search(true));
  },

  onSwitchType(e) {
    const t = e.currentTarget.dataset.key;
    if (t === this.data.activeType) return;
    this.setData({ activeType: t }, () => {
      if (this.data.kw.trim()) this.search(true);
    });
  },

  search(reset) {
    const kw = this.data.kw.trim();
    if (!kw) {
      util.toast('请输入搜索关键词');
      return;
    }
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ loading: true, loadStatus: reset ? '' : 'loading', error: false });
    if (reset) this.saveHistory(kw);

    api.search.query(kw, this.data.activeType, page, 20).then((data) => {
      const raw = (data && data.list) || [];
      const incoming = raw.map(decorate);
      this.setData({
        results: reset ? incoming : this.data.results.concat(incoming),
        page: page,
        total: (data && data.total) || incoming.length,
        hasMore: raw.length >= 20,
        loading: false,
        searched: true,
        loadStatus: raw.length >= 20 ? '' : 'nomore'
      });
    }).catch(() => {
      this.setData({ loading: false, searched: true, error: true });
    });
  },

  onReachBottom() {
    if (this.data.hasMore && this.data.searched) this.search(false);
  },

  saveHistory(kw) {
    // 本地也存一份：作为服务端 Redis 不可用时的兜底展示
    // （已确认口径：服务端 Redis 为主 + 前端本地存储兜底，不改《12》表结构）
    store.searchHistory.add(kw);
    const list = this.data.history.filter(h => (h.kw || h) !== kw);
    list.unshift({ id: kw, kw: kw });
    this.setData({ history: list.slice(0, 20) });
  },

  onRemoveHistory(e) {
    const id = e.currentTarget.dataset.id;
    const kw = e.currentTarget.dataset.kw;
    api.search.removeHistory(id).catch(() => {});
    // 本地同步删除，避免服务端删成功但本地兜底里还留着
    if (kw || id) store.searchHistory.removeOne(kw || id);
    this.setData({ history: this.data.history.filter(h => (h.id || h.kw) !== (id || kw)) });
  },

  onClearHistory() {
    util.confirm('确定清空全部搜索历史？').then((ok) => {
      if (!ok) return;
      api.search.clearHistory().catch(() => {});
      store.searchHistory.clear();
      this.setData({ history: [] });
      util.toast('已清空');
    });
  },

  onTapResult(e) {
    const item = e.currentTarget.dataset.item;
    const route = contentTypeRoute(item.contentType, item.contentId);
    if (route) {
      wx.navigateTo({ url: route, fail: () => util.toast('目标内容不可用') });
      return;
    }
    // TOPIC(9) / MESSAGE(10) 无独立详情页，单独处理（见 constants.contentTypeRoute 注释）
    if (item.contentType === CONTENT_TYPE.TOPIC) {
      // 话题详情页尚未交付，只能看到话题名与热度
      util.toast('话题页开发中，暂不支持跳转');
    } else if (item.contentType === CONTENT_TYPE.MESSAGE) {
      // 消息是 TabBar 页，必须用 switchTab
      wx.switchTab({ url: '/pages/message/index', fail: () => util.toast('消息页不可用') });
    }
  },

  onRetry() {
    this.search(!this.data.results.length);
  }
});

const TYPE_TEXT = { post: '动态', topic: '话题', notice: '公告', message: '消息' };

function decorate(item) {
  return Object.assign({}, item, {
    typeText: TYPE_TEXT[item.type] || item.typeText || '动态',
    timeText: util.relativeTime(item.createdAt),
    summary: item.summary || item.content || '',
    title: item.title || item.content || ''
  });
}
