package au.com.meathouseskewer.bridge;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class PrinterClient {
    private static final Charset ENCODING = Charset.forName("GB2312");

    static void printTest(String host, int port, String label) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        init(b); center(b); bold(b,true); size(b,1,1);
        line(b,"MEAT HOUSE"); line(b,"SKEWER BRIDGE TEST");
        size(b,0,0); bold(b,false); line(b,label); line(b,host+":"+port);
        line(b,"Printer connection OK"); finish(b);
        send(host,port,b.toByteArray());
    }

    static void printOrder(String host, int port, JSONObject job) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        init(b); center(b); bold(b,true); line(b,"MEAT HOUSE"); line(b,"SKEWER ORDER");
        size(b,1,1); line(b,job.optString("table_name","TABLE"));
        size(b,0,0); line(b,"ROUND "+job.optInt("round_no",0));
        bold(b,false); line(b,"------------------------------------------");
        left(b);
        String created=job.optString("created_at","");
        String time=created;
        try { time=DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Australia/Sydney")).format(Instant.parse(created)); } catch(Exception ignored) {}
        line(b,"ORDER TIME: "+time);
        line(b,"------------------------------------------");
        JSONArray items=job.optJSONArray("items"); int total=0;
        if(items!=null) for(int i=0;i<items.length();i++){
            JSONObject it=items.getJSONObject(i); int qty=it.optInt("qty",0); total+=qty;
            line(b,formatItem(it.optString("name","Item"),qty));
        }
        line(b,"------------------------------------------");
        bold(b,true); size(b,1,0); line(b,"TOTAL SKEWERS: "+total); size(b,0,0); bold(b,false);
        finish(b); send(host,port,b.toByteArray());
    }

    private static String formatItem(String name,int qty){
        if(name.length()>30) name=name.substring(0,30);
        String q="x "+qty; int spaces=Math.max(1,42-name.length()-q.length());
        return name+" ".repeat(spaces)+q;
    }
    private static void send(String host,int port,byte[] data)throws Exception{
        try(Socket s=new Socket()){
            s.connect(new InetSocketAddress(host,port),3000); s.setSoTimeout(5000);
            OutputStream out=s.getOutputStream(); out.write(data); out.flush();
        }
    }
    private static void init(ByteArrayOutputStream b){b.write(0x1B);b.write(0x40);}    
    private static void center(ByteArrayOutputStream b){b.write(0x1B);b.write(0x61);b.write(1);}    
    private static void left(ByteArrayOutputStream b){b.write(0x1B);b.write(0x61);b.write(0);}    
    private static void bold(ByteArrayOutputStream b,boolean on){b.write(0x1B);b.write(0x45);b.write(on?1:0);}    
    private static void size(ByteArrayOutputStream b,int w,int h){b.write(0x1D);b.write(0x21);b.write(((w&7)<<4)|(h&7));}
    private static void line(ByteArrayOutputStream b,String s){try{b.write(s.getBytes(ENCODING));b.write('\n');}catch(Exception ignored){}}
    private static void finish(ByteArrayOutputStream b){b.write('\n');b.write('\n');b.write('\n');b.write(0x1D);b.write(0x56);b.write(1);}
}
