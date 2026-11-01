package com.photowebcam.pro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends Activity {
    private final int navy=Color.rgb(5,35,83), blue=Color.rgb(0,100,235), pale=Color.rgb(241,246,255), red=Color.rgb(238,35,45), purple=Color.rgb(86,62,235);
    private final ArrayList<Photo> photos=new ArrayList<>();
    private LinearLayout root, photoList;
    private ImageView preview;
    private TextView urlText, statusText, emptyText;
    private Photo selected;
    private boolean live=false;
    private ServerSocket server;
    private Thread serverThread;
    private static class Photo { Uri uri; boolean visible=true; Bitmap bitmap; Photo(Uri u, Bitmap b){uri=u;bitmap=b;} }

    @Override public void onCreate(Bundle b){ super.onCreate(b); buildUi(); refresh(); }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(String s,int size,int color,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); if(bold)t.setTypeface(null,Typeface.BOLD); return t; }
    private LinearLayout vertical(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private void pad(View v,int a){v.setPadding(dp(a),dp(a),dp(a),dp(a));}
    private Button button(String s,int color){ Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false); b.setTypeface(null,Typeface.BOLD); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color)); return b; }
    private void buildUi(){
        ScrollView sc=new ScrollView(this); sc.setBackgroundColor(pale); root=vertical(); root.setPadding(dp(12),dp(10),dp(12),dp(18)); sc.addView(root); setContentView(sc);
        LinearLayout head=horizontal(); head.setBackgroundColor(navy); head.setPadding(dp(14),dp(16),dp(14),dp(16)); TextView logo=text("📷",30,Color.WHITE,true); head.addView(logo); LinearLayout titles=vertical(); TextView title=text("Photo Webcam Pro",21,Color.WHITE,true); TextView sub=text("Photos to Webcam / OBS / SplitCam",12,Color.WHITE,false); titles.addView(title); titles.addView(sub); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1); tp.leftMargin=dp(12); head.addView(titles,tp); root.addView(head);
        preview=new ImageView(this); preview.setBackgroundColor(Color.rgb(218,226,240)); preview.setScaleType(ImageView.ScaleType.FIT_CENTER); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(260)); pp.topMargin=dp(12); preview.setLayoutParams(pp); root.addView(preview);
        LinearLayout liveTag=horizontal(); liveTag.setPadding(dp(8),dp(8),dp(8),dp(8)); liveTag.addView(text("●  LIVE DISPLAY",14,Color.rgb(0,140,60),true)); root.addView(liveTag);
        LinearLayout tabs=horizontal(); tabs.setBackgroundColor(Color.WHITE); tabs.setPadding(dp(8),dp(8),dp(8),dp(8)); TextView tab=text("▣  Photos",16,blue,true); tabs.addView(tab); TextView liveTab=text("       ◉  Live",16,navy,true); tabs.addView(liveTab); TextView info=text("       ⓘ  Info",16,navy,true); tabs.addView(info); root.addView(tabs);
        LinearLayout actions=horizontal(); actions.setPadding(0,dp(10),0,dp(8)); Button upload=button("＋ Upload Photo",blue); upload.setOnClickListener(v->pickPhotos()); actions.addView(upload,new LinearLayout.LayoutParams(0,dp(50),1)); Button show=button("Show All",purple); LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(50),1); ap.leftMargin=dp(5); actions.addView(show,ap); show.setOnClickListener(v->{for(Photo p:photos)p.visible=true;refresh();}); Button hide=button("Hide All",red); LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,dp(50),1); hp.leftMargin=dp(5); actions.addView(hide,hp); hide.setOnClickListener(v->{for(Photo p:photos)p.visible=false;refresh();}); root.addView(actions);
        emptyText=text("Upload photos to display them here",14,Color.GRAY,false); emptyText.setGravity(Gravity.CENTER); root.addView(emptyText);
        photoList=vertical(); root.addView(photoList);
        LinearLayout urlCard=vertical(); urlCard.setBackgroundColor(Color.WHITE); urlCard.setPadding(dp(12),dp(12),dp(12),dp(12)); LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(-1,-2); up.topMargin=dp(12); root.addView(urlCard,up); urlCard.addView(text("🔗  Webcam URL (For OBS / SplitCam)",15,navy,true)); urlText=text("Start Live to get URL",14,Color.DKGRAY,false); urlText.setPadding(0,dp(10),0,dp(8)); LinearLayout urlRow=horizontal(); urlRow.addView(urlText,new LinearLayout.LayoutParams(0,-2,1)); Button copy=button("Copy",blue); urlRow.addView(copy); copy.setOnClickListener(v->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(android.content.ClipData.newPlainText("Webcam URL",urlText.getText()));Toast.makeText(this,"URL copied",Toast.LENGTH_SHORT).show();}); urlCard.addView(urlRow); statusText=text("●  Live Stopped",13,red,true); urlCard.addView(statusText);
        LinearLayout controls=horizontal(); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(56)); cp.topMargin=dp(10); root.addView(controls,cp); Button start=button("▶  Start Live",blue); controls.addView(start,new LinearLayout.LayoutParams(0,-1,1)); start.setOnClickListener(v->startServer()); Button stop=button("■  Stop",red); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-1,1); sp.leftMargin=dp(8); controls.addView(stop,sp); stop.setOnClickListener(v->stopServer());
    }
    private void pickPhotos(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,44); }
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data); if(req!=44||result!=RESULT_OK||data==null)return; try{ if(data.getClipData()!=null){for(int n=0;n<data.getClipData().getItemCount();n++)addPhoto(data.getClipData().getItemAt(n).getUri());}else if(data.getData()!=null)addPhoto(data.getData()); refresh(); }catch(Exception e){Toast.makeText(this,"Photo could not be loaded",Toast.LENGTH_SHORT).show();}}
    private void addPhoto(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION); InputStream in=getContentResolver().openInputStream(u); Bitmap b=BitmapFactory.decodeStream(in); if(in!=null)in.close(); if(b!=null){Photo p=new Photo(u,b);photos.add(p);if(selected==null)selected=p;}}catch(Exception ignored){}}
    private void refresh(){ if(photoList==null)return; photoList.removeAllViews(); emptyText.setVisibility(photos.isEmpty()?View.VISIBLE:View.GONE); if(selected!=null&&selected.visible)preview.setImageBitmap(selected.bitmap); else {Photo first=null;for(Photo p:photos)if(p.visible){first=p;break;} selected=first; if(first!=null)preview.setImageBitmap(first.bitmap);else preview.setImageDrawable(null);}
        for(Photo p:photos){LinearLayout row=horizontal(); row.setBackgroundColor(Color.WHITE); row.setPadding(dp(7),dp(7),dp(7),dp(7)); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(94)); rp.bottomMargin=dp(6); photoList.addView(row,rp); ImageView thumb=new ImageView(this);thumb.setImageBitmap(p.bitmap);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);row.addView(thumb,new LinearLayout.LayoutParams(dp(76),dp(78))); TextView label=text(p.visible?"Photo  •  Shown":"Photo  •  Hidden",14,p.visible?blue:Color.GRAY,true); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(10);row.addView(label,lp); Button toggle=button(p.visible?"Hide":"Show",p.visible?red:blue);row.addView(toggle);toggle.setOnClickListener(v->{p.visible=!p.visible;if(!p.visible&&selected==p)selected=null;else if(p.visible&&selected==null)selected=p;refresh();}); thumb.setOnClickListener(v->{if(p.visible){selected=p;refresh();}});label.setOnClickListener(v->{if(p.visible){selected=p;refresh();}}); }
    }
    private String getIp(){try{ArrayList<NetworkInterface> all=Collections.list(NetworkInterface.getNetworkInterfaces());Collections.sort(all,(a,b)->Boolean.compare(isWifi(b.getName()),isWifi(a.getName())));for(NetworkInterface ni:all)for(java.net.InetAddress a:Collections.list(ni.getInetAddresses()))if(!a.isLoopbackAddress()&&a instanceof Inet4Address){String ip=a.getHostAddress();if(ip!=null&&!ip.startsWith("169.254"))return ip;}}catch(Exception ignored){}return "PHONE_IP";}
    private boolean isWifi(String n){return n!=null&&(n.startsWith("wlan")||n.startsWith("ap")||n.startsWith("swlan"));}
    private void startServer(){ if(live)return; boolean anyVisible=false;for(Photo p:photos)if(p.visible){anyVisible=true;break;}if(!anyVisible){Toast.makeText(this,"Pehle kam se kam ek photo Show karein",Toast.LENGTH_LONG).show();return;} try{server=new ServerSocket(8080);live=true;String url="http://"+getIp()+":8080/stream";urlText.setText(url);statusText.setText("●  Live Ready  •  URL ko OBS / SplitCam mein daalein");statusText.setTextColor(Color.rgb(0,140,60));serverThread=new Thread(()->{while(live){try{Socket client=server.accept();new Thread(()->serve(client)).start();}catch(Exception e){if(live)e.printStackTrace();}}});serverThread.start();Toast.makeText(this,"Live server started",Toast.LENGTH_SHORT).show();}catch(Exception e){live=false;Toast.makeText(this,"Port 8080 start nahi hua",Toast.LENGTH_LONG).show();}}
    private void serve(Socket s){try{ s.setSoTimeout(10000);OutputStream out=s.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\nCache-Control: no-cache\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n").getBytes("UTF-8"));out.flush();while(live&&!s.isClosed()){Photo p=selected;if(p==null||!p.visible){for(Photo x:photos)if(x.visible){p=x;break;}}if(p==null)break;ByteArrayOutputStream bytes=new ByteArrayOutputStream();p.bitmap.compress(Bitmap.CompressFormat.JPEG,88,bytes);byte[] jpg=bytes.toByteArray();out.write("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ".getBytes("UTF-8"));out.write(Integer.toString(jpg.length).getBytes("UTF-8"));out.write("\r\n\r\n".getBytes("UTF-8"));out.write(jpg);out.write("\r\n".getBytes("UTF-8"));out.flush();Thread.sleep(100);} }catch(Exception ignored){}finally{try{s.close();}catch(Exception ignored){}}}
    private void stopServer(){live=false;try{if(server!=null)server.close();}catch(Exception ignored){}urlText.setText("Start Live to get URL");statusText.setText("●  Live Stopped");statusText.setTextColor(red);}
    @Override protected void onDestroy(){stopServer();super.onDestroy();}
}
