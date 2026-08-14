package au.com.meathouseskewer.bridge;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.text.InputType;
import android.widget.*;

public class MainActivity extends Activity {
    private EditText ip1,ip2,port,secret; private TextView status; private final Handler h=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle b){super.onCreate(b);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},5);buildUi();h.post(refresh);}
    private void buildUi(){
        SharedPreferences p=getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE);
        ScrollView sv=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,28,32,40);sv.addView(root);
        root.addView(t("MEAT HOUSE",30,true));root.addView(t("SKEWER PRINT BRIDGE",16,true));root.addView(t("Two printers receive the same skewer order.",14,false));
        ip1=field("Printer 1 IP",p.getString("ip1","192.168.0.192"));root.addView(ip1);
        ip2=field("Printer 2 IP",p.getString("ip2","192.168.0.193"));root.addView(ip2);
        port=field("Port",String.valueOf(p.getInt("port",9100)));port.setInputType(InputType.TYPE_CLASS_NUMBER);root.addView(port);
        secret=field("Bridge Key",p.getString("secret",""));secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);root.addView(secret);
        LinearLayout tests=row();Button t1=button("TEST P1"),t2=button("TEST P2"),tb=button("TEST BOTH");tests.addView(t1);tests.addView(t2);tests.addView(tb);root.addView(tests);
        Button start=button("START BRIDGE");start.setBackgroundColor(Color.rgb(111,40,35));start.setTextColor(Color.WHITE);root.addView(start);
        Button stop=button("STOP BRIDGE");root.addView(stop);
        status=t("Status: "+p.getString("status","Stopped"),16,true);status.setPadding(0,24,0,0);root.addView(status);
        root.addView(t("Default printers: 192.168.0.192 and 192.168.0.193 · ESC/POS · TCP 9100",12,false));
        setContentView(sv);
        t1.setOnClickListener(v->test(1));t2.setOnClickListener(v->test(2));tb.setOnClickListener(v->{test(1);test(2);});
        start.setOnClickListener(v->startBridge());
        stop.setOnClickListener(v->stopBridge());
    }
    private void startBridge(){
        try{
            save();
            SharedPreferences p=getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE);
            if(secret.getText().toString().trim().isEmpty()){toast("Bridge Key is required");return;}
            p.edit().putBoolean("enabled",true).putString("status","Starting...").apply();
            Intent i=new Intent(this,BridgeService.class);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
            toast("Starting bridge...");
        }catch(Exception e){
            getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putBoolean("enabled",false).putString("status","Start failed: "+e.getClass().getSimpleName()+" - "+String.valueOf(e.getMessage())).apply();
            toast("Start failed: "+e.getMessage());
        }
    }
    private void stopBridge(){
        try{
            Intent i=new Intent(this,BridgeService.class);i.setAction(BridgeService.ACTION_STOP);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        }catch(Exception e){stopService(new Intent(this,BridgeService.class));getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putBoolean("enabled",false).putString("status","Stopped").apply();}
        toast("Bridge stopped");
    }
    private void save(){int prt=9100;try{prt=Integer.parseInt(port.getText().toString().trim());}catch(Exception ignored){}getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putString("ip1",ip1.getText().toString().trim()).putString("ip2",ip2.getText().toString().trim()).putInt("port",prt).putString("secret",secret.getText().toString().trim()).apply();}
    private void test(int which){save();String host=which==1?ip1.getText().toString().trim():ip2.getText().toString().trim();int prt=9100;try{prt=Integer.parseInt(port.getText().toString().trim());}catch(Exception ignored){}final int fp=prt;new Thread(()->{try{PrinterClient.printTest(host,fp,"PRINTER "+which);runOnUiThread(()->toast("Printer "+which+" OK"));}catch(Exception e){runOnUiThread(()->toast("Printer "+which+" failed: "+e.getMessage()));}}).start();}
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setPadding(18,18,18,18);return e;}
    private TextView t(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.rgb(35,28,25));if(bold)v.setTypeface(null,1);v.setPadding(0,8,0,8);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));return b;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private final Runnable refresh=new Runnable(){public void run(){if(status!=null){SharedPreferences p=getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE);status.setText("Status: "+p.getString("status",p.getBoolean("enabled",false)?"Starting...":"Stopped"));}h.postDelayed(this,750);}};
    @Override protected void onDestroy(){h.removeCallbacks(refresh);super.onDestroy();}
}
