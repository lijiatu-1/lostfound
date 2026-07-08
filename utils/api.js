const BASE_URL = 'http://localhost:8080/api'

function request(url, method = 'GET', data = {}) {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token')

    wx.request({
      url: BASE_URL + url,
      method: method,
      data: data,
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? 'Bearer ' + token : ''
      },
      success: (res) => {
        if (res.statusCode === 200) {
          resolve(res.data)
        } else if (res.statusCode === 401) {
          // Token 过期或无效：清除旧 token，重新登录
          wx.removeStorageSync('token')
          const app = getApp()
          if (app && app.login) {
            app.login()
          }
          reject({ message: '登录已过期，请重试' })
        } else {
          reject(res.data || { message: '请求失败' })
        }
      },
      fail: (err) => {
        reject({ message: '网络请求失败，请检查后端服务是否启动' })
      }
    })
  })
}

export const authApi = {
  login: (code) => post('/auth/login', { code }),
  getUser: () => get('/auth/user'),
  certification: (data) => post('/auth/certification', data),
  updateProfile: (data) => post('/auth/profile', data),
  getPendingCertifications: () => get('/auth/certifications/pending'),
  reviewCertification: (id, data) => post('/auth/certification/' + id + '/review', data)
}

function buildQuery(params) {
  if (!params) return ''
  const parts = []
  for (const key in params) {
    if (params[key] !== undefined && params[key] !== null && params[key] !== '') {
      parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(params[key]))
    }
  }
  return parts.length > 0 ? '?' + parts.join('&') : ''
}

/**
 * GET 请求不传 body，参数全部在 query string 中
 */
function get(url, params) {
  return request(url + buildQuery(params), 'GET')
}

function post(url, data) {
  return request(url, 'POST', data)
}

function put(url, data) {
  return request(url, 'PUT', data)
}

function del(url) {
  return request(url, 'DELETE')
}

export const itemApi = {
  getItems: (params) => get('/items', params),
  getItem: (id) => get('/items/' + id),
  getContact: (id) => get('/items/' + id + '/contact'),
  getCategories: () => get('/items/categories'),
  publish: (data) => post('/items', data),
  update: (id, data) => put('/items/' + id, data),
  delete: (id) => del('/items/' + id),
  getMyItems: () => get('/items/my'),
  resolve: (id) => post('/items/' + id + '/resolve'),
  renew: (id) => post('/items/' + id + '/renew')
}

export const applicationApi = {
  apply: (data) => post('/applications', data),
  getByItem: (itemId) => get('/applications/item/' + itemId),
  getMyApplications: () => get('/applications/my'),
  handle: (id, action) => post('/applications/' + id + '/handle', { action })
}

export const messageApi = {
  getMessages: () => get('/messages'),
  getUnreadCount: () => get('/messages/count'),
  markRead: (id) => post('/messages/' + id + '/read'),
  markAllRead: () => post('/messages/read-all')
}

export const commentApi = {
  getByItem: (itemId) => get('/comments/item/' + itemId),
  add: (data) => post('/comments', data),
  delete: (id) => del('/comments/' + id)
}

export const aiApi = {
  recognize: (imageUrl) => post('/ai/recognize', { imageUrl })
}

export function uploadImage(filePath) {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token')
    wx.uploadFile({
      url: BASE_URL + '/upload',
      filePath: filePath,
      name: 'file',
      header: {
        'Authorization': token ? 'Bearer ' + token : ''
      },
      success: (res) => {
        try {
          const data = JSON.parse(res.data)
          if (res.statusCode === 200 && data.success) {
            // 用正则确保只替换末尾的 /api
            const baseUrl = BASE_URL.replace(/\/api$/, '')
            resolve(baseUrl + data.url)
          } else {
            reject(data)
          }
        } catch (e) {
          reject({ message: '上传失败' })
        }
      },
      fail: (err) => {
        reject({ message: err.errMsg || '网络请求失败' })
      }
    })
  })
}