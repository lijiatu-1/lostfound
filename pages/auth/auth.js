import { authApi, uploadImage } from '../../utils/api.js'

Page({
  data: {
    formData: {
      realName: '',
      studentId: ''
    },
    cardPhotoAssetId: '',
    cardPhotoUrl: '',
    tempPhoto: '',  // 上传中的本地预览路径，不参与提交
    status: 'unauthorized',
    isSubmitting: false
  },

  onLoad() {
    this.loadUserStatus()
  },

  loadUserStatus() {
    authApi.getUser()
      .then(res => {
        if (res && res.status) {
          this.setData({
            status: res.status,
            formData: {
              realName: res.realName || '',
              studentId: res.studentId || ''
            }
          })
        }
      })
      .catch(err => {
        console.error('获取用户状态失败:', err)
      })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({
      [`formData.${field}`]: e.detail.value
    })
  },

  chooseCardPhoto() {
    wx.showActionSheet({
      itemList: ['拍照', '从相册选择'],
      success: (res) => {
        const sourceType = res.tapIndex === 0 ? ['camera'] : ['album']
        wx.chooseMedia({
          count: 1,
          mediaType: ['image'],
          sizeType: ['compressed'],
          sourceType: sourceType,
          success: (res) => {
            const tempPath = res.tempFiles[0].tempFilePath
            // 本地路径只用于上传中的预览；认证提交始终只携带受控资产 ID。
            this.setData({ tempPhoto: tempPath, cardPhotoAssetId: '', cardPhotoUrl: '' })
            wx.showLoading({ title: '上传图片中...' })
            uploadImage(tempPath, 'certification')
              .then(asset => {
                wx.hideLoading()
                this.setData({ cardPhotoAssetId: asset.assetId, cardPhotoUrl: tempPath, tempPhoto: '' })
              })
              .catch(err => {
                wx.hideLoading()
                // 上传失败时清除预览，防止提交本地路径
                this.setData({ tempPhoto: '' })
                wx.showToast({ title: err.message || '图片上传失败', icon: 'none' })
              })
          },
          fail: (err) => {
            console.log('选择图片失败:', err)
          }
        })
      }
    })
  },

  submitCertification() {
    const { realName, studentId } = this.data.formData

    if (!realName.trim()) {
      wx.showToast({
        title: '请输入真实姓名',
        icon: 'none'
      })
      return
    }

    if (!studentId.trim()) {
      wx.showToast({
        title: '请输入学号',
        icon: 'none'
      })
      return
    }

    if (!this.data.cardPhotoAssetId) {
      wx.showToast({
        title: '请上传校园卡照片',
        icon: 'none'
      })
      return
    }

    this.setData({ isSubmitting: true })

    const data = {
      realName: realName.trim(),
      studentId: studentId.trim(),
      cardPhotoAssetId: this.data.cardPhotoAssetId
    }

    authApi.certification(data)
      .then(res => {
        this.setData({ isSubmitting: false })
        if (res.success) {
          wx.showToast({
            title: '提交成功，等待审核',
            icon: 'success'
          })
          setTimeout(() => {
            wx.navigateBack()
          }, 1500)
        } else {
          wx.showToast({
            title: res.message || '提交失败',
            icon: 'none'
          })
        }
      })
      .catch(err => {
        this.setData({ isSubmitting: false })
        wx.showToast({
          title: err.message || '提交失败',
          icon: 'none'
        })
      })
  },

  goBack() {
    wx.navigateBack()
  }
})
