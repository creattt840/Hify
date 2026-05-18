import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  },
)

export default http
