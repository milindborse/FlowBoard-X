import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use(config => {
  const raw = localStorage.getItem('fbx_user')
  if (raw) {
    const user = JSON.parse(raw)
    if (user?.token) config.headers.Authorization = `Bearer ${user.token}`
  }
  return config
})

api.interceptors.response.use(
  r => r,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('fbx_user')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default api
