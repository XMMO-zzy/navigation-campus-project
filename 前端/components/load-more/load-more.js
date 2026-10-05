/**
 * components/load-more —— 分页加载状态（FR-M7-02：分页加载 / 失败重试）
 *
 * 用法：<load-more status="{{loadStatus}}" bind:retry="onRetry" />
 * status: loading | nomore | error | ''
 */

Component({
  properties: {
    status: { type: String, value: '' },
    /** 无数据文案 */
    nomoreText: { type: String, value: '没有更多了' },
    /** 空数据文案（列表首屏为空时的兜底） */
    emptyText: { type: String, value: '暂无内容' }
  },
  methods: {
    onRetry() { this.triggerEvent('retry'); }
  }
});
