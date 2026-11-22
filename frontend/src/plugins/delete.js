import axios from 'axios'
import { ElLoading, ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'

const baseURL = import.meta.env.VITE_APP_BASE_API
let deleteLoadingInstance;

export default {
  /**
   * 根据文件 URL 删除服务器上的文件
   * @param {String} fileUrl 文件的相对路径或绝对路径
   */
  file(fileUrl) {
    // 这里的 url 拼接方式取决于你后端的接口设计
    const url = baseURL + "/common/delete?resource=" + encodeURIComponent(fileUrl);
    
    deleteLoadingInstance = ElLoading.service({ 
      text: "正在删除文件，请稍候", 
      background: "rgba(0, 0, 0, 0.7)" 
    });

    return axios({
      method: 'delete', // 如果你的后端是 POST 接口，请改为 'post'
      url: url,
      headers: { 'Authorization': 'Bearer ' + getToken() }
    }).then((res) => {
      deleteLoadingInstance.close();
      // 获取后端的返回业务码，若无则默认为 200
      const code = res.data.code || 200;
      const msg = errorCode[code] || res.data.msg || errorCode['default'];
      
      if (code === 200 || msg.includes("文件不存在或已被删除")) {
        ElMessage.success("删除成功");
        return Promise.resolve(res.data);
      } else {
        ElMessage.error(msg);
        return Promise.reject(new Error(msg));
      }
    }).catch((error) => {
      deleteLoadingInstance.close();
      console.error(error);
      ElMessage.error('删除文件出现错误，请联系管理员！');
      return Promise.reject(error);
    });
  }
}