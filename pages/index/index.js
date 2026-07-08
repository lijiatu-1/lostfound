import { itemApi } from '../../utils/api.js'
import { formatTimeAgo } from '../../utils/formatTime.js'

Page({
  data: {
    currentTab: 0,
    currentCategory: '',
    keyword: '',
    categories: [],
    items: [],
    filteredList: [],
    loading: true,
    isRefreshing: false
  },

  onLoad() {
    this.loadCategories()
    this.loadItems()
  },

  onShow() {
    if (this.data.items.length === 0) {
      this.loadItems()
    }
  },

  onPullDownRefresh() {
    this.setData({ isRefreshing: true })
    this.loadCategories()
    this.loadItems()
  },

  onRefresh() {
    this.setData({ isRefreshing: true })
    this.loadItems()
  },

  loadCategories() {
    itemApi.getCategories()
      .then(res => {
        const categories = res || []
        this.setData({ categories })
      })
      .catch(err => {
        console.error('加载分类失败:', err)
        this.setData({ categories: ['证件卡片', '电子产品', '服饰配件', '学习用品', '生活用品', '其他物品'] })
      })
  },

  loadItems() {
    const type = this.data.currentTab === 1 ? 'lost' : (this.data.currentTab === 2 ? 'found' : '')
    const params = {}
    if (type) params.type = type
    if (this.data.keyword) params.keyword = this.data.keyword
    if (this.data.currentCategory) params.category = this.data.currentCategory

    itemApi.getItems(params)
      .then(res => {
        const list = (res && res.items) || res || []
        const items = list.map(item => {
          if (item.createdAt) {
            item.timeAgo = formatTimeAgo(item.createdAt)
          }
          if (!item.imageList) {
            item.imageList = this.parseImages(item.images)
          }
          return item
        })
        this.setData({ items, filteredList: items, loading: false, isRefreshing: false })
        wx.stopPullDownRefresh()
      })
      .catch(err => {
        console.error('加载列表失败:', err)
        this.setData({ items: this.getMockItems(), loading: false, isRefreshing: false }, () => {
          this.filterItems()
        })
        wx.stopPullDownRefresh()
      })
  },

  getMockItems() {
    return [
      { id: 1, title: '苹果AirPods Pro蓝牙耳机', type: 'lost', locationName: '图书馆三楼自习室', timeAgo: '2小时前', status: 'active', imageList: [] },
      { id: 2, title: '华为Mate40手机', type: 'lost', locationName: '一食堂二楼', timeAgo: '昨天', status: 'active', imageList: [] },
      { id: 3, title: '学生证', type: 'lost', locationName: '教学楼A栋', timeAgo: '3天前', status: 'active', imageList: [] },
      { id: 4, title: '蓝色钱包', type: 'found', locationName: '体育馆门口', timeAgo: '3小时前', status: 'active', imageList: [] },
      { id: 5, title: '小米充电宝', type: 'found', locationName: '图书馆一楼', timeAgo: '1天前', status: 'active', imageList: [] },
      { id: 6, title: '雨伞', type: 'found', locationName: '教学楼B栋', timeAgo: '2天前', status: 'active', imageList: [] }
    ]
  },

  parseImages(images) {
    if (!images) return []
    try {
      return JSON.parse(images)
    } catch (e) {
      return []
    }
  },

  switchTab(e) {
    const tab = parseInt(e.currentTarget.dataset.tab)
    this.setData({ currentTab: tab }, () => {
      this.loadItems()
    })
  },

  switchCategory(e) {
    const category = e.currentTarget.dataset.category
    this.setData({ currentCategory: category }, () => {
      this.loadItems()
    })
  },

  onSearchInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  onSearchConfirm() {
    this.loadItems()
  },

  clearSearch() {
    this.setData({ keyword: '' }, () => {
      this.loadItems()
    })
  },

  filterItems() {
    const keyword = this.data.keyword ? this.data.keyword.toLowerCase() : ''
    let list = [...this.data.items]

    if (this.data.currentTab === 1) {
      list = list.filter(item => item.type === 'lost')
    } else if (this.data.currentTab === 2) {
      list = list.filter(item => item.type === 'found')
    }

    if (keyword) {
      list = list.filter(item =>
        (item.title || '').toLowerCase().includes(keyword) ||
        (item.locationName || '').toLowerCase().includes(keyword)
      )
    }

    this.setData({ filteredList: list })
  },

  goToDetail(e) {
    wx.navigateTo({
      url: '/pages/detail/detail?id=' + e.currentTarget.dataset.id
    })
  },

  goToPublish() {
    wx.switchTab({
      url: '/pages/publish/publish'
    })
  }
})
