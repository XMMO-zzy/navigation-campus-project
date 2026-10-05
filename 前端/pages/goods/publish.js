// pages/goods/publish.js — 二手商品发布（3步向导）
const api = require('../../framework/api');
const util = require('../../framework/util');
const auth = require('../../framework/auth');
const C = require('../../framework/constants');

// 品类 / 成色取自《12》§2.4 字典（TINYINT），真值可由 GET /configs 覆盖
const CATEGORIES = C.GOODS_CATEGORY;
const CONDITIONS = C.GOODS_COND;

function emptyForm() {
  return {
    images: [],          // 本地路径或已上传 url
    title: '',
    description: '',
    price: '',
    originPrice: '',
    category: '',        // 数值 value
    cond: '',            // 数值 value
    tradePlace: '',
    contact: ''
  };
}

Page({
  data: {
    step: 1,
    categories: CATEGORIES,
    conditions: CONDITIONS,
    categoryLabels: CATEGORIES.map(c => c.label),
    conditionLabels: CONDITIONS.map(c => c.label),
    categoryText: '',
    condText: '',
    form: emptyForm(),
    submitting: false
  },

  onLoad(options) {
    if (options && options.id) {
      this.goodsId = options.id;
      wx.setNavigationBarTitle({ title: '编辑商品' });
      this.loadDetail(options.id);
    }
    // 恢复草稿
    if (!options || !options.id) {
      const draft = wx.getStorageSync('goods_publish_draft');
      if (draft && draft.savedAt && Date.now() - draft.savedAt < 7 * 24 * 3600 * 1000) {
        this.setData({ form: Object.assign(emptyForm(), draft.form) });
      }
    }
  },

  onUnload() {
    this.saveDraft();
  },

  saveDraft() {
    const f = this.data.form;
    if (!f.title && !f.description && !f.images.length) return;
    wx.setStorageSync('goods_publish_draft', { form: f, savedAt: Date.now() });
  },

  loadDetail(id) {
    wx.showLoading({ title: '加载中' });
    api.goods.detail(id).then((data) => {
      wx.hideLoading();
      const cat = data.category !== undefined ? data.category : data.categoryId;
      const cond = data.cond !== undefined ? data.cond : data.condition;
      this.setData({
        form: {
          images: data.images || (data.image ? [data.image] : []),
          title: data.title || '',
          description: data.description || '',
          price: data.price === 0 ? '' : String(data.price),
          originPrice: data.originPrice ? String(data.originPrice) : '',
          category: cat !== undefined && cat !== null ? cat : '',
          cond: cond !== undefined && cond !== null ? cond : '',
          tradePlace: data.tradePlace || '',
          contact: data.contact || ''
        },
        categoryText: C.labelOf(CATEGORIES, cat, ''),
        condText: C.labelOf(CONDITIONS, cond, '')
      });
    }).catch(() => wx.hideLoading());
  },

  /* ---------- 步骤切换 ---------- */
  onNext() {
    if (!this.validateStep(this.data.step)) return;
    if (this.data.step < 3) {
      this.setData({ step: this.data.step + 1 });
      wx.pageScrollTo({ scrollTop: 0, duration: 200 });
    }
  },

  onPrev() {
    if (this.data.step > 1) {
      this.setData({ step: this.data.step - 1 });
      wx.pageScrollTo({ scrollTop: 0, duration: 200 });
    } else {
      wx.navigateBack();
    }
  },

  onTapStep(e) {
    const target = Number(e.currentTarget.dataset.step);
    if (target < this.data.step) {
      this.setData({ step: target });
    }
  },

  validateStep(step) {
    const f = this.data.form;
    if (step === 1) {
      if (!f.images.length) {
        util.toast('请至少上传 1 张商品图片');
        return false;
      }
      return true;
    }
    if (step === 2) {
      if (!f.title || f.title.trim().length < 2) {
        util.toast('请输入商品标题（至少 2 个字）');
        return false;
      }
      if (f.title.trim().length > 40) {
        util.toast('商品标题不能超过 40 个字');
        return false;
      }
      if (f.description.length > 500) {
        util.toast('商品描述不能超过 500 个字');
        return false;
      }
      if (f.price === '' || isNaN(Number(f.price)) || Number(f.price) < 0) {
        util.toast('请输入正确的价格');
        return false;
      }
      if (Number(f.price) > 99999) {
        util.toast('价格超出上限');
        return false;
      }
      if (!f.category && f.category !== 0) {
        util.toast('请选择商品分类');
        return false;
      }
      if (!f.cond && f.cond !== 0) {
        util.toast('请选择商品成色');
        return false;
      }
      return true;
    }
    return true;
  },

  /* ---------- 图片 ---------- */
  onChooseImage() {
    const rest = 9 - this.data.form.images.length;
    if (rest <= 0) {
      util.toast('最多上传 9 张图片');
      return;
    }
    wx.chooseMedia({
      count: rest,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: (res) => {
        const paths = res.tempFiles.map(f => f.tempFilePath);
        this.setData({ 'form.images': this.data.form.images.concat(paths) });
      }
    });
  },

  onDeleteImage(e) {
    const index = Number(e.currentTarget.dataset.index);
    const images = this.data.form.images.slice();
    images.splice(index, 1);
    this.setData({ 'form.images': images });
  },

  onPreviewImage(e) {
    const index = Number(e.currentTarget.dataset.index);
    wx.previewImage({
      current: this.data.form.images[index],
      urls: this.data.form.images
    });
  },

  /* ---------- 表单 ---------- */
  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['form.' + field]: e.detail.value });
  },

  onPicker(e) {
    const field = e.currentTarget.dataset.field;
    const list = field === 'category' ? CATEGORIES : CONDITIONS;
    const index = Number(e.detail.value);
    const hit = list[index];
    if (!hit) return;
    const patch = { ['form.' + field]: hit.value };
    patch[field === 'category' ? 'categoryText' : 'condText'] = hit.label;
    this.setData(patch);
  },

  onClear(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['form.' + field]: '' });
  },

  /* ---------- 上传图片 ---------- */
  uploadImages() {
    const local = this.data.form.images.filter(p => !/^https?:/.test(p));
    if (!local.length) return Promise.resolve(this.data.form.images);
    wx.showLoading({ title: '上传图片中', mask: true });
    const tasks = local.map(p => api.file.upload(p, C.FILE_BIZ.GOODS));
    return Promise.all(tasks).then((results) => {
      wx.hideLoading();
      const remote = results.map(r => (r && (r.url || (r.data && r.data.url))) || r);
      // 用已上传的 url 替换本地路径，保留原有远程 url
      const keep = this.data.form.images.filter(p => /^https?:/.test(p));
      const all = keep.concat(remote);
      this.setData({ 'form.images': all });
      return all;
    }).catch((err) => {
      wx.hideLoading();
      throw err;
    });
  },

  /* ---------- 提交 ---------- */
  onSubmit() {
    if (this.data.submitting) return;
    for (let s = 1; s <= 3; s++) {
      if (!this.validateStep(s)) {
        this.setData({ step: s });
        return;
      }
    }
    const f = this.data.form;
    if (!f.tradePlace) {
      util.toast('请填写交易地点');
      return;
    }
    if (!f.contact) {
      util.toast('请填写联系方式');
      return;
    }

    this.setData({ submitting: true });
    util.toast('提交中', 'loading', 2000);

    this.uploadImages().then((images) => {
      const payload = {
        images: images,
        title: f.title.trim(),
        description: f.description.trim(),
        price: Number(f.price),
        originPrice: f.originPrice ? Number(f.originPrice) : null,
        category: Number(f.category),
        cond: Number(f.cond),
        tradePlace: f.tradePlace.trim(),
        contact: f.contact.trim()
      };
      return this.goodsId
        ? api.goods.update(this.goodsId, payload)
        : api.goods.create(payload);
    }).then((res) => {
      this.setData({ submitting: false });
      wx.hideToast();
      wx.removeStorageSync('goods_publish_draft');
      util.toast(this.goodsId ? '修改成功' : '发布成功', 'success');
      const id = this.goodsId || (res && (res.id || (res.data && res.data.id)));
      setTimeout(() => {
        if (this.goodsId) {
          wx.navigateBack();
        } else {
          wx.redirectTo({ url: `/pages/goods/detail?id=${id}` });
        }
      }, 800);
    }).catch((err) => {
      this.setData({ submitting: false });
      wx.hideToast();
      if (err && err.code === 40302) {
        auth.requireVerified({ title: '发布商品' });
      }
    });
  }
});
