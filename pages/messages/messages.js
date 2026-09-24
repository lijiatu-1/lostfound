import { messageApi } from '../../utils/api.js'
import { formatTimeAgo } from '../../utils/formatTime.js'

Page({
  data: {
    messages: [],
    isLoading: true,
    error: ''
  },

  onLoad() {
    this.loadMessages()
  },

  loadMessages() {
    messageApi.getMessages()
      .then(res => {
        const list = (res && res.messages) || res || []
        const messages = list.map(msg => {
          msg.timeAgo = formatTimeAgo(msg.createdAt)
          return msg
        })
        this.setData({
          messages: messages,
          isLoading: false,
          error: ''
        })
        wx.stopPullDownRefresh()
      })
      .catch(err => {
        console.error('加载消息失败:', err)
        this.setData({
          messages: [],
          isLoading: false,
          error: err.message || '消息加载失败，请重试'
        })
        wx.stopPullDownRefresh()
      })
  },

  retry() { this.loadMessages() },
  goChats() { wx.navigateTo({ url: '/pages/conversations/conversations' }) },
  markAsRead(messageId) {
    // 先保存旧状态以便回滚
    const oldMessages = this.data.messages
    // 立即更新本地状态（乐观更新）
    this.setData({
      messages: this.data.messages.map(msg =>
        msg.id == messageId ? { ...msg, isRead: true } : msg
      )
    })
    messageApi.markRead(messageId)
      .catch(err => {
        console.error('标记已读失败:', err)
        // 请求失败时回滚本地状态，避免下次刷新时状态闪烁
        this.setData({ messages: oldMessages })
      })
  },

  goToDetail(e) {
    const itemId = e.currentTarget.dataset.itemId
    const messageId = e.currentTarget.dataset.id
    if (itemId) {
      // 有关联物品，跳转详情页
      this.markAsRead(messageId)
      wx.navigateTo({
        url: `/pages/detail/detail?id=${itemId}`
      })
    } else {
      // 系统通知，只标记已读
      this.markAsRead(messageId)
    }
  },

  markAllRead() {
    messageApi.markAllRead()
      .then(res => {
        if (res.success) {
          this.setData({
            messages: this.data.messages.map(msg => ({ ...msg, isRead: true }))
          })
          wx.showToast({
            title: '已全部标为已读',
            icon: 'success'
          })
        }
      })
      .catch(err => {
        wx.showToast({
          title: '操作失败',
          icon: 'none'
        })
      })
  },

  goBack() {
    wx.navigateBack()
  },

  onPullDownRefresh() {
    this.loadMessages()  // loadMessages 内部会在完成时调用 stopPullDownRefresh
  }
})
