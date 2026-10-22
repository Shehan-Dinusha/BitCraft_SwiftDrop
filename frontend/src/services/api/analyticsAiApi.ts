import {apiClient} from './apiClient';export const analyticsAiApi={list:()=>apiClient.get('/v1/analytics'),get:(id:string)=>apiClient.get(`/v1/analytics/${id}`)};
