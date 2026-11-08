const { env, getAccessToken } = require('../store/auth')
const { get, post } = require('../utils/request')

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

/**
 * 下载并打开合同 PDF：
 * PDF 接口需要 Authorization，不能用 wx.downloadFile 的默认请求头，
 * 因此显式带 Bearer Token，下载到临时文件后用 wx.openDocument 预览。
 */
function downloadContractPdf(id) {
  return new Promise((resolve, reject) => {
    wx.downloadFile({
      url: env.apiBaseUrl + '/contracts/' + id + '/pdf',
      header: { Authorization: 'Bearer ' + getAccessToken() },
      success(res) {
        if (res.statusCode !== 200) {
          reject(new Error('合同文件下载失败（' + res.statusCode + '）'))
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
        reject(new Error((err && err.errMsg) || '网络异常，下载失败'))
      },
    })
  })
}

module.exports = { listMyContracts, getContract, confirmContract, downloadContractPdf }
