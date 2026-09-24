import { API_BASE_URL, IS_API_CONFIGURED } from './config.js'

function apiNotConfiguredError() {
  return { code: 'API_NOT_CONFIGURED', message: '服务地址尚未配置，请联系管理员' }
}

function getErrorMessage(payload, fallback) {
  if (payload && typeof payload === 'object') return payload.message || payload.error || fallback
  return typeof payload === 'string' ? payload : fallback
}

export function resolveAssetUrl(value) {
  if (!value) return ''
  if (!IS_API_CONFIGURED) return ''
  const origin = API_BASE_URL.replace(/\/api$/, '')
  if (/^https?:\/\//i.test(value)) {
    return value.startsWith(origin + '/api/assets/') ? value : ''
  }
  if (/^\/api\/assets\/[0-9]+\/content$/.test(value)) return origin + value
  return /^[0-9]+$/.test(String(value)) ? origin + '/api/assets/' + encodeURIComponent(value) + '/content' : ''
}

function request(url, method = 'GET', data) {
  return new Promise((resolve, reject) => {
    if (!IS_API_CONFIGURED) {
      reject(apiNotConfiguredError())
      return
    }

    const token = wx.getStorageSync('token')
    wx.request({
      url: API_BASE_URL + url,
      method,
      data,
      header: {
        'Content-Type': 'application/json',
        Authorization: token ? 'Bearer ' + token : ''
      },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
          return
        }

        if (res.statusCode === 401) {
          wx.removeStorageSync('token')
          const app = getApp()
          if (app) {
            app.globalData.userId = ''
            app.globalData.isAuthenticated = false
            app.globalData.isAdmin = false
            if (url !== '/auth/login' && app.login) app.globalData.loginPromise = app.login().catch(() => null)
          }
          reject({ code: 'UNAUTHORIZED', message: '登录已过期，请重试' })
          return
        }

        reject({ code: res.statusCode, message: getErrorMessage(res.data, '请求失败') })
      },
      fail: (err) => {
        reject({ code: 'NETWORK_ERROR', message: (err && err.errMsg) || '网络异常，请检查网络后重试' })
      }
    })
  })
}

function buildQuery(params) {
  if (!params) return ''
  const parts = []
  Object.keys(params).forEach((key) => {
    const value = params[key]
    if (value !== undefined && value !== null && value !== '') {
      parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value))
    }
  })
  return parts.length ? '?' + parts.join('&') : ''
}

function get(url, params) { return request(url + buildQuery(params), 'GET') }
function post(url, data) { return request(url, 'POST', data) }
function put(url, data) { return request(url, 'PUT', data) }
function del(url) { return request(url, 'DELETE') }

export const authApi = {
  login: (code) => post('/auth/login', { code }),
  getUser: () => get('/auth/user'),
  certification: (data) => post('/auth/certification', data),
  updateProfile: (data) => post('/auth/profile', data),
  getPendingCertifications: () => get('/auth/certifications/pending'),
  reviewCertification: (id, data) => post('/auth/certification/' + id + '/review', data)
}

export const itemApi = {
  getItems: (params) => get('/items', params),
  getItem: (id) => get('/items/' + id),
  getCategories: () => get('/items/categories'),
  publish: (data) => post('/items', data),
  update: (id, data) => put('/items/' + id, data),
  delete: (id) => del('/items/' + id),
  getMyItems: () => get('/items/my'),
  resolve: (id) => post('/items/' + id + '/resolve'),
  reopen: (id) => post('/items/' + id + '/reopen'),
  renew: (id) => post('/items/' + id + '/renew')
}

export const applicationApi = {
  // Application kind is derived by the server from the item type.
  apply: ({ itemId, content }) => post('/applications', { itemId, content }),
  getByItem: (itemId) => get('/applications/item/' + itemId),
  getMyApplications: () => get('/applications/my'),
  handle: (id, action) => post('/applications/' + id + '/handle', { action })
}

export const conversationApi = {
  getConversations: () => get('/conversations'),
  getMessages: (id, params) => get('/conversations/' + id + '/messages', params),
  sendMessage: (id, content) => post('/conversations/' + id + '/messages', { content }),
  markRead: (id) => post('/conversations/' + id + '/read')
}

export const messageApi = {
  getMessages: () => get('/messages'),
  // The backend aggregates system-notification and conversation unread counts.
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
  recognize: (assetId) => post('/ai/recognize', { assetId })
}

export function uploadImage(filePath, purpose = 'item') {
  return new Promise((resolve, reject) => {
    if (!IS_API_CONFIGURED) {
      reject(apiNotConfiguredError())
      return
    }

    const token = wx.getStorageSync('token')
    wx.uploadFile({
      url: API_BASE_URL + '/assets',
      filePath,
      name: 'file',
      formData: { purpose },
      header: { Authorization: token ? 'Bearer ' + token : '' },
      success: (res) => {
        let data
        try {
          data = JSON.parse(res.data)
        } catch (e) {
          reject({ message: '上传服务返回了无效数据' })
          return
        }

        const assetId = data && (data.assetId || data.id || (data.asset && data.asset.id))
        if (res.statusCode >= 200 && res.statusCode < 300 && data && data.success !== false && assetId) {
          const rawUrl = data.url || (data.asset && data.asset.url) || String(assetId)
          resolve({ assetId: String(assetId), url: resolveAssetUrl(rawUrl) })
          return
        }
        reject({ code: res.statusCode, message: getErrorMessage(data, '上传失败') })
      },
      fail: (err) => {
        reject({ code: 'NETWORK_ERROR', message: (err && err.errMsg) || '网络异常，请检查网络后重试' })
      }
    })
  })
}

export function downloadPrivateAsset(assetId) {
  return new Promise((resolve, reject) => {
    if (!IS_API_CONFIGURED || !/^[0-9]+$/.test(String(assetId))) {
      reject({ message: '图片资源不可用' })
      return
    }
    const token = wx.getStorageSync('token')
    wx.downloadFile({
      url: API_BASE_URL + '/assets/' + assetId + '/content',
      header: { Authorization: token ? 'Bearer ' + token : '' },
      success: res => {
        if (res.statusCode === 200) resolve(res.tempFilePath)
        else reject({ message: '无权查看或图片已失效' })
      },
      fail: () => reject({ message: '图片加载失败' })
    })
  })
}
