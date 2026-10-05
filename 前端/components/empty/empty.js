/**
 * components/empty —— 空态组件（FR-M7-02）
 *
 * 来源：《H-设计-体验与设计系统》§4.4「空态引导：每个列表空态都带"去发布/去逛逛"行动点」
 * 用法：
 *   <empty state="empty|offline|error" icon="📭" text="还没有内容"
 *          action-text="去发布" bind:action="onAction" />
 */

Component({
  properties: {
    /** 状态：empty（空）/ error（失败重试）/ offline（离线） */
    state: { type: String, value: 'empty' },
    /** 图标文案（emoji，避免引入图片资源） */
    icon: { type: String, value: '📭' },
    /** 主文案 */
    text: { type: String, value: '这里还没有内容' },
    /** 辅助说明 */
    desc: { type: String, value: '' },
    /** 操作按钮文案，为空则不渲染 */
    actionText: { type: String, value: '' },
    /** 次要操作按钮文案 */
    subActionText: { type: String, value: '' }
  },

  methods: {
    onAction() { this.triggerEvent('action'); },
    onSubAction() { this.triggerEvent('subaction'); }
  }
});
