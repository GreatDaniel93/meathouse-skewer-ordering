import { db } from '@/lib/db';
import { clearAccessCookie,getAccessToken } from '@/lib/auth';
export async function POST(){const token=await getAccessToken();try{if(token)await db().rpc('access_logout',{p_secret:token});}catch{}await clearAccessCookie();return Response.json({ok:true});}
