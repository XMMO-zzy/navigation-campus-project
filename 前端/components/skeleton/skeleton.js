/**
 * components/skeleton —— 首屏骨架屏（FR-M7-02 / NFR-01）
 *
 * 用法：<skeleton wx:if="{{loading}}" rows="{{3}}" />
 *      type: list（信息流卡片）| grid（两栏瀑布流）| detail（详情页）
 */

Component({
  properties: {
    type: { type: String, value: 'list' },
    /** 骨架条目数量 */
    rows: { type: Number, value: 3 }
  },
  data: {
    items: []
  },
  observers: {
    'rows': function (n) {
      const count = Math.max(1, Math.min(Number(n) || 3, 10));
      this.setData({ items: new Array(count).fill(0) });
    }
  },
  lifetimes: {
    attached() {
      const count = Math.max(1, Math.min(this.data.rows || 3, 10));
      this.setData({ items: new Array(count).fill(0) });
    }
  }
});
