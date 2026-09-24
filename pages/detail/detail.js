import { itemApi, applicationApi, commentApi, resolveAssetUrl } from '../../utils/api.js'
import { formatTimeAgo } from '../../utils/formatTime.js'

Page({
  data: {
    item: null,
    comments: [],
    commentError: '',
    isLoading: true,
    error: '',
    isOwner: false,
    isAuthenticated: false,
    canRenew: false,
    commentContent: ''
  },

  onLoad(options) {
    this.itemId = options.id
    this.loadItem()
  },

  onShow() {
    if (this.itemId) this.loadItem()
  },

  loadItem() {
    this.setData({ isLoading: true, error: '' })
    itemApi.getItem(this.itemId).then(item => {
      let images = []
      let tags = []
      try { images = JSON.parse(item.images || '[]') } catch (e) {}
      try { tags = JSON.parse(item.tags || '[]') } catch (e) {}
      item.imageList = Array.isArray(images) ? images.map(resolveAssetUrl).filter(Boolean) : []
      item.tagList = Array.isArray(tags) ? tags : []
      item.timeAgo = formatTimeAgo(item.createdAt)
      const app = getApp()
      const expires = item.expireAt ? new Date(item.expireAt).getTime() : 0
      this.setData({
        item,
        isOwner: String(app.globalData.userId) === String(item.publisherId),
        isAuthenticated: !!app.globalData.isAuthenticated,
        canRenew: item.status === 'expired' || (item.status === 'active' && expires <= Date.now() + 3 * 86400000),
        isLoading: false
      })
      if (item.type === 'lost') this.loadComments(item.id)
    }).catch(err => {
      this.setData({ isLoading: false, item: null, comments: [], commentError: '', error: err.message || '加载失败，请重试' })
    })
  },

  retry() { this.loadItem() },

  loadComments(itemId) {
    this.setData({ commentError: '' })
    commentApi.getByItem(itemId).then(res => {
      this.setData({ comments: (Array.isArray(res) ? res : []).map(c => ({
        ...c, timeAgo: formatTimeAgo(c.createdAt)
      })) })
    }).catch(err => this.setData({ comments: [], commentError: err.message || '评论加载失败' }))
  },
  retryComments() { if (this.data.item) this.loadComments(this.data.item.id) },

  goEdit() {
    getApp().globalData.editItemId = this.data.item.id
    wx.switchTab({ url: '/pages/publish/publish' })
  },
  goToAuth() { wx.navigateTo({ url: '/pages/auth/auth' }) },
  goApplications() {
    wx.navigateTo({ url: '/pages/applications/applications?itemId=' + this.data.item.id })
  },

  apply() {
    if (!this.data.isAuthenticated) { this.goToAuth(); return }
    const item = this.data.item
    if (!item || item.status !== 'active') return
    wx.showModal({
      title: item.type === 'found' ? '申请认领' : '提供线索',
      editable: true,
      placeholderText: item.type === 'found' ? '请描述能证明物品属于你的信息' : '请描述你掌握的线索',
      success: res => {
        if (!res.confirm) return
        const content = (res.content || '').trim()
        if (!content) { wx.showToast({ title: '请填写申请内容', icon: 'none' }); return }
        applicationApi.apply({ itemId: item.id, content })
          .then(() => wx.showToast({ title: '申请已提交', icon: 'success' }))
          .catch(err => wx.showToast({ title: err.message || '提交失败', icon: 'none' }))
      }
    })
  },

  onCommentInput(e) { this.setData({ commentContent: e.detail.value }) },
  submitComment() {
    const content = this.data.commentContent.trim()
    if (!content) return
    commentApi.add({ itemId: this.data.item.id, content }).then(() => {
      this.setData({ commentContent: '' })
      this.loadComments(this.data.item.id)
    }).catch(err => wx.showToast({ title: err.message || '发表失败', icon: 'none' }))
  },

  markResolved() {
    wx.showModal({
      title: '确认结案', content: '结案后私信历史仍可查看，但不能再发送。',
      success: res => {
        if (res.confirm) itemApi.resolve(this.data.item.id).then(() => this.loadItem())
          .catch(err => wx.showToast({ title: err.message || '结案失败', icon: 'none' }))
      }
    })
  },
  reopenItem() {
    wx.showModal({
      title: '重新开放', content: '当前私信将关闭，物品重新出现在发现列表。',
      success: res => {
        if (res.confirm) itemApi.reopen(this.data.item.id).then(() => this.loadItem())
          .catch(err => wx.showToast({ title: err.message || '重新开放失败', icon: 'none' }))
      }
    })
  },
  renewItem() {
    itemApi.renew(this.data.item.id).then(() => this.loadItem())
      .catch(err => wx.showToast({ title: err.message || '延期失败', icon: 'none' }))
  },
  deleteItem() {
    wx.showModal({
      title: '确认删除', content: '此物品将不再公开展示。',
      success: res => {
        if (res.confirm) itemApi.delete(this.data.item.id).then(() => wx.navigateBack())
          .catch(err => wx.showToast({ title: err.message || '删除失败', icon: 'none' }))
      }
    })
  },
  previewImage(e) {
    wx.previewImage({ current: e.currentTarget.dataset.src, urls: this.data.item.imageList })
  }
})
