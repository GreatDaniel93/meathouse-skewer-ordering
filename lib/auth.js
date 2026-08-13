import { cookies } from 'next/headers';
import { db } from './db';

const COOKIE='mh_access';
export async function getAccessToken(){return (await cookies()).get(COOKIE)?.value||'';}
export async function requireRole(allowed){const token=await getAccessToken();if(!token)return null;const {data,error}=await db().rpc('access_role',{p_secret:token});if(error||!allowed.includes(data))return null;return data;}
export async function setAccessCookie(token){(await cookies()).set(COOKIE,token,{httpOnly:true,sameSite:'lax',secure:true,path:'/',maxAge:60*60*12});}
export async function clearAccessCookie(){(await cookies()).set(COOKIE,'',{httpOnly:true,sameSite:'lax',secure:true,path:'/',maxAge:0});}
