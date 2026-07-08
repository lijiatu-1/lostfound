import { itemApi, commentApi } from '../../utils/api.js'
import { formatTimeAgo } from '../../utils/formatTime.js'

Page({
  data: {
    item: {},
    comments: [],
    isLoading: true,
    isOwner: false,
    isAuthenticated: false,
    canRenew: false,
    commentContent: ''
  },

  onLoad(options) {
    const id = options.id
    if (id) {
      this.loadItem(id)
    }
  },

  loadItem(id) {
    itemApi.getItem(id)
      .then(res => {
        const item = res || {}
        if (item.images) {
          try {
            item.imageList = JSON.parse(item.images)
          } catch (e) {
            item.imageList = []
          }
        }
        if (item.tags) {
          try {
            item.tagList = JSON.parse(item.tags)
          } catch (e) {
            item.tagList = []
          }
        }
        item.timeAgo = formatTimeAgo(item.createdAt)

        // 计算是否可以延期（过期、已解决、或3天内过期）
        const now = new Date()
        const expireAt = item.expireAt ? new Date(item.expireAt) : null
        const daysLeft = expireAt ? (expireAt.getTime() - now.getTime()) / (24 * 60 * 60 * 1000) : 999
        const canRenew = item.status === 'expired' || item.status === 'resolved' || (item.status === 'active' && daysLeft <= 3)

        const app = getApp()
        const isOwner = app.globalData.userId === item.publisherId
        this.setData({
          item: item,
          isOwner: isOwner,
          isAuthenticated: app.globalData.isAuthenticated,
          canRenew: canRenew,
          isLoading: false
        })
        // 无论是否发布者，都加载评论
        this.loadComments(item.id)
      })
      .catch(err => {
        console.error('加载详情失败:', err)
        this.setData({
          isLoading: false,
          item: this.getMockItem()
        })
        wx.showToast({
          title: err.message || '加载失败，显示示例数据',
          icon: 'none'
        })
      })
  },

  loadComments(itemId) {
    commentApi.getByItem(itemId)
      .then(res => {
        const comments = (res || []).map(c => {
          c.timeAgo = formatTimeAgo(c.createdAt)
          return c
        })
        this.setData({ comments: comments })
      })
      .catch(err => {
        console.error('加载评论失败:', err)
      })
  },

  getMockItem() {
    return {
      id: '1',
      title: '苹果AirPods Pro蓝牙耳机',
      type: 'lost',
      locationName: '图书馆三楼自习室',
      imageList: [],
      tagList: ['耳机', '图书馆', '白色'],
      description: '白色充电盒，左耳耳机丢失，在图书馆自习时发现不见。',
      timeAgo: '2小时前',
      publisherId: 1,
      status: 'active'
    }
  },

  // 跳转到编辑页（publish 是 tabBar 页，不能用 navigateTo）
  goEdit() {
    const app = getApp()
    app.globalData.editItemId = this.data.item.id
    wx.switchTab({
      url: '/pages/publish/publish'
    })
  },

  // 跳转到认证页
  goToAuth() {
    wx.navigateTo({
      url: '/pages/auth/auth'
    })
  },

  // 复制手机号到剪贴板（需先通过接口获取）
  copyPhone() {
    if (!this.data.isAuthenticated) {
      wx.showModal({
        title: '需要认证',
        content: '请先完成校园卡认证后再联系失主',
        confirmText: '去认证',
        success: (res) => {
          if (res.confirm) {
            wx.navigateTo({ url: '/pages/auth/auth' })
          }
        }
      })
      return
    }
    wx.showLoading({ title: '获取联系方式...' })
    itemApi.getContact(this.data.item.id)
      .then(res => {
        wx.hideLoading()
        const phone = res && res.phone
        if (!phone) {
          wx.showToast({ title: '发布者未留电话', icon: 'none' })
          return
        }
        wx.setClipboardData({
          data: phone,
          success: () => {
            wx.showToast({ title: '已复制手机号', icon: 'none' })
          }
        })
      })
      .catch(err => {
        wx.hideLoading()
        wx.showToast({ title: err.message || '获取联系方式失败', icon: 'none' })
      })
  },

  // 评论输入
  onCommentInput(e) {
    this.setData({ commentContent: e.detail.value })
  },

  // 发表评论
  submitComment() {
    if (!this.data.commentContent.trim()) {
      wx.showToast({ title: '请输入评论内容', icon: 'none' })
      return
    }

    wx.showLoading({ title: '发表中...' })

    commentApi.add({
      itemId: this.data.item.id,
      content: this.data.commentContent.trim()
    })
      .then(res => {
        wx.hideLoading()
        if (res.success) {
          wx.showToast({ title: '评论成功', icon: 'success' })
          this.setData({ commentContent: '' })
          this.loadComments(this.data.item.id)
        } else {
          wx.showToast({ title: res.message || '评论失败', icon: 'none' })
        }
      })
      .catch(err => {
        wx.hideLoading()
        wx.showToast({ title: err.message || '评论失败', icon: 'none' })
      })
  },

  markResolved() {
    wx.showModal({
      title: '确认已找回',
      content: '确定物品已找回/归还吗？',
      success: (res) => {
        if (res.confirm) {
          itemApi.resolve(this.data.item.id)
            .then(res => {
              if (res.success) {
                wx.showToast({
                  title: '已标记为已解决',
                  icon: 'success'
                })
                this.setData({
                  'item.status': 'resolved'
                })
              } else {
                wx.showToast({
                  title: res.message || '操作失败',
                  icon: 'none'
                })
              }
            })
            .catch(err => {
              wx.showToast({
                title: err.message || '操作失败',
                icon: 'none'
              })
            })
        }
      }
    })
  },

  previewImage(e) {
    const current = e.currentTarget.dataset.src
    wx.previewImage({
      current: current,
      urls: this.data.item.imageList || []
    })
  },

  deleteItem() {
    wx.showModal({
      title: '确认删除',
      content: '确定要删除此物品吗？',
      success: (res) => {
        if (res.confirm) {
          itemApi.delete(this.data.item.id)
            .then(res => {
              if (res.success) {
                wx.showToast({ title: '删除成功', icon: 'success' })
                setTimeout(() => wx.navigateBack(), 800)
              } else {
                wx.showToast({ title: res.message || '删除失败', icon: 'none' })
              }
            })
            .catch(err => {
              wx.showToast({ title: err.message || '删除失败', icon: 'none' })
            })
        }
      }
    })
  },

  renewItem() {
    wx.showModal({
      title: '确认延期',
      content: '确定要延期7天吗？物品有效期将重新计算。',
      success: (res) => {
        if (res.confirm) {
          itemApi.renew(this.data.item.id)
            .then(res => {
              if (res.success) {
                wx.showToast({ title: '延期成功', icon: 'success' })
                // 直接用后端返回的数据更新界面，不重新请求
                const item = res.item
                if (item) {
                  if (item.images) {
                    try { item.imageList = JSON.parse(item.images) } catch (e) { item.imageList = [] }
                  }
                  if (item.tags) {
                    try { item.tagList = JSON.parse(item.tags) } catch (e) { item.tagList = [] }
                  }
                  item.timeAgo = formatTimeAgo(item.createdAt)
                  this.setData({
                    item: item,
                    canRenew: false
                  })
                }
              } else {
                wx.showToast({ title: res.message || '延期失败', icon: 'none' })
              }
            })
            .catch(err => {
              wx.showToast({ title: err.message || '延期失败', icon: 'none' })
            })
        }
      }
    })
  }
})
