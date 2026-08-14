package au.com.meathouseskewer.bridge;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    private EditText ip1,ip2,port;
    private TextView statusText,statusPill,backgroundState;
    private Button start,stop;
    private final Handler h=new Handler(Looper.getMainLooper());
    private final int burgundy=Color.rgb(105,32,31), ink=Color.rgb(32,28,26), cream=Color.rgb(248,246,242), line=Color.rgb(225,220,214);

    @Override public void onCreate(Bundle b){super.onCreate(b);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},5);buildUi();h.post(refresh);}

    private void buildUi(){
        SharedPreferences p=getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE);
        ScrollView sv=new ScrollView(this);sv.setBackgroundColor(cream);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(22),dp(20),dp(36));sv.addView(root);

        root.addView(text("MEAT HOUSE",32,true,ink));
        TextView sub=text("SKEWER PRINT BRIDGE",14,true,burgundy);sub.setLetterSpacing(.12f);root.addView(sub);
        TextView desc=text("Kitchen printer controller · dual printer mode",13,false,Color.DKGRAY);desc.setPadding(0,0,0,dp(16));root.addView(desc);

        LinearLayout statusCard=card();statusCard.setPadding(dp(18),dp(16),dp(18),dp(16));
        statusPill=text("● STOPPED",13,true,Color.rgb(130,130,130));statusCard.addView(statusPill);
        statusText=text(p.getString("status","Stopped"),17,true,ink);statusText.setPadding(0,dp(6),0,0);statusCard.addView(statusText);
        TextView hint=text("Bridge access is built in. Keep this phone on store Wi-Fi and power.",12,false,Color.DKGRAY);hint.setPadding(0,dp(4),0,0);statusCard.addView(hint);root.addView(statusCard);

        section(root,"PRINTERS");
        LinearLayout pcard=card();pcard.setPadding(dp(16),dp(12),dp(16),dp(14));
        pcard.addView(label("Printer 1"));ip1=field("192.168.0.192",p.getString("ip1","192.168.0.192"));pcard.addView(ip1);
        pcard.addView(label("Printer 2"));ip2=field("192.168.0.193",p.getString("ip2","192.168.0.193"));pcard.addView(ip2);
        pcard.addView(label("TCP Port"));port=field("9100",String.valueOf(p.getInt("port",9100)));port.setInputType(InputType.TYPE_CLASS_NUMBER);pcard.addView(port);root.addView(pcard);

        LinearLayout tests=new LinearLayout(this);tests.setOrientation(LinearLayout.HORIZONTAL);tests.setPadding(0,dp(10),0,0);
        Button t1=smallButton("TEST P1"),t2=smallButton("TEST P2"),tb=smallButton("TEST BOTH");tests.addView(t1);tests.addView(t2);tests.addView(tb);root.addView(tests);

        section(root,"BACKGROUND RUNNING");
        LinearLayout bg=card();bg.setPadding(dp(16),dp(14),dp(16),dp(14));
        backgroundState=text("Checking battery settings…",14,true,ink);bg.addView(backgroundState);
        TextView bgHelp=text("Allow unrestricted background operation so Android does not pause kitchen printing while the screen is locked.",12,false,Color.DKGRAY);bgHelp.setPadding(0,dp(4),0,dp(8));bg.addView(bgHelp);
        Button allow=secondaryButton("ALLOW BACKGROUND RUNNING");bg.addView(allow);root.addView(bg);

        start=primaryButton("START BRIDGE");root.addView(start);
        stop=secondaryButton("STOP BRIDGE");LinearLayout.LayoutParams slp=(LinearLayout.LayoutParams)stop.getLayoutParams();slp.setMargins(0,dp(10),0,0);stop.setLayoutParams(slp);root.addView(stop);

        TextView footer=text("ESC/POS · TCP 9100 · Both printers receive the same bilingual skewer order",11,false,Color.GRAY);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(18),0,0);root.addView(footer);setContentView(sv);

        t1.setOnClickListener(v->test(1));t2.setOnClickListener(v->test(2));tb.setOnClickListener(v->{test(1);test(2);});start.setOnClickListener(v->startBridge());stop.setOnClickListener(v->stopBridge());allow.setOnClickListener(v->requestBackgroundAccess());updateBackgroundState();
    }

    private void requestBackgroundAccess(){try{if(Build.VERSION.SDK_INT>=23){Intent i=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName()));startActivity(i);}else toast("Background protection is not required on this Android version.");}catch(Exception e){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception ignored){}}}
    private void updateBackgroundState(){boolean ok=true;if(Build.VERSION.SDK_INT>=23){PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);ok=pm!=null&&pm.isIgnoringBatteryOptimizations(getPackageName());}if(backgroundState!=null){backgroundState.setText(ok?"✓ Background protection enabled":"⚠ Background protection recommended");backgroundState.setTextColor(ok?Color.rgb(42,120,72):Color.rgb(176,105,20));}}

    private void startBridge(){try{save();getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putBoolean("enabled",true).putString("status","Starting...").apply();Intent i=new Intent(this,BridgeService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception e){getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putBoolean("enabled",false).putString("status","Start failed: "+e.getMessage()).apply();toast("Start failed: "+e.getMessage());}}
    private void stopBridge(){try{Intent i=new Intent(this,BridgeService.class);i.setAction(BridgeService.ACTION_STOP);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception e){stopService(new Intent(this,BridgeService.class));}toast("Bridge stopped");}
    private void save(){int prt=9100;try{prt=Integer.parseInt(port.getText().toString().trim());}catch(Exception ignored){}getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putString("ip1",ip1.getText().toString().trim()).putString("ip2",ip2.getText().toString().trim()).putInt("port",prt).apply();}
    private void test(int which){save();String host=which==1?ip1.getText().toString().trim():ip2.getText().toString().trim();int prt=9100;try{prt=Integer.parseInt(port.getText().toString().trim());}catch(Exception ignored){}final int fp=prt;new Thread(()->{try{PrinterClient.printTest(host,fp,"PRINTER "+which);runOnUiThread(()->toast("Printer "+which+" OK"));}catch(Exception e){runOnUiThread(()->toast("Printer "+which+" failed: "+e.getMessage()));}}).start();}

    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setBackground(round(Color.WHITE,16,line));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(4));l.setLayoutParams(lp);l.setElevation(dp(1));return l;}
    private void section(LinearLayout root,String s){TextView v=text(s,12,true,Color.rgb(100,92,87));v.setLetterSpacing(.1f);v.setPadding(0,dp(18),0,dp(7));root.addView(v);}
    private TextView label(String s){TextView v=text(s,12,true,Color.DKGRAY);v.setPadding(0,dp(8),0,0);return v;}
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setTextSize(17);e.setTextColor(ink);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(round(Color.rgb(250,249,247),10,line));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.setMargins(0,dp(5),0,dp(4));e.setLayoutParams(lp);return e;}
    private Button smallButton(String s){Button b=secondaryButton(s);b.setTextSize(12);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(dp(3),0,dp(3),0);b.setLayoutParams(lp);return b;}
    private Button primaryButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setTypeface(null,Typeface.BOLD);b.setAllCaps(false);b.setBackground(round(burgundy,14,burgundy));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(0,dp(22),0,0);b.setLayoutParams(lp);return b;}
    private Button secondaryButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(ink);b.setTextSize(14);b.setTypeface(null,Typeface.BOLD);b.setAllCaps(false);b.setBackground(round(Color.WHITE,12,line));b.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(52)));return b;}
    private TextView text(String s,int size,boolean bold,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private GradientDrawable round(int fill,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private final Runnable refresh=new Runnable(){public void run(){SharedPreferences p=getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE);String s=p.getString("status",p.getBoolean("enabled",false)?"Starting...":"Stopped");if(statusText!=null)statusText.setText(s);boolean on=p.getBoolean("enabled",false);if(statusPill!=null){statusPill.setText(on?"● BRIDGE ON":"● STOPPED");statusPill.setTextColor(on?Color.rgb(42,120,72):Color.rgb(130,130,130));}if(start!=null)start.setEnabled(!on);if(stop!=null)stop.setEnabled(on);updateBackgroundState();h.postDelayed(this,1000);}};
    @Override protected void onDestroy(){h.removeCallbacks(refresh);super.onDestroy();}
}
