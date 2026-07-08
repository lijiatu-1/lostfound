/**
 * 格式化时间为"xx分钟前"等友好格式
 * 从7个页面中提取的共享工具函数
 */
function formatTimeAgo(dateStr) {
  if (!dateStr) return ''
  const now = new Date()
  const past = new Date(dateStr)
  const diff = now.getTime() - past.getTime()

  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return '刚刚'
  if (diff < hour) return Math.floor(diff / minute) + '分钟前'
  if (diff < day) return Math.floor(diff / hour) + '小时前'
  if (diff < 30 * day) return Math.floor(diff / day) + '天前'
  return past.toLocaleDateString()
}

/**
 * 获取申请状态文字
 */
function getStatusText(status) {
  const statusMap = {
    pending: '待处理',
    accepted: '已通过',
    rejected: '已拒绝'
  }
  return statusMap[status] || status
}

module.exports = {
  formatTimeAgo,
  getStatusText
}
