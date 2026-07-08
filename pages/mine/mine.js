import { authApi, itemApi, messageApi } from '../../utils/api.js'
import { formatTimeAgo } from '../../utils/formatTime.js'

Page({
  data: {
    user: {},
    isAuthenticated: false,
    isAdmin: false,
    unreadCount: 0,
    myItems: [],
    resolvedCount: 0,
    isLoading: true
  },

  onLoad() {
    this.loadUserInfo()
    this.loadUnreadCount()
    this.loadMyItems()
  },

  onShow() {
    this.loadUserInfo()  // 从认证页返回后需要刷新认证状态
    this.loadUnreadCount()
    this.loadMyItems()
  },

  loadUserInfo() {
    authApi.getUser()
      .then(res => {
        const user = res || {}
        this.setData({
          user: user,
          isAuthenticated: user.status === 'authorized',
          isAdmin: user.role === 'admin'
        })

        const app = getApp()
        app.globalData.userId = user.id || ''
        app.globalData.isAuthenticated = user.status === 'authorized'
        app.globalData.isAdmin = user.role === 'admin'
      })
      .catch(err => {
        console.error('加载用户信息失败:', err)
        this.setData({
          user: this.getMockUser()
        })
      })
      .finally(() => {
        this.setData({ isLoading: false })
      })
  },

  getMockUser() {
    return {
      id: '1',
      nickname: '张三',
      avatarUrl: 'https://neeko-copilot.bytedance.net/api/text2image?prompt=young%20asian%20male%20student%20avatar%20portrait%20smiling&width=512&height=512',
      status: 'authorized',
      realName: '张三',
      studentId: '2023001001',
      createdAt: '2024-01-01'
    }
  },

  loadUnreadCount() {
    messageApi.getUnreadCount()
      .then(res => {
        this.setData({
          unreadCount: (res && res.count) || 0
        })
      })
      .catch(err => {
        console.error('获取未读消息数失败:', err)
      })
  },

  loadMyItems() {
    itemApi.getMyItems()
      .then(res => {
        const list = (res && res.items) || res || []
        const items = list.map(item => {
          if (item.images) {
            try {
              const imgList = JSON.parse(item.images)
              item.image = imgList[0] || ''
            } catch (e) {
              item.image = ''
            }
          }
          item.timeAgo = formatTimeAgo(item.createdAt)
          return item
        })
        this.setData({
          myItems: items,
          resolvedCount: items.filter(i => i.status === 'resolved').length
        })
      })
      .catch(err => {
        console.error('加载我的发布失败:', err)
        this.setData({
          myItems: this.getMockMyItems()
        })
      })
  },

  getMockMyItems() {
    return [
      {
        id: '1',
        title: '苹果AirPods Pro蓝牙耳机',
        type: 'lost',
        image: 'https://neeko-copilot.bytedance.net/api/text2image?prompt=Apple%20AirPods%20Pro%20wireless%20earbuds%20in%20charging%20case%20on%20white%20background&width=512&height=512',
        timeAgo: '2小时前',
        status: 'active'
      },
      {
        id: '2',
        title: '学生证',
        type: 'lost',
        image: 'https://neeko-copilot.bytedance.net/api/text2image?prompt=student%20ID%20card%20with%20photo%20on%20white%20background&width=512&height=512',
        timeAgo: '3天前',
        status: 'resolved'
      }
    ]
  },

  goToMyPublish() {
    wx.navigateTo({
      url: '/pages/my-publish/my-publish'
    })
  },

  goToAdminCert() {
    wx.navigateTo({
      url: '/pages/admin-cert/admin-cert'
    })
  },

  goToAuth() {
    // 使用 globalData 中的认证状态而非可能来自 mock 的 user.status
    const app = getApp()
    const isAuth = app.globalData.isAuthenticated
    if (isAuth) {
      wx.showToast({
        title: '您已完成认证',
        icon: 'none'
      })
      return
    }
    wx.navigateTo({
      url: '/pages/auth/auth'
    })
  },

  goToMessages() {
    wx.navigateTo({
      url: '/pages/messages/messages'
    })
  },

  goToItemDetail(e) {
    const id = e.currentTarget.dataset.id
    // 后端返回的 id 是 Number，dataset 取到的是 String，需统一类型比较
    const item = this.data.myItems.find(i => String(i.id) === String(id))
    if (item) {
      wx.navigateTo({
        url: `/pages/detail/detail?id=${id}&type=${item.type}`
      })
    }
  },

  goToPublish() {
    wx.switchTab({
      url: '/pages/publish/publish'
    })
  },

  editProfile() {
    wx.showModal({
      title: '修改资料',
      editable: true,
      placeholderText: '请输入新昵称',
      success: (res) => {
        if (res.confirm && res.content) {
          authApi.updateProfile({ nickname: res.content.trim() })
            .then(res => {
              if (res.success) {
                wx.showToast({
                  title: '修改成功',
                  icon: 'success'
                })
                this.loadUserInfo()
              } else {
                wx.showToast({
                  title: res.message || '修改失败',
                  icon: 'none'
                })
              }
            })
            .catch(err => {
              wx.showToast({
                title: err.message || '修改失败',
                icon: 'none'
              })
            })
        }
      }
    })
  },

  clearCache() {
    wx.showModal({
      title: '清除缓存',
      content: '确定要清除本地缓存吗？清除后将重新登录。',
      success: (res) => {
        if (res.confirm) {
          wx.clearStorageSync()
          wx.showToast({
            title: '清除成功',
            icon: 'success'
          })
          // 清除后重新登录，否则后续所有请求都会 401
          const app = getApp()
          if (app && app.login) {
            app.globalData.loginPromise = app.login()
          }
        }
      }
    })
  }
})