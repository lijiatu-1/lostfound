import { messageApi } from '../../utils/api.js'
import { formatTimeAgo } from '../../utils/formatTime.js'

Page({
  data: {
    messages: [],
    isLoading: true
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
          isLoading: false
        })
        wx.stopPullDownRefresh()
      })
      .catch(err => {
        console.error('加载消息失败:', err)
        this.setData({
          messages: this.getMockMessages(),
          isLoading: false
        })
        wx.stopPullDownRefresh()
      })
  },

  getMockMessages() {
    return [
      {
        id: '1',
        type: 'claim_apply',
        title: '有人申请认领你的物品',
        content: '李四申请认领你发布的"苹果AirPods Pro蓝牙耳机"，请查看详情并处理。',
        relatedItemId: '1',
        isRead: false,
        createdAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
        timeAgo: '30分钟前'
      },
      {
        id: '2',
        type: 'help_offer',
        title: '有人提供帮助线索',
        content: '王五提供了关于你丢失的"华为Mate40手机"的线索，请查看详情。',
        relatedItemId: '2',
        isRead: false,
        createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
        timeAgo: '2小时前'
      },
      {
        id: '3',
        type: 'system_notice',
        title: '认证通过通知',
        content: '恭喜！你的校园卡认证已通过，现在可以发布失物信息了。',
        isRead: true,
        createdAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
        timeAgo: '昨天'
      }
    ]
  },

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