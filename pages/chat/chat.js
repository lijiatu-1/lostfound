import { conversationApi } from '../../utils/api.js'
import { formatTimeAgo } from '../../utils/formatTime.js'

Page({
  data: { conversation: null, messages: [], draft: '', loading: true, error: '', sending: false },
  onLoad(options) { this.id = options.id; this.load() },
  onShow() { if (this.id) this.load() },
  load() {
    this.setData({ loading: true, error: '' })
    conversationApi.getMessages(this.id).then(res => {
      this.setData({
        conversation: res.conversation,
        messages: (res.messages || []).map(m => ({
          ...m, mine: String(m.senderId) === String(getApp().globalData.userId),
          timeAgo: formatTimeAgo(m.createdAt)
        })),
        loading: false
      })
      conversationApi.markRead(this.id).catch(() => {})
    }).catch(err => this.setData({ loading: false, error: err.message || '加载失败', messages: [] }))
  },
  retry() { this.load() },
  input(e) { this.setData({ draft: e.detail.value }) },
  send() {
    const content = this.data.draft.trim()
    if (!content || this.data.sending || !this.data.conversation || this.data.conversation.status !== 'open') return
    this.setData({ sending: true })
    conversationApi.sendMessage(this.id, content).then(() => {
      this.setData({ draft: '', sending: false })
      this.load()
    }).catch(err => {
      this.setData({ sending: false })
      wx.showToast({ title: err.message || '发送失败', icon: 'none' })
    })
  }
})
