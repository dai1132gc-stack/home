const { env, getAccessToken } = require('../store/auth')
const { get, post, refreshAccessToken, networkError } = require('../utils/request')

/** 客户合同：我的合同、详情、确认签署、PDF 下载 */
function listMyContracts(params) {
  return get('/me/contracts', params)
}

function getContract(id) {
  return get('/contracts/' + id)
}

function confirmContract(id) {
  return post('/contracts/' + id + '/confirm', {})
}

function genTraceId() {
  return Math.random().toString(16).slice(2) + Date.now().toString(16)
}

/**
 * 下载并打开合同 PDF：
 * PDF 接口需要 Authorization，wx.downloadFile 不会自动带令牌，这里显式带 Bearer；
 * 401 时用单飞刷新换新 Access Token 后重试一次，仍失败提示重新登录。
 */
function downloadContractPdf(id, retried) {
  return new Promise((resolve, reject) => {
    wx.downloadFile({
      url: env.apiBaseUrl + '/contracts/' + id + '/pdf',
      header: {
        Authorization: 'Bearer ' + getAccessToken(),
        'X-Request-Id': genTraceId(),
      },
      success(res) {
        if (res.statusCode === 401 && !retried) {
          refreshAccessToken()
            .then(() => downloadContractPdf(id, true).then(resolve, reject))
            .catch(reject)
          return
        }
        if (res.statusCode === 401) {
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        if (res.statusCode !== 200) {
          reject(new Error('合同文件下载失败（' + res.statusCode + '），请稍后重试'))
          return
        }
        wx.openDocument({
          filePath: res.tempFilePath,
          fileType: 'pdf',
          showMenu: true,
          success: resolve,
          fail(err) {
            reject(new Error((err && err.errMsg) || '无法打开合同文件'))
          },
        })
      },
      fail(err) {
        reject(networkError(err))
      },
    })
  })
}

module.exports = { listMyContracts, getContract, confirmContract, downloadContractPdf }
