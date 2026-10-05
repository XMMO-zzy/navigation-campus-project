/**
 * framework/util.js —— 通用工具函数
 */

/**
 * 时间格式化
 * @param {string|number|Date} input ISO8601 字符串或时间戳
 * @param {'full'|'date'|'time'|'relative'|'monthDay'} mode
 */
function formatTime(input, mode = 'full') {
  if (!input) return '';
  let d;
  if (typeof input === 'string') {
    // iOS 兼容：ISO8601 含 +08:00 需转成 / 分隔或时间戳
    d = new Date(input.replace(/-/g, '/').replace('T', ' ').replace(/\+08:00$/, '').replace(/\.\d+Z?$/, ''));
    if (isNaN(d.getTime())) d = new Date(input.replace(/-/g, '/').replace('T', ' ').replace(/\.\d+.*$/, ''));
  } else {
    d = new Date(input);
  }
  if (isNaN(d.getTime())) return String(input);

  const p = n => (n < 10 ? '0' + n : '' + n);
  const Y = d.getFullYear(), M = p(d.getMonth() + 1), D = p(d.getDate());
  const h = p(d.getHours()), m = p(d.getMinutes());

  switch (mode) {
    case 'date': return `${Y}-${M}-${D}`;
    case 'time': return `${h}:${m}`;
    case 'monthDay': return `${M}-${D}`;
    case 'relative': return relativeTime(d);
    default: return `${Y}-${M}-${D} ${h}:${m}`;
  }
}

/** 相对时间（刚刚 / N分钟前 / N小时前 / N天前 / 日期） */
function relativeTime(input) {
  const d = input instanceof Date ? input : new Date(String(input).replace(/-/g, '/').replace('T', ' ').replace(/\+08:00$/, '').replace(/\.\d+Z?$/, ''));
  if (isNaN(d.getTime())) return '';
  const diff = Date.now() - d.getTime();
  const min = 60 * 1000, hour = 60 * min, day = 24 * hour;

  if (diff < 0) return formatTime(d, 'full');
  if (diff < min) return '刚刚';
  if (diff < hour) return `${Math.floor(diff / min)}分钟前`;
  if (diff < day) return `${Math.floor(diff / hour)}小时前`;
  if (diff < 7 * day) return `${Math.floor(diff / day)}天前`;
  return formatTime(d, 'date');
}

/**
 * 剩余时间文案（FR-M3-05「即将截止（剩余时间）」）
 * @returns {string} 如「3天」「5小时」「已截止」
 */
function deadlineLeft(deadline) {
  if (!deadline) return '';
  const d = new Date(String(deadline).replace(/-/g, '/').replace('T', ' ').replace(/\+08:00$/, '').replace(/\.\d+Z?$/, ''));
  if (isNaN(d.getTime())) return '';
  const diff = d.getTime() - Date.now();
  if (diff <= 0) return '已截止';
  const day = 24 * 3600 * 1000, hour = 3600 * 1000, min = 60 * 1000;
  if (diff >= day) return `${Math.floor(diff / day)}天`;
  if (diff >= hour) return `${Math.floor(diff / hour)}小时`;
  return `${Math.max(1, Math.floor(diff / min))}分钟`;
}

/** 金额格式化：¥699.00（区域性约定：¥ 为默认货币符号） */
function formatPrice(v) {
  if (v === null || v === undefined || v === '') return '';
  const n = Number(v);
  if (isNaN(n)) return String(v);
  return '¥' + n.toFixed(2).replace(/\.00$/, '');
}

/** 手机号脱敏 138****5678（NFR-07） */
function maskPhone(phone) {
  if (!phone) return '';
  const s = String(phone);
  if (s.length < 7) return s;
  return s.slice(0, 3) + '****' + s.slice(-4);
}

/** 学号脱敏（保留前 2 后 2） */
function maskStudentNo(no) {
  if (!no) return '';
  const s = String(no);
  if (s.length <= 4) return '****';
  return s.slice(0, 2) + '****' + s.slice(-2);
}

/** 数值格式化：1.2万 */
function formatCount(n) {
  const v = Number(n) || 0;
  if (v < 10000) return String(v);
  return (v / 10000).toFixed(1).replace(/\.0$/, '') + '万';
}

/** 节流 */
function throttle(fn, wait = 1000) {
  let last = 0;
  return function (...args) {
    const now = Date.now();
    if (now - last < wait) return;
    last = now;
    return fn.apply(this, args);
  };
}

/** 防抖 */
function debounce(fn, wait = 300) {
  let timer = null;
  return function (...args) {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), wait);
  };
}

/** 简易 Toast（icon: success/error/loading/none） */
function toast(title, icon = 'none', duration = 2000) {
  wx.showToast({ title, icon, duration, mask: icon === 'loading' });
}

/** 确认弹窗（Promise 化） */
function confirm(content, title = '提示', confirmText = '确定') {
  return new Promise(resolve => {
    wx.showModal({
      title,
      content,
      confirmText,
      confirmColor: '#1E6FE8',
      success: res => resolve(!!res.confirm),
      fail: () => resolve(false)
    });
  });
}

/** 图片预览 */
function previewImage(urls, current) {
  wx.previewImage({ urls: Array.isArray(urls) ? urls : [urls], current: current || urls[0] });
}

/** 点击复制 */
function copy(text) {
  wx.setClipboardData({
    data: String(text),
    success: () => toast('已复制', 'success')
  });
}

/** 空值兜底 */
function orDash(v) {
  return (v === null || v === undefined || v === '') ? '—' : v;
}

/**
 * 草稿节流保存器（FR-M2-10：节流 2s）
 * 返回一个函数，连续调用时最多每 wait 毫秒执行一次，并在最后一次调用后补一次
 */
function throttleSave(fn, wait = 2000) {
  let last = 0;
  let timer = null;
  return function (...args) {
    const now = Date.now();
    if (now - last >= wait) {
      last = now;
      return fn.apply(this, args);
    }
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      last = Date.now();
      timer = null;
      fn.apply(this, args);
    }, wait - (now - last));
  };
}

/** 解析生日/月龄无关：判断是否为今天 */
function isToday(input) {
  return formatTime(input, 'date') === formatTime(Date.now(), 'date');
}

module.exports = {
  formatTime,
  relativeTime,
  deadlineLeft,
  formatPrice,
  maskPhone,
  maskStudentNo,
  formatCount,
  throttle,
  debounce,
  toast,
  confirm,
  previewImage,
  copy,
  orDash,
  throttleSave,
  isToday
};
