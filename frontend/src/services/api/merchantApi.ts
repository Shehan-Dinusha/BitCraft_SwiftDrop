import {apiClient} from './apiClient';export const merchantApi={list:()=>apiClient.get('/v1/merchants'),get:(id:string)=>apiClient.get(`/v1/merchants/${id}`)};
