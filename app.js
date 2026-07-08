import { authApi } from './utils/api.js'

App({
  globalData: {
    userId: '',
    isAuthenticated: false,
    isAdmin: false,
    userInfo: {},
    editItemId: null,
    loginPromise: null  // 让其他页面可以等待登录完成
  },

  onLaunch() {
    this.globalData.loginPromise = this.login()
  },

  login() {
    return new Promise((resolve, reject) => {
      wx.login({
        success: (loginRes) => {
          if (loginRes.code) {
            authApi.login(loginRes.code)
              .then(apiRes => {
                if (apiRes && apiRes.token) {
                  wx.setStorageSync('token', apiRes.token)
                  const user = apiRes.user || {}
                  this.globalData.userId = user.id ? Number(user.id) : ''
                  this.globalData.isAuthenticated = user.status === 'authorized'
                  this.globalData.isAdmin = user.role === 'admin'
                  resolve(apiRes)
                } else {
                  reject(new Error('登录返回数据异常'))
                }
              })
              .catch(err => {
                console.error('登录失败:', err)
                reject(err)
              })
          } else {
            console.error('wx.login 失败:', loginRes.errMsg)
            reject(new Error(loginRes.errMsg))
          }
        },
        fail: (err) => {
          console.error('wx.login 失败:', err)
          reject(err)
        }
      })
    })
  }
})
