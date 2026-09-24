import { conversationApi } from '../../utils/api.js'
import { formatTimeAgo } from '../../utils/formatTime.js'

Page({
  data: { conversations: [], loading: true, error: '' },
  onLoad() { this.load() },
  onShow() { this.load() },
  load() {
    this.setData({ loading: true, error: '' })
    conversationApi.getConversations().then(res => {
      const list = (res && res.conversations) || []
      this.setData({
        conversations: list.map(c => ({ ...c, timeAgo: formatTimeAgo(c.createdAt) })),
        loading: false
      })
    }).catch(err => this.setData({ conversations: [], loading: false, error: err.message || '加载失败' }))
  },
  retry() { this.load() },
  open(e) { wx.navigateTo({ url: '/pages/chat/chat?id=' + e.currentTarget.dataset.id }) }
})
