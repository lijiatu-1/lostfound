// Deployment-specific API origins are supplied in runtime-config.local.js.
let runtimeConfig = {}
try {
  runtimeConfig = require('./runtime-config.local.js') || {}
} catch (e) {}

function getEnvironment() {
  try {
    const info = wx.getAccountInfoSync && wx.getAccountInfoSync()
    return (info && info.miniProgram && info.miniProgram.envVersion) || 'develop'
  } catch (e) {
    return 'develop'
  }
}

export const MINI_PROGRAM_ENV = getEnvironment()
const urls = runtimeConfig.apiBaseUrls || runtimeConfig.API_BASE_URLS || {}
const configured = runtimeConfig.API_BASE_URL || urls[MINI_PROGRAM_ENV]
export const API_BASE_URL = (configured || (MINI_PROGRAM_ENV === 'develop' ? 'http://localhost:8080/api' : '')).replace(/\/+$/, '')
export const IS_API_CONFIGURED = Boolean(API_BASE_URL)
