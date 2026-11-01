package com.photowebcam.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int bg=Color.rgb(3,12,35), panel=Color.rgb(5,28,67), panel2=Color.rgb(9,45,99), blue=Color.rgb(0,125,255), cyan=Color.rgb(0,210,255), green=Color.rgb(0,190,65), red=Color.rgb(235,35,55), white=Color.WHITE, muted=Color.rgb(170,195,230);
    private final ArrayList<Photo> photos=new ArrayList<>();
    private LinearLayout root, layerList;
    private ImageView preview, sceneThumb;
    private SeekBar zoomSeek;
    private TextView zoomValue;
    private float canvasZoom=1f;
    private TextView urlText,statusText,sceneName,emptyText;
    private Photo selected;
    private volatile boolean live=false;
    private volatile Photo activePhoto;
    private final ExecutorService clientPool=Executors.newFixedThreadPool(4);
    private ServerSocket server;
    private Thread serverThread;
    private static class Photo { Uri uri; String name; volatile boolean visible=true, locked=false; Bitmap bitmap, baseBitmap; float zoom=1f; byte[] jpeg; Photo(Uri u,String n,Bitmap b,byte[] j){uri=u;name=n;bitmap=b;baseBitmap=b;jpeg=j;} }

    @Override public void onCreate(Bundle b){super.onCreate(b);buildUi();refresh();}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private TextView txt(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private LinearLayout vert(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horiz(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private Button btn(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(white);b.setTextSize(13);b.setAllCaps(false);b.setTypeface(null,Typeface.BOLD);b.setPadding(dp(3),0,dp(3),0);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));return b;}
    private void buildUi(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        root=vert();
        root.setPadding(dp(12),dp(14),dp(12),dp(20));
        scroll.addView(root);
        setContentView(scroll);

        // Premium header
        LinearLayout header=horiz();
        header.setBackgroundColor(panel);
        header.setPadding(dp(14),dp(13),dp(14),dp(13));
        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.webcam_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout titles=vert();
        TextView title=txt("WebCam Pro",21,white,true);
        TextView sub=txt("CAMERA CONTROL CENTER",10,cyan,true);
        titles.addView(title);titles.addView(sub);
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,1);
        tl.leftMargin=dp(11);header.addView(titles,tl);
        TextView settings=txt("⚙",26,white,true);
        header.addView(settings);
        root.addView(header);

        LinearLayout liveBadge=horiz();
        liveBadge.setPadding(dp(4),dp(11),dp(4),dp(5));
        liveBadge.addView(txt("●",12,red,true));
        liveBadge.addView(txt("  WORKSPACE  /  IMAGE CONTROLS",10,muted,true));
        root.addView(liveBadge);

        // Preview panel
        LinearLayout previewCard=vert();
        previewCard.setBackgroundColor(panel);
        previewCard.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.topMargin=dp(5);root.addView(previewCard,cp);
        LinearLayout previewHead=horiz();
        previewHead.addView(txt("LIVE PREVIEW",14,white,true),new LinearLayout.LayoutParams(0,dp(34),1));
        TextView res=txt("960 × 540",10,cyan,true);
        previewHead.addView(res);
        previewCard.addView(previewHead);
        preview=new ImageView(this);
        preview.setBackgroundColor(Color.rgb(2,8,20));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(220));
        pp.topMargin=dp(5);previewCard.addView(preview,pp);
        TextView previewHint=txt("Select a media layer to edit its preview",10,muted,false);
        previewHint.setGravity(Gravity.CENTER);
        previewCard.addView(previewHint);

        // Zoom control panel
        LinearLayout zoomCard=vert();
        zoomCard.setBackgroundColor(panel);
        zoomCard.setPadding(dp(13),dp(12),dp(13),dp(12));
        LinearLayout.LayoutParams zp=new LinearLayout.LayoutParams(-1,-2);
        zp.topMargin=dp(11);root.addView(zoomCard,zp);
        LinearLayout zoomHead=horiz();
        zoomHead.addView(txt("⌕  ZOOM CONTROL",14,white,true),new LinearLayout.LayoutParams(0,dp(34),1));
        zoomValue=txt("100%",13,cyan,true);
        zoomValue.setGravity(Gravity.CENTER);
        zoomHead.addView(zoomValue);
        zoomCard.addView(zoomHead);
        LinearLayout zoomButtons=horiz();
        Button zoomOut=btn("−",panel2);
        zoomButtons.addView(zoomOut,new LinearLayout.LayoutParams(dp(48),dp(44)));
        zoomSeek=new android.widget.SeekBar(this);
        zoomSeek.setMax(200);zoomSeek.setProgress(0);
        LinearLayout.LayoutParams seekp=new LinearLayout.LayoutParams(0,dp(44),1);
        seekp.leftMargin=dp(9);seekp.rightMargin=dp(9);
        zoomButtons.addView(zoomSeek,seekp);
        Button zoomIn=btn("+",blue);
        zoomButtons.addView(zoomIn,new LinearLayout.LayoutParams(dp(48),dp(44)));
        zoomCard.addView(zoomButtons);
        LinearLayout zoomFoot=horiz();
        zoomFoot.addView(txt("100%",10,muted,false),new LinearLayout.LayoutParams(0,-2,1));
        TextView maxZoom=txt("300%",10,muted,false);maxZoom.setGravity(Gravity.RIGHT);
        zoomFoot.addView(maxZoom);zoomCard.addView(zoomFoot);
        zoomOut.setOnClickListener(v->changeZoom(-0.25f));
        zoomIn.setOnClickListener(v->changeZoom(0.25f));
        zoomSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(android.widget.SeekBar bar,int progress,boolean fromUser){
                if(fromUser&&!photos.isEmpty()){
                    setCanvasZoom(1f+(progress/100f));
                    zoomValue.setText(Math.round(canvasZoom*100)+"%");
                    if(preview!=null&&selected!=null)preview.setImageBitmap(selected.bitmap);
                }
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar){}
            public void onStopTrackingTouch(android.widget.SeekBar bar){if(selected!=null)refresh();}
        });
        Button reset=btn("↺  RESET ZOOM",panel2);
        LinearLayout.LayoutParams resetp=new LinearLayout.LayoutParams(-1,dp(42));
        resetp.topMargin=dp(8);zoomCard.addView(reset,resetp);
        reset.setOnClickListener(v->{resetZoom();zoomSeek.setProgress(0);zoomValue.setText("100%");});

        // Media layers panel
        LinearLayout layers=vert();
        layers.setBackgroundColor(panel);
        layers.setPadding(dp(12),dp(11),dp(12),dp(12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(11);root.addView(layers,lp);
        LinearLayout layerHead=horiz();
        TextView layerTitle=txt("MEDIA LAYERS",14,white,true);
        layerHead.addView(layerTitle,new LinearLayout.LayoutParams(0,dp(42),1));
        Button add=btn("＋  ADD PHOTO",blue);
        layerHead.addView(add,new LinearLayout.LayoutParams(dp(132),dp(40)));
        add.setOnClickListener(v->pickPhotos());
        layers.addView(layerHead);
        layerList=vert();
        LinearLayout.LayoutParams listp=new LinearLayout.LayoutParams(-1,-2);
        listp.topMargin=dp(7);layers.addView(layerList,listp);
        emptyText=txt("No media yet. Add a photo to get started.",11,muted,false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(0,dp(16),0,dp(16));
        layers.addView(emptyText);

        // Stream connection panel
        LinearLayout urlCard=vert();
        urlCard.setBackgroundColor(panel);
        urlCard.setPadding(dp(12),dp(11),dp(12),dp(11));
        LinearLayout.LayoutParams ucp=new LinearLayout.LayoutParams(-1,-2);
        ucp.topMargin=dp(11);root.addView(urlCard,ucp);
        urlCard.addView(txt("STREAM CONNECTION",14,white,true));
        LinearLayout urlRow=horiz();
        urlText=txt("Start Live to get URL",11,muted,false);
        urlText.setSingleLine(true);
        urlText.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        urlRow.addView(urlText,new LinearLayout.LayoutParams(0,dp(42),1));
        Button copy=btn("COPY",panel2);urlRow.addView(copy);
        copy.setOnClickListener(v->{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Webcam URL",urlText.getText()));Toast.makeText(this,"URL copied",Toast.LENGTH_SHORT).show();});
        urlCard.addView(urlRow);
        statusText=txt("●  LIVE STOPPED",11,red,true);
        urlCard.addView(statusText);

        LinearLayout stream=horiz();
        LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,dp(50));
        stp.topMargin=dp(11);root.addView(stream,stp);
        Button start=btn("▶  START LIVE",blue);
        stream.addView(start,new LinearLayout.LayoutParams(0,-1,1));
        start.setOnClickListener(v->startServer());
        Button stop=btn("■  STOP",red);
        LinearLayout.LayoutParams stopP=new LinearLayout.LayoutParams(0,-1,1);
        stopP.leftMargin=dp(8);stream.addView(stop,stopP);
        stop.setOnClickListener(v->stopServer());
    }
    private void pickPhotos(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,44);}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=44||result!=RESULT_OK||data==null)return;try{if(data.getClipData()!=null){for(int n=0;n<data.getClipData().getItemCount();n++)addPhoto(data.getClipData().getItemAt(n).getUri());}else if(data.getData()!=null)addPhoto(data.getData());refresh();}catch(Exception e){Toast.makeText(this,"Photo could not be loaded",Toast.LENGTH_SHORT).show();}}
    private void addPhoto(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);String name="Photo "+(photos.size()+1);try(android.database.Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int ix=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(ix>=0)name=c.getString(ix);}}InputStream in=getContentResolver().openInputStream(u);Bitmap b=BitmapFactory.decodeStream(in);if(in!=null)in.close();if(b!=null){Bitmap scaled=scaleForStream(b,1280,720);ByteArrayOutputStream encoded=new ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.JPEG,82,encoded);Photo p=new Photo(u,name,scaled,encoded.toByteArray());photos.add(p);if(selected==null){selected=p;activePhoto=p;}if(canvasZoom>1f){p.zoom=canvasZoom;renderZoom(p);}if(b!=scaled&&!b.isRecycled())b.recycle();}}catch(Exception ignored){}}
    // One canvas zoom factor is applied to every media layer, not just the selected photo.
    private void setCanvasZoom(float value){
        canvasZoom=Math.max(1f,Math.min(3f,value));
        for(Photo photo:photos){photo.zoom=canvasZoom;renderZoom(photo);}
        if(zoomValue!=null)zoomValue.setText(Math.round(canvasZoom*100)+"%");
        if(zoomSeek!=null){int progress=Math.round((canvasZoom-1f)*100);if(zoomSeek.getProgress()!=progress)zoomSeek.setProgress(progress);}
        syncActive();
    }
    private void changeZoom(float delta){
        if(photos.isEmpty()){toast("Pehle photo add karein");return;}
        float next=Math.max(1f,Math.min(3f,canvasZoom+delta));
        if(Math.abs(next-canvasZoom)<0.001f){toast(next>=3f?"Maximum zoom 3x":"Minimum zoom 1x");return;}
        setCanvasZoom(next);refresh();toast("Full canvas zoom "+String.format(java.util.Locale.US,"%.2fx",next));
    }
    private void resetZoom(){if(photos.isEmpty()){toast("Pehle photo add karein");return;}setCanvasZoom(1f);refresh();toast("Full canvas reset 1x");}
    private void renderZoom(Photo p){
        try{
            Bitmap base=p.baseBitmap;int w=base.getWidth(),h=base.getHeight();
            int cw=Math.max(1,(int)(w/p.zoom)),ch=Math.max(1,(int)(h/p.zoom));
            int left=(w-cw)/2,top=(h-ch)/2;
            Bitmap crop=Bitmap.createBitmap(base,left,top,cw,ch);
            Bitmap output=Bitmap.createScaledBitmap(crop,w,h,true);if(crop!=base&&!crop.isRecycled())crop.recycle();
            ByteArrayOutputStream encoded=new ByteArrayOutputStream();output.compress(Bitmap.CompressFormat.JPEG,85,encoded);
            Bitmap old=p.bitmap;p.bitmap=output;p.jpeg=encoded.toByteArray();if(old!=null&&old!=base&&!old.isRecycled())old.recycle();
        }catch(Exception e){toast("Zoom apply nahi ho paya");}
    }
    private void ensureSelection(){if(selected==null||!selected.visible){selected=null;for(Photo p:photos)if(p.visible){selected=p;break;}}syncActive();}
    private void syncActive(){activePhoto=(selected!=null&&selected.visible)?selected:null;}
    private void refresh(){if(layerList==null)return;ensureSelection();layerList.removeAllViews();emptyText.setVisibility(photos.isEmpty()?View.VISIBLE:View.GONE);if(selected!=null){if(sceneThumb!=null)sceneThumb.setImageBitmap(selected.bitmap);if(preview!=null)preview.setImageBitmap(selected.bitmap);if(zoomSeek!=null)zoomSeek.setProgress(Math.max(0,Math.min(200,Math.round((canvasZoom-1f)*100))));if(zoomValue!=null)zoomValue.setText(Math.round(canvasZoom*100)+"%");}else{if(sceneThumb!=null)sceneThumb.setImageDrawable(null);if(preview!=null)preview.setImageDrawable(null);if(zoomSeek!=null)zoomSeek.setProgress(0);if(zoomValue!=null)zoomValue.setText(Math.round(canvasZoom*100)+"%");}
        for(Photo p:new ArrayList<>(photos)){LinearLayout row=horiz();row.setBackgroundColor(panel2);row.setPadding(dp(3),dp(3),dp(3),dp(3));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(58));rp.bottomMargin=dp(6);layerList.addView(row,rp);
            TextView type=txt("▧",21,cyan,true);type.setGravity(Gravity.CENTER);row.addView(type,new LinearLayout.LayoutParams(dp(30),-1));TextView name=txt(p.name,12,white,true);name.setSingleLine(true);name.setPadding(dp(5),0,dp(2),0);name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-1,1);np.leftMargin=dp(3);name.setGravity(Gravity.CENTER_VERTICAL);name.setHintTextColor(muted);row.addView(name,np);
            name.setOnClickListener(v->renamePhoto(p));
            Button lock=btn(p.locked?"🔒":"🔓",p.locked?Color.rgb(80,95,125):panel);row.addView(lock,new LinearLayout.LayoutParams(dp(34),dp(42)));lock.setOnClickListener(v->{p.locked=!p.locked;refresh();});
            Button eye=btn(p.visible?"👁":"🚫",p.visible?green:Color.rgb(70,80,100));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(dp(36),dp(42));ep.leftMargin=dp(3);row.addView(eye,ep);eye.setOnClickListener(v->{if(p.locked){toast("Layer locked hai. Pehle unlock karein.");return;}p.visible=!p.visible;if(!p.visible&&selected==p)selected=null;else if(p.visible&&selected==null)selected=p;syncActive();refresh();});
            Button del=btn("🗑",red);LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(dp(36),dp(42));dp1.leftMargin=dp(3);row.addView(del,dp1);del.setOnClickListener(v->deletePhoto(p));
            row.setOnClickListener(v->{if(p.visible){selected=p;syncActive();refresh();}});
        }
    }
    private void renamePhoto(Photo p){
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setText(p.name);
        input.setSelection(input.getText().length());
        input.setHint("Enter layer name");
        new AlertDialog.Builder(this)
            .setTitle("Rename Layer")
            .setMessage("Photo ka naya naam likhein")
            .setView(input)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Save",(d,w)->{
                String value=input.getText().toString().trim();
                if(value.isEmpty()){toast("Name khali nahi ho sakta");return;}
                p.name=value;
                refresh();
                toast("Layer name updated");
            }).show();
    }
    private void deletePhoto(Photo p){if(p.locked){toast("Layer locked hai. Delete karne se pehle unlock karein.");return;}new AlertDialog.Builder(this).setTitle("Delete Layer?").setMessage(p.name+" ko delete karna hai?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{photos.remove(p);if(selected==p)selected=null;syncActive();refresh();}).show();}
    private void confirmDeleteAll(){new AlertDialog.Builder(this).setTitle("Delete All Layers?").setMessage("Sirf unlocked layers delete hongi. Locked layers safe rahengi.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{for(int i=photos.size()-1;i>=0;i--)if(!photos.get(i).locked)photos.remove(i);if(selected!=null&&!photos.contains(selected))selected=null;syncActive();refresh();}).show();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private String getIp(){try{ArrayList<NetworkInterface> all=Collections.list(NetworkInterface.getNetworkInterfaces());Collections.sort(all,(a,b)->Boolean.compare(isWifi(b.getName()),isWifi(a.getName())));for(NetworkInterface ni:all)for(java.net.InetAddress a:Collections.list(ni.getInetAddresses()))if(!a.isLoopbackAddress()&&a instanceof Inet4Address){String ip=a.getHostAddress();if(ip!=null&&!ip.startsWith("169.254"))return ip;}}catch(Exception ignored){}return "PHONE_IP";}
    private boolean isWifi(String n){return n!=null&&(n.startsWith("wlan")||n.startsWith("ap")||n.startsWith("swlan"));}
    private void startServer(){if(live)return;ensureSelection();if(activePhoto==null){toast("Pehle kam se kam ek layer Show karein");return;}try{server=new ServerSocket(8080);live=true;String url="http://"+getIp()+":8080/stream";urlText.setText(url);statusText.setText("● Live Ready  •  OBS / SplitCam mein URL daalein");statusText.setTextColor(green);serverThread=new Thread(()->{while(live){try{Socket client=server.accept();clientPool.execute(()->serve(client));}catch(Exception e){if(live)e.printStackTrace();}}},"webcam-accept");serverThread.start();toast("Live server started");}catch(Exception e){live=false;toast("Port 8080 start nahi hua");}}
    private Bitmap scaleForStream(Bitmap source,int maxW,int maxH){int w=source.getWidth(),h=source.getHeight();float ratio=Math.min(1f,Math.min((float)maxW/w,(float)maxH/h));if(ratio>=1f)return source;return Bitmap.createScaledBitmap(source,Math.max(1,(int)(w*ratio)),Math.max(1,(int)(h*ratio)),true);}
    private void serve(Socket s){try{s.setSoTimeout(30000);s.setTcpNoDelay(true);s.setKeepAlive(true);OutputStream out=s.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\nCache-Control: no-cache, no-store\r\nPragma: no-cache\r\nConnection: keep-alive\r\nAccess-Control-Allow-Origin: *\r\n\r\n").getBytes("UTF-8"));out.flush();while(live&&!s.isClosed()){Photo p=activePhoto;if(p!=null&&p.visible&&p.jpeg!=null){byte[] jpg=p.jpeg;out.write("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ".getBytes("UTF-8"));out.write(Integer.toString(jpg.length).getBytes("UTF-8"));out.write("\r\n\r\n".getBytes("UTF-8"));out.write(jpg);out.write("\r\n".getBytes("UTF-8"));out.flush();}Thread.sleep(66);}}catch(Exception ignored){}finally{try{s.close();}catch(Exception ignored){}}}
    private void stopServer(){live=false;activePhoto=null;try{if(server!=null)server.close();}catch(Exception ignored){}if(urlText!=null)urlText.setText("Start Live to get URL");if(statusText!=null){statusText.setText("● Live Stopped");statusText.setTextColor(red);}}
    @Override protected void onDestroy(){stopServer();clientPool.shutdownNow();super.onDestroy();}
}
