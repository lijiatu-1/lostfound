import { applicationApi, conversationApi } from '../../utils/api.js'
import { formatTimeAgo, getStatusText } from '../../utils/formatTime.js'

Page({
  data: {
    claimList: [],
    error: ''
  },

  onLoad() {
    this.loadClaimList()
  },

  onShow() {
    this.loadClaimList()
  },

  loadClaimList() {
    applicationApi.getMyApplications()
      .then(res => {
        const list = (res || []).map(app => {
          app.timeAgo = formatTimeAgo(app.createdAt)
          app.statusText = getStatusText(app.status)
          return app
        })
        this.setData({ claimList: list, error: '' })
        conversationApi.getConversations().then(result => {
          const byApplication = {}
          ;((result && result.conversations) || []).forEach(c => { byApplication[c.applicationId] = c.id })
          this.setData({ claimList: list.map(a => ({ ...a, conversationId: byApplication[a.id] || null })) })
        }).catch(() => {})
      })
      .catch(err => {
        console.error('加载我的申请失败:', err)
        this.setData({ claimList: [], error: err.message || '加载失败，请重试' })
      })
  },

  retry() { this.loadClaimList() },
  goToChat(e) {
    wx.navigateTo({ url: '/pages/chat/chat?id=' + e.currentTarget.dataset.id })
  },
  goToDetail(e) {
    const itemId = e.currentTarget.dataset.itemId
    if (itemId) {
      wx.navigateTo({
        url: '/pages/detail/detail?id=' + itemId
      })
    }
  }
})
