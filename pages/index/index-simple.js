Page({
  data: {
    items: []
  },

  onLoad() {
    this.setData({
      items: [
        { id: '1', title: '苹果AirPods Pro蓝牙耳机', type: 'lost', locationName: '图书馆三楼自习室', timeAgo: '2小时前' },
        { id: '2', title: '华为Mate40手机', type: 'lost', locationName: '一食堂二楼', timeAgo: '昨天' },
        { id: '3', title: '蓝色钱包', type: 'found', locationName: '体育馆门口', timeAgo: '3小时前' },
        { id: '4', title: '小米充电宝', type: 'found', locationName: '图书馆一楼', timeAgo: '1天前' }
      ]
    })
  }
})