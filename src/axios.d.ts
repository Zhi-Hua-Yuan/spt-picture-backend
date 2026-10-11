import 'axios'

declare module 'axios' {
  interface AxiosRequestConfig {
    requestType?: string
  }
}
