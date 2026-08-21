import {db} from '@/lib/db';
import {getAccessToken,requireRole} from '@/lib/auth';

export async function GET(){
  const role=await requireRole(['manager']);
  if(!role)return Response.json({error:'Manager login required.'},{status:401});
  const token=await getAccessToken();
  const {data,error}=await db().rpc('manager_print_health',{p_secret:token});
  if(error)return Response.json({error:error.message},{status:409});
  return Response.json(data);
}

export async function POST(request){
  const role=await requireRole(['manager']);
  if(!role)return Response.json({error:'Manager login required.'},{status:401});
  const token=await getAccessToken();
  const body=await request.json().catch(()=>({}));
  const jobId=String(body.job_id||'');
  const printer=Number(body.printer);
  if(!jobId||![0,1,2].includes(printer))return Response.json({error:'Invalid reprint request.'},{status:400});
  const {data,error}=await db().rpc('manager_reprint_job',{p_secret:token,p_job_id:jobId,p_printer:printer});
  if(error)return Response.json({error:error.message},{status:409});
  return Response.json(data);
}
