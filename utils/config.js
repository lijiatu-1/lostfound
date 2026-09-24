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
const productionLike = MINI_PROGRAM_ENV === 'trial' || MINI_PROGRAM_ENV === 'release'
export const IS_API_CONFIGURED = Boolean(API_BASE_URL) &&
  (!productionLike || (/^https:\/\/[^/]+\/api$/.test(API_BASE_URL) &&
    !/^https:\/\/(localhost|127\.0\.0\.1)(:|\/)/i.test(API_BASE_URL) &&
    !/^https:\/\/[^/]*\.(example\.edu|example)\/api$/i.test(API_BASE_URL)))
