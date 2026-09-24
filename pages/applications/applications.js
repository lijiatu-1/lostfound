import { applicationApi } from '../../utils/api.js'
import { formatTimeAgo } from '../../utils/formatTime.js'

Page({
  data: { applications: [], loading: true, error: '' },
  onLoad(options) { this.itemId = options.itemId; this.load() },
  onShow() { if (this.itemId) this.load() },
  load() {
    this.setData({ loading: true, error: '' })
    applicationApi.getByItem(this.itemId).then(list => {
      this.setData({
        applications: (Array.isArray(list) ? list : []).map(a => ({ ...a, timeAgo: formatTimeAgo(a.createdAt) })),
        loading: false
      })
    }).catch(err => this.setData({ loading: false, applications: [], error: err.message || '加载失败' }))
  },
  retry() { this.load() },
  handle(e) {
    const { id, action } = e.currentTarget.dataset
    wx.showModal({
      title: action === 'accept' ? '批准申请' : '拒绝申请',
      content: action === 'accept' ? '批准后物品进入处理中，并开启私信。其他待处理申请将关闭。' : '确定拒绝此申请吗？',
      success: result => {
        if (!result.confirm) return
        applicationApi.handle(id, action).then(res => {
          this.load()
          if (res.conversationId) wx.navigateTo({ url: '/pages/chat/chat?id=' + res.conversationId })
          else wx.showToast({ title: '已处理', icon: 'success' })
        }).catch(err => wx.showToast({ title: err.message || '处理失败', icon: 'none' }))
      }
    })
  },
  goChats() { wx.navigateTo({ url: '/pages/conversations/conversations' }) }
})
