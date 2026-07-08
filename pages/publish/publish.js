import { itemApi, uploadImage, aiApi } from '../../utils/api.js'

Page({
  data: {
    type: 'lost',
    imageUrls: [],
    imageUploading: false,
    aiLoading: false,
    currentLocation: '',
    currentLat: null,   // 定位获取的纬度
    currentLng: null,   // 定位获取的经度
    editMode: false,
    editId: null,
    categories: [],
    selectedCategory: '其他物品',
    formData: {
      title: '',
      location: '',
      description: '',
      phone: ''
    }
  },

  onLoad(options) {
    this.loadCategories()
    // 支持两种方式进入编辑模式：URL 参数 或 globalData
    const editId = options.id || getApp().globalData.editItemId
    if (editId) {
      getApp().globalData.editItemId = null // 用完清掉
      this.setData({ editMode: true, editId: editId })
      wx.setNavigationBarTitle({ title: '编辑物品' })
      this.loadItemForEdit(editId)
    }
  },

  onShow() {
    // switchTab 不触发 onLoad，从详情页跳过来时在这里处理
    const app = getApp()
    if (app.globalData.editItemId) {
      const editId = app.globalData.editItemId
      app.globalData.editItemId = null
      this.setData({ editMode: true, editId: editId })
      wx.setNavigationBarTitle({ title: '编辑物品' })
      this.loadItemForEdit(editId)
    } else if (!this.data.editMode) {
      // 非编辑模式时重置表单
      this.setData({
        imageUrls: [],
        selectedCategory: '其他物品',
        formData: { title: '', location: '', description: '', phone: '' }
      })
    }
  },

  loadItemForEdit(id) {
    wx.showLoading({ title: '加载中...' })
    itemApi.getItem(id)
      .then(res => {
        wx.hideLoading()
        if (!res) return
        this.setData({
          type: res.type || 'lost',
          formData: {
            title: res.title || '',
            location: res.locationName || '',
            description: res.description || '',
            phone: ''
          }
        })
        if (res.images) {
          try {
            const imgs = JSON.parse(res.images)
            if (imgs.length > 0) {
              this.setData({ imageUrls: imgs })
            }
          } catch (e) {}
        }
        if (res.category) {
          this.setData({ selectedCategory: res.category })
        }
      })
      .catch(err => {
        wx.hideLoading()
        wx.showToast({ title: '加载失败', icon: 'none' })
      })
  },

  loadCategories() {
    itemApi.getCategories()
      .then(res => {
        this.setData({ categories: res || [] })
      })
      .catch(err => {
        this.setData({ categories: ['证件卡片', '电子产品', '服饰配件', '学习用品', '生活用品', '其他物品'] })
      })
  },

  selectCategory(e) {
    this.setData({ selectedCategory: e.currentTarget.dataset.category })
  },

  aiRecognize() {
    if (this.data.imageUrls.length === 0 || this.data.aiLoading) return

    const { title, description } = this.data.formData
    if (title.trim() || description.trim()) {
      wx.showModal({
        title: '确认覆盖',
        content: 'AI识别会覆盖已填写的标题和描述，确定继续吗？',
        success: (res) => {
          if (res.confirm) {
            this.doAiRecognize()
          }
        }
      })
    } else {
      this.doAiRecognize()
    }
  },

  doAiRecognize() {
    this.setData({ aiLoading: true })
    wx.showLoading({ title: 'AI识别中...' })

    // 用第一张图片进行识别
    aiApi.recognize(this.data.imageUrls[0])
      .then(res => {
        wx.hideLoading()
        this.setData({ aiLoading: false })

        if (res && res.success) {
          const updateData = {}
          if (res.title) {
            updateData['formData.title'] = res.title
          }
          if (res.description) {
            updateData['formData.description'] = res.description
          }
          if (res.category) {
            updateData['selectedCategory'] = res.category
          }
          this.setData(updateData)
          wx.showToast({ title: '识别成功，请检查并补充信息', icon: 'none', duration: 2000 })
        } else {
          console.error('AI识别失败:', res.message)
          const msg = this.isRateLimitError(res.message) ? '使用人数过多，请重新点击' : '识别失败，请稍后重试'
          wx.showToast({ title: msg, icon: 'none' })
        }
      })
      .catch(err => {
        wx.hideLoading()
        this.setData({ aiLoading: false })
        console.error('AI识别异常:', err.message)
        const msg = this.isRateLimitError(err.message) ? '使用人数过多，请重新点击' : '识别失败，请稍后重试'
        wx.showToast({ title: msg, icon: 'none' })
      })
  },

  switchType() {
    const types = ['lost', 'found']
    const currentIndex = types.indexOf(this.data.type)
    const nextIndex = (currentIndex + 1) % types.length
    this.setData({
      type: types[nextIndex]
    })
  },

  chooseImage() {
    const remaining = 9 - this.data.imageUrls.length
    if (remaining <= 0) return

    wx.showActionSheet({
      itemList: ['拍照', '从相册选择'],
      success: (res) => {
        const sourceType = res.tapIndex === 0 ? ['camera'] : ['album']
        wx.chooseMedia({
          count: remaining,
          mediaType: ['image'],
          sizeType: ['compressed'],
          sourceType: sourceType,
          success: (res) => {
            const tempPaths = res.tempFiles.map(f => f.tempFilePath)
            this.setData({ imageUploading: true })
            wx.showLoading({ title: '上传图片中...' })

            // 使用 Promise.allSettled 并行上传，避免手动计数器竞态
            const uploadPromises = tempPaths.map(path => uploadImage(path))
            Promise.allSettled(uploadPromises)
              .then(results => {
                wx.hideLoading()
                const successUrls = []
                let failCount = 0
                results.forEach(r => {
                  if (r.status === 'fulfilled') {
                    successUrls.push(r.value)
                  } else {
                    failCount++
                  }
                })
                this.setData({
                  imageUrls: [...this.data.imageUrls, ...successUrls],
                  imageUploading: false
                })
                if (failCount > 0 && successUrls.length > 0) {
                  wx.showToast({ title: '部分图片上传失败', icon: 'none' })
                } else if (failCount > 0 && successUrls.length === 0) {
                  wx.showToast({ title: '图片上传失败', icon: 'none' })
                }
              })
          },
          fail: (err) => {
            console.log('选择图片失败:', err)
          }
        })
      }
    })
  },

  deleteImage(e) {
    const index = e.currentTarget.dataset.index
    const imageUrls = [...this.data.imageUrls]
    imageUrls.splice(index, 1)
    this.setData({ imageUrls })
  },

  getLocation() {
    wx.showLoading({
      title: '获取位置中...'
    })
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        // 保存经纬度，提交时发送给后端
        this.setData({
          currentLat: res.latitude,
          currentLng: res.longitude
        })
        this.reverseGeocode(res.latitude, res.longitude)
      },
      fail: (err) => {
        wx.hideLoading()
        wx.showModal({
          title: '获取位置失败',
          content: '请检查定位权限设置',
          showCancel: false
        })
      }
    })
  },

  reverseGeocode(latitude, longitude) {
    wx.request({
      url: `https://apis.map.qq.com/ws/geocoder/v1/?location=${latitude},${longitude}&key=OB4BZ-D4W3U-B7VVO-4PJWW-6TKDJ-WPB77`,
      success: (res) => {
        wx.hideLoading()
        if (res.data.status === 0) {
          const address = [res.data.result.address_component.city, res.data.result.address_component.district, res.data.result.address_component.street].filter(Boolean).join('')
          this.setData({
            currentLocation: address,
            // 自动将定位地址填入 location 输入框
            'formData.location': address
          })
        } else {
          this.setData({
            currentLocation: '定位成功，但解析失败'
          })
        }
      },
      fail: () => {
        wx.hideLoading()
        this.setData({
          currentLocation: '地址解析失败'
        })
      }
    })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({
      [`formData.${field}`]: e.detail.value
    })
  },

  submitForm() {
    const { title, location, description } = this.data.formData

    if (this.data.imageUploading) {
      wx.showToast({ title: '图片正在上传中...', icon: 'none' })
      return
    }

    if (!title.trim()) {
      wx.showToast({
        title: '请输入物品名称',
        icon: 'none'
      })
      return
    }

    if (!location.trim()) {
      wx.showToast({
        title: '请输入地点',
        icon: 'none'
      })
      return
    }

    if (!description.trim()) {
      wx.showToast({
        title: '请输入详细描述',
        icon: 'none'
      })
      return
    }

    wx.showLoading({
      title: this.data.editMode ? '保存中...' : '发布中...'
    })

    const data = {
      title: title.trim(),
      description: description.trim(),
      locationName: location.trim(),
      category: this.data.selectedCategory,
      images: JSON.stringify(this.data.imageUrls),
      tags: JSON.stringify(this.extractTags(title, description))
    }

    // 附带经纬度（如果有的话）
    if (this.data.currentLat != null && this.data.currentLng != null) {
      data.locationLat = this.data.currentLat
      data.locationLng = this.data.currentLng
    }

    const phone = this.data.formData.phone.trim()
    if (phone || !this.data.editMode) {
      data.phone = phone
    }

    if (!this.data.editMode) {
      data.type = this.data.type
    }

    const apiCall = this.data.editMode
      ? itemApi.update(this.data.editId, data)
      : itemApi.publish(data)

    apiCall
      .then(res => {
        wx.hideLoading()
        if (res.success) {
          wx.showToast({
            title: this.data.editMode ? '保存成功' : '发布成功',
            icon: 'success'
          })
          this.setData({
            imageUrls: [],
            currentLocation: '',
            currentLat: null,
            currentLng: null,
            formData: { title: '', location: '', description: '', phone: '' },
            // 重置编辑模式，防止下次提交时错误调用 update
            editMode: false,
            editId: null
          })
          setTimeout(() => {
            if (this.data.editMode) {
              wx.navigateBack()
            } else {
              wx.switchTab({ url: '/pages/index/index' })
            }
          }, 800)
        } else {
          wx.showToast({
            title: res.message || '操作失败',
            icon: 'none'
          })
        }
      })
      .catch(err => {
        wx.hideLoading()
        console.error('操作失败:', err)
        wx.showToast({
          title: err.message || '操作失败',
          icon: 'none'
        })
      })
  },

  extractTags(title, description) {
    const keywords = ['耳机', '手机', '钱包', '钥匙', '学生证', '校园卡', '电脑', '书籍', '雨伞', '充电宝', '水杯', '书包', '眼镜', '手表', '充电器', '数据线']
    const tags = []

    keywords.forEach(keyword => {
      if (title.includes(keyword) || description.includes(keyword)) {
        tags.push(keyword)
      }
    })

    return tags.length > 0 ? tags : ['其他']
  },

  isRateLimitError(msg) {
    if (!msg) return false
    return msg.includes('人数') || msg.includes('访问量') || msg.includes('429') || msg.includes('Too Many')
  }
})
