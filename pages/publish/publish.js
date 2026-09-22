import { itemApi, uploadImage, aiApi, resolveAssetUrl } from '../../utils/api.js'

const DEFAULT_CATEGORIES = ['证件卡片', '电子产品', '服饰配件', '学习用品', '生活用品', '其他物品']

Page({
  data: {
    type: 'lost',
    imageAssets: [],
    imageUploading: false,
    aiLoading: false,
    editMode: false,
    editId: null,
    categories: DEFAULT_CATEGORIES,
    selectedCategory: '其他物品',
    tagsInput: '',
    formData: {
      title: '',
      location: '',
      description: ''
    }
  },

  onLoad(options) {
    this.loadCategories()
    this.activateEdit(options.id)
  },

  onShow() {
    // A tab page is not reloaded after switchTab.  Detail stores the target
    // briefly in globalData so editing still works without leaking an ID into
    // the next normal publish session.
    this.activateEdit(getApp().globalData.editItemId)
  },

  activateEdit(id) {
    if (!id || String(id) === String(this.data.editId)) return
    getApp().globalData.editItemId = null
    this.setData({ editMode: true, editId: id })
    wx.setNavigationBarTitle({ title: '编辑物品' })
    this.loadItemForEdit(id)
  },

  resetForm() {
    this.setData({
      type: 'lost',
      imageAssets: [],
      selectedCategory: '其他物品',
      tagsInput: '',
      formData: { title: '', location: '', description: '' },
      editMode: false,
      editId: null
    })
    wx.setNavigationBarTitle({ title: '失物发布' })
  },

  loadItemForEdit(id) {
    wx.showLoading({ title: '加载中...' })
    itemApi.getItem(id)
      .then((res) => {
        if (!res) throw new Error('未找到该物品')
        this.setData({
          type: res.type || 'lost',
          imageAssets: this.normalizeImageAssets(res),
          selectedCategory: res.category || '其他物品',
          tagsInput: this.normalizeTags(res.tags).join('、'),
          formData: {
            title: res.title || '',
            location: res.locationName || '',
            description: res.description || ''
          }
        })
      })
      .catch((err) => {
        this.resetForm()
        wx.showToast({ title: err.message || '加载失败，请重试', icon: 'none' })
      })
      .finally(() => wx.hideLoading())
  },

  normalizeImageAssets(item) {
    let source = item.imageAssets || item.imageAssetIds || item.images || []
    if (typeof source === 'string') {
      try { source = JSON.parse(source) } catch (e) { source = [] }
    }
    if (!Array.isArray(source)) return []
    return source.map((asset) => {
      if (asset && typeof asset === 'object') {
        const assetId = asset.assetId || asset.id
        return assetId ? { assetId: String(assetId), url: resolveAssetUrl(asset.url || String(assetId)) } : null
      }
      return asset ? { assetId: String(asset), url: resolveAssetUrl(String(asset)) } : null
    }).filter(Boolean)
  },

  normalizeTags(tags) {
    if (!tags) return []
    if (Array.isArray(tags)) return tags
    try {
      const result = JSON.parse(tags)
      return Array.isArray(result) ? result : []
    } catch (e) {
      return String(tags).split(/[，,、\s]+/).filter(Boolean)
    }
  },

  loadCategories() {
    itemApi.getCategories()
      .then((res) => {
        const categories = Array.isArray(res) ? res : (res && res.categories) || []
        if (categories.length) this.setData({ categories })
      })
      .catch((err) => {
        // Categories are local form labels, not a substitute for missing item
        // data.  The request failure remains visible to the user.
        wx.showToast({ title: err.message || '分类加载失败', icon: 'none' })
      })
  },

  selectCategory(e) {
    this.setData({ selectedCategory: e.currentTarget.dataset.category })
  },

  switchType() {
    if (this.data.editMode) return
    this.setData({ type: this.data.type === 'lost' ? 'found' : 'lost' })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ ['formData.' + field]: e.detail.value })
  },

  onTagsInput(e) {
    this.setData({ tagsInput: e.detail.value })
  },

  chooseImage() {
    const remaining = 9 - this.data.imageAssets.length
    if (remaining <= 0) {
      wx.showToast({ title: '最多上传9张图片', icon: 'none' })
      return
    }

    wx.showActionSheet({
      itemList: ['拍照', '从相册选择'],
      success: (selection) => {
        const sourceType = selection.tapIndex === 0 ? ['camera'] : ['album']
        wx.chooseMedia({
          count: remaining,
          mediaType: ['image'],
          sizeType: ['compressed'],
          sourceType,
          success: (media) => {
            const tempPaths = media.tempFiles.map((file) => file.tempFilePath)
            this.setData({ imageUploading: true })
            wx.showLoading({ title: '上传图片中...' })
            Promise.allSettled(tempPaths.map((path) => uploadImage(path, 'item')))
              .then((results) => {
                const uploaded = results.filter((result) => result.status === 'fulfilled').map((result) => result.value)
                const failed = results.length - uploaded.length
                this.setData({ imageAssets: this.data.imageAssets.concat(uploaded) })
                if (failed) wx.showToast({ title: uploaded.length ? '部分图片上传失败' : '图片上传失败', icon: 'none' })
              })
              .finally(() => {
                this.setData({ imageUploading: false })
                wx.hideLoading()
              })
          }
        })
      }
    })
  },

  deleteImage(e) {
    const imageAssets = this.data.imageAssets.slice()
    imageAssets.splice(Number(e.currentTarget.dataset.index), 1)
    this.setData({ imageAssets })
  },

  aiRecognize() {
    if (!this.data.imageAssets.length || this.data.aiLoading) return
    const { title, description } = this.data.formData
    if (title.trim() || description.trim()) {
      wx.showModal({
        title: '确认覆盖',
        content: 'AI识别会覆盖已填写的标题和描述，确定继续吗？',
        success: (modal) => { if (modal.confirm) this.doAiRecognize() }
      })
      return
    }
    this.doAiRecognize()
  },

  doAiRecognize() {
    this.setData({ aiLoading: true })
    wx.showLoading({ title: 'AI识别中...' })
    aiApi.recognize(this.data.imageAssets[0].assetId)
      .then((res) => {
        if (!res || res.success === false) throw new Error((res && res.message) || '识别失败')
        const update = {}
        if (res.title) update['formData.title'] = res.title
        if (res.description) update['formData.description'] = res.description
        if (res.category) update.selectedCategory = res.category
        this.setData(update)
        wx.showToast({ title: '识别成功，请检查并补充信息', icon: 'none' })
      })
      .catch((err) => {
        const message = this.isRateLimitError(err.message) ? '今日识别次数已用完，请明天再试' : (err.message || '识别失败，请稍后重试')
        wx.showToast({ title: message, icon: 'none' })
      })
      .finally(() => {
        this.setData({ aiLoading: false })
        wx.hideLoading()
      })
  },

  getTags(title, description) {
    const manualTags = this.data.tagsInput.split(/[，,、\s]+/).map((tag) => tag.trim()).filter(Boolean)
    if (manualTags.length) return [...new Set(manualTags)].slice(0, 10)
    const keywords = ['耳机', '手机', '钱包', '钥匙', '学生证', '校园卡', '电脑', '书籍', '雨伞', '充电宝', '水杯', '书包', '眼镜', '手表', '充电器', '数据线']
    const tags = keywords.filter((keyword) => title.includes(keyword) || description.includes(keyword))
    return tags.length ? tags : ['其他']
  },

  submitForm() {
    const { title, location, description } = this.data.formData
    if (this.data.imageUploading) {
      wx.showToast({ title: '图片正在上传中...', icon: 'none' })
      return
    }
    if (!title.trim() || !location.trim() || !description.trim()) {
      wx.showToast({ title: '请完整填写物品名称、地点和描述', icon: 'none' })
      return
    }

    const wasEditing = this.data.editMode
    const data = {
      title: title.trim(),
      description: description.trim(),
      locationName: location.trim(),
      category: this.data.selectedCategory,
      tags: JSON.stringify(this.getTags(title, description)),
      imageAssetIds: this.data.imageAssets.map((asset) => asset.assetId)
    }
    if (!wasEditing) data.type = this.data.type

    wx.showLoading({ title: wasEditing ? '保存中...' : '发布中...' })
    const request = wasEditing ? itemApi.update(this.data.editId, data) : itemApi.publish(data)
    request
      .then((res) => {
        if (res && res.success === false) throw new Error(res.message || '操作失败')
        wx.showToast({ title: wasEditing ? '保存成功' : '发布成功', icon: 'success' })
        this.resetForm()
        setTimeout(() => {
          if (wasEditing) wx.navigateBack()
          else wx.switchTab({ url: '/pages/index/index' })
        }, 700)
      })
      .catch((err) => wx.showToast({ title: err.message || '操作失败，请重试', icon: 'none' }))
      .finally(() => wx.hideLoading())
  },

  isRateLimitError(message) {
    return /429|次数|限额|too many/i.test(message || '')
  }
})
