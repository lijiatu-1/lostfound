import { authApi } from '../../utils/api.js'
import { formatTimeAgo } from '../../utils/formatTime.js'

Page({
  data: {
    certList: [],
    isLoading: true
  },

  onLoad() {
    this.loadPendingList()
  },

  onShow() {
    this.loadPendingList()
  },

  loadPendingList() {
    this.setData({ isLoading: true })
    authApi.getPendingCertifications()
      .then(res => {
        // 后端返回 { certifications: [...] }，需要取 .certifications
        const list = ((res && res.certifications) || []).map(cert => {
          cert.timeAgo = formatTimeAgo(cert.createdAt)
          return cert
        })
        this.setData({ certList: list, isLoading: false })
      })
      .catch(err => {
        console.error('加载认证列表失败:', err)
        this.setData({ certList: this.getMockItems(), isLoading: false })
      })
  },

  getMockItems() {
    return [
      { id: 1, userId: 3, realName: '王同学', studentId: '2023001003', timeAgo: '1小时前', status: 'pending' }
    ]
  },

  handleReview(e) {
    const certId = e.currentTarget.dataset.id
    const action = e.currentTarget.dataset.action
    const actionText = action === 'accept' ? '通过' : '拒绝'

    wx.showModal({
      title: '确认' + actionText,
      content: '确定要' + actionText + '该认证申请吗？',
      success: (res) => {
        if (res.confirm) {
          authApi.reviewCertification(certId, { action: action })
            .then(res => {
              if (res.success) {
                wx.showToast({ title: actionText + '成功', icon: 'success' })
                this.loadPendingList()
              } else {
                wx.showToast({ title: res.message || '操作失败', icon: 'none' })
              }
            })
            .catch(err => {
              wx.showToast({ title: err.message || '操作失败', icon: 'none' })
            })
        }
      }
    })
  }
})
