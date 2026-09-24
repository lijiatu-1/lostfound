import { itemApi } from '../../utils/api.js'
import { formatTimeAgo } from '../../utils/formatTime.js'

Page({
  data: {
    currentTab: 'all',
    publishList: [],
    filteredList: [],
    error: ''
  },

  onLoad() {
    this.loadPublishList()
  },

  onShow() {
    this.loadPublishList()
  },

  loadPublishList() {
    itemApi.getMyItems()
      .then(res => {
        const list = (res && res.items) || res || []
        const items = list.map(item => {
          item.timeAgo = formatTimeAgo(item.createdAt)
          return item
        })
        this.setData({ publishList: items, error: '' }, () => {
          this.filterItems()
        })
      })
      .catch(err => {
        console.error('加载我的发布失败:', err)
        this.setData({ publishList: [], error: err.message || '加载失败，请重试' }, () => {
          this.filterItems()
        })
      })
  },

  retry() { this.loadPublishList() },
  setTab(e) {
    const tab = e.currentTarget.dataset.tab
    this.setData({ currentTab: tab }, () => {
      this.filterItems()
    })
  },

  filterItems() {
    const list = this.data.currentTab === 'all'
      ? this.data.publishList
      : this.data.publishList.filter(item => item.status === this.data.currentTab)
    this.setData({ filteredList: list })
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: '/pages/detail/detail?id=' + id
    })
  },

  editItem(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: '/pages/detail/detail?id=' + id
    })
  },

  deleteItem(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这条发布吗？',
      success: (res) => {
        if (res.confirm) {
          itemApi.delete(id)
            .then(res => {
              if (res.success) {
                wx.showToast({ title: '删除成功', icon: 'success' })
                this.loadPublishList()
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

  renewItem(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认延期',
      content: '确定要延期7天吗？物品将重新变为进行中。',
      success: (res) => {
        if (res.confirm) {
          itemApi.renew(id)
            .then(res => {
              if (res.success) {
                wx.showToast({ title: '延期成功', icon: 'success' })
                this.loadPublishList()
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
