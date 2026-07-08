import { applicationApi } from '../../utils/api.js'
import { formatTimeAgo, getStatusText } from '../../utils/formatTime.js'

Page({
  data: {
    claimList: []
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
        this.setData({ claimList: list })
      })
      .catch(err => {
        console.error('加载我的申请失败:', err)
        this.setData({ claimList: this.getMockItems() })
      })
  },

  getMockItems() {
    return [
      { id: 1, itemId: 1, type: 'claim', content: '这是我的耳机！上有贴纸。', status: 'pending', statusText: '待处理', timeAgo: '5小时前' },
      { id: 2, itemId: 4, type: 'help', content: '我在体育馆见过这个钱包。', status: 'accepted', statusText: '已通过', timeAgo: '昨天' },
      { id: 3, itemId: 5, type: 'claim', content: '充电宝是我丢的，白色小米。', status: 'rejected', statusText: '已拒绝', timeAgo: '3天前' }
    ]
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
