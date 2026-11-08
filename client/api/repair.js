const { env, getAccessToken } = require('../store/auth')
const { get, post, refreshAccessToken, networkError } = require('../utils/request')

/** 报修：提交、我的列表、详情、状态操作与私有图片上传/下载 */

function genTraceId() {
  return Math.random().toString(16).slice(2) + Date.now().toString(16)
}

function listMyRepairs(params) {
  return get('/repairs', params)
}

function getRepair(id) {
  return get('/repairs/' + id)
}

function createRepair(payload) {
  return post('/repairs', payload)
}

function closeRepair(id) {
  return post('/repairs/' + id + '/close', {})
}

function cancelRepair(id) {
  return post('/repairs/' + id + '/cancel', {})
}

function reopenRepair(id) {
  return post('/repairs/' + id + '/reopen', {})
}

/**
 * 上传报修图片：wx.uploadFile 不会自动带 Authorization，
 * 这里显式带 Bearer；401 刷新令牌后重试一次。
 */
function uploadRepairImage(ticketId, filePath, retried) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: env.apiBaseUrl + '/repairs/' + ticketId + '/media',
      filePath,
      name: 'file',
      header: {
        Authorization: 'Bearer ' + getAccessToken(),
        'X-Request-Id': genTraceId(),
      },
      timeout: 60000,
      success(res) {
        let body = {}
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          body = {}
        }
        if (res.statusCode === 401 && !retried) {
          refreshAccessToken()
            .then(() => uploadRepairImage(ticketId, filePath, true).then(resolve, reject))
            .catch(reject)
          return
        }
        if (res.statusCode === 401) {
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        if (res.statusCode >= 400 || body.code !== '0') {
          reject(new Error(body.message || '图片上传失败（' + res.statusCode + '）'))
          return
        }
        resolve(body.data)
      },
      fail(err) {
        reject(networkError(err))
      },
    })
  })
}

/** 报修图片为私有存储：带 Token 下载到临时文件后用于预览 */
function downloadRepairImage(ticketId, mediaId, retried) {
  return new Promise((resolve, reject) => {
    wx.downloadFile({
      url: env.apiBaseUrl + '/repairs/' + ticketId + '/media/' + mediaId,
      header: { Authorization: 'Bearer ' + getAccessToken() },
      timeout: 30000,
      success(res) {
        if (res.statusCode === 401 && !retried) {
          refreshAccessToken()
            .then(() => downloadRepairImage(ticketId, mediaId, true).then(resolve, reject))
            .catch(reject)
          return
        }
        if (res.statusCode === 401) {
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        if (res.statusCode !== 200) {
          reject(new Error('图片加载失败（' + res.statusCode + '）'))
          return
        }
        resolve(res.tempFilePath)
      },
      fail(err) {
        reject(networkError(err))
      },
    })
  })
}

module.exports = {
  listMyRepairs,
  getRepair,
  createRepair,
  closeRepair,
  cancelRepair,
  reopenRepair,
  uploadRepairImage,
  downloadRepairImage,
}
