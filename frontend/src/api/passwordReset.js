import axios from 'axios'
import { apiBase, isAdminApp } from '@/config/surface'
import { ApiError } from './errors'
// Public credential-recovery calls must not refresh or attach an unrelated stored JWT.
const client=axios.create({baseURL:apiBase,withCredentials:true,timeout:15000,headers:{'Content-Type':'application/json'}})
async function post(path,payload){
 try {return (await client.post(path,payload)).data.data}
 catch(e){throw new ApiError(e.response?.data,e.response?.status,e.message || '連線失敗，請稍後重試')}
}
export const passwordResetApi={
 request:email=>post('/auth/forgot-password',{email,surface:isAdminApp?'admin':'storefront'}),
 reset:(token,newPassword)=>post('/auth/reset-password',{token,newPassword})
}
