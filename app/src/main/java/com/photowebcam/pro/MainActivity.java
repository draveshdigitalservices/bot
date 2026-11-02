package com.photowebcam.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
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
    private CanvasPreview preview;
    private ImageView sceneThumb;
    private SeekBar zoomSeek;
    private Spinner resolutionSpinner;
    private int outputWidth=960, outputHeight=540;
    private TextView zoomValue;
    private float canvasZoom=1f;
    private TextView urlText,statusText,sceneName,emptyText;
    private Photo selected;
    private volatile boolean live=false;
    private volatile Photo activePhoto;
    private final ExecutorService clientPool=Executors.newFixedThreadPool(4);
    private ServerSocket server;
    private Thread serverThread;
    private static class Photo {
        Uri uri; String name; volatile boolean visible=true, locked=false;
        Bitmap bitmap, baseBitmap; float zoom=1f;
        // Each photo keeps its own zoom AND pan position.
        final float[] transform=new float[]{1f,0f,0f, 0f,1f,0f, 0f,0f,1f};
        byte[] jpeg;
        Photo(Uri u,String n,Bitmap b,byte[] j){uri=u;name=n;bitmap=b;baseBitmap=b;jpeg=j;}
    }

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
        TextView resLabel=txt("RES:",10,cyan,true);
        previewHead.addView(resLabel);
        resolutionSpinner=new Spinner(this);
        String[] resolutions={"640x360","848x480","960x540","1280x720"};
        ArrayAdapter<String> resolutionAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,resolutions){
            @Override public android.view.View getView(int position,android.view.View convertView,android.view.ViewGroup parent){
                TextView v=(TextView)super.getView(position,convertView,parent);v.setTextColor(white);v.setTextSize(11);v.setPadding(dp(5),0,dp(5),0);return v;
            }
            @Override public android.view.View getDropDownView(int position,android.view.View convertView,android.view.ViewGroup parent){
                TextView v=(TextView)super.getDropDownView(position,convertView,parent);v.setTextColor(Color.BLACK);v.setTextSize(14);v.setPadding(dp(18),dp(8),dp(18),dp(8));
                v.setBackgroundColor(position==2?blue:Color.WHITE);if(position==2)v.setTextColor(Color.WHITE);return v;
            }
        };
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionSpinner.setAdapter(resolutionAdapter);
        resolutionSpinner.setSelection(2);
        LinearLayout.LayoutParams resSpinLp=new LinearLayout.LayoutParams(dp(112),dp(38));
        previewHead.addView(resolutionSpinner,resSpinLp);
        resolutionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                int[][] sizes={{640,360},{848,480},{960,540},{1280,720}};
                outputWidth=sizes[position][0];outputHeight=sizes[position][1];
                updateStreamFrame();
            }
            public void onNothingSelected(AdapterView<?> parent){}
        });
        previewCard.addView(previewHead);
        preview=new CanvasPreview();
        preview.setBackgroundColor(Color.rgb(2,8,20));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(260));
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
        zoomSeek.setMax(100);zoomSeek.setProgress(0);
        LinearLayout.LayoutParams seekp=new LinearLayout.LayoutParams(0,dp(44),1);
        seekp.leftMargin=dp(9);seekp.rightMargin=dp(9);
        zoomButtons.addView(zoomSeek,seekp);
        Button zoomIn=btn("+",blue);
        zoomButtons.addView(zoomIn,new LinearLayout.LayoutParams(dp(48),dp(44)));
        zoomCard.addView(zoomButtons);
        LinearLayout zoomFoot=horiz();
        zoomFoot.addView(txt("100%",10,muted,false),new LinearLayout.LayoutParams(0,-2,1));
        TextView maxZoom=txt("200%",10,muted,false);maxZoom.setGravity(Gravity.RIGHT);
        zoomFoot.addView(maxZoom);zoomCard.addView(zoomFoot);
        zoomOut.setOnClickListener(v->changeZoom(-0.02f));
        zoomIn.setOnClickListener(v->changeZoom(0.02f));
        zoomSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(android.widget.SeekBar bar,int progress,boolean fromUser){
                if(fromUser&&!photos.isEmpty()){
                    setCanvasZoom(1f+(progress/100f));
                    zoomValue.setText(Math.round(canvasZoom*100)+"%");
                    if(preview!=null&&selected!=null){preview.setPhoto(selected);updateStreamFrame();}
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
    private void addPhoto(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);String name="Photo "+(photos.size()+1);try(android.database.Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int ix=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(ix>=0)name=c.getString(ix);}}InputStream in=getContentResolver().openInputStream(u);Bitmap b=BitmapFactory.decodeStream(in);if(in!=null)in.close();if(b!=null){Bitmap scaled=scaleForStream(b,1280,720);ByteArrayOutputStream encoded=new ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.JPEG,82,encoded);Photo p=new Photo(u,name,scaled,encoded.toByteArray());photos.add(p);if(selected==null){selected=p;activePhoto=p;}if(preview!=null&&selected==p)preview.setPhoto(p);if(b!=scaled&&!b.isRecycled())b.recycle();}}catch(Exception ignored){}}
    // Zoom and pan are applied inside a fixed black 960x540 canvas.
    private void setCanvasZoom(float value){
        canvasZoom=Math.max(1f,Math.min(2f,value));
        if(preview!=null)preview.setZoom(canvasZoom);
        if(zoomValue!=null)zoomValue.setText(Math.round(canvasZoom*100)+"%");
        if(zoomSeek!=null){int progress=Math.round((canvasZoom-1f)*100);if(zoomSeek.getProgress()!=progress)zoomSeek.setProgress(progress);}
        updateStreamFrame();
    }
    private void changeZoom(float delta){
        if(selected==null){toast("Pehle photo add karein");return;}
        int currentPercent=Math.round(canvasZoom*100f);
        int nextPercent=Math.max(100,Math.min(200,currentPercent+Math.round(delta*100f)));
        setCanvasZoom(nextPercent/100f);refresh();
    }
    private void resetZoom(){
        if(selected==null){toast("Pehle photo add karein");return;}
        canvasZoom=1f;
        if(preview!=null)preview.resetTransform();
        if(zoomSeek!=null)zoomSeek.setProgress(0);
        if(zoomValue!=null)zoomValue.setText("100%");
        updateStreamFrame();refresh();
    }
    private void updateStreamFrame(){
        if(preview==null||selected==null)return;
        byte[] frame=preview.renderJpeg(outputWidth,outputHeight);
        if(frame!=null){selected.jpeg=frame;selected.bitmap=preview.getPhotoBitmap();}
        activePhoto=(selected!=null&&selected.visible)?selected:null;
    }

    private class CanvasPreview extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Paint selectionPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap photoBitmap;
        private Photo currentPhoto;
        private final Matrix matrix=new Matrix();
        private float zoom=1f, lastX,lastY, startDist=0f, startZoom=1f;
        private float baseLeft,baseTop,baseW,baseH;
        private boolean dragging=false;
        private boolean selectionVisible=false;
        CanvasPreview(){
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            selectionPaint.setColor(Color.rgb(0,145,255));
            selectionPaint.setStyle(Paint.Style.STROKE);
            selectionPaint.setStrokeWidth(dp(2));
        }
        private void saveCurrentTransform(){
            if(currentPhoto==null)return;
            currentPhoto.zoom=zoom;
            matrix.getValues(currentPhoto.transform);
        }
        void setPhoto(Photo p){
            if(currentPhoto==p)return;
            // Save the outgoing photo before changing the canvas.
            saveCurrentTransform();
            currentPhoto=p;
            photoBitmap=(p!=null&&p.visible)?p.baseBitmap:null;
            selectionVisible=false;
            if(p!=null){
                zoom=Math.max(1f,Math.min(2f,p.zoom));
                matrix.setValues(p.transform);
            }else{
                zoom=1f;
                matrix.reset();
            }
            canvasZoom=zoom;
            invalidate();
            if(zoomSeek!=null)zoomSeek.setProgress(Math.round((zoom-1f)*100));
            if(zoomValue!=null)zoomValue.setText(Math.round(zoom*100)+"%");
            updateStreamFrame();
        }
        Bitmap getPhotoBitmap(){return photoBitmap;}
        void setZoom(float z){
            float next=Math.max(1f,Math.min(2f,z));
            float factor=next/zoom;zoom=next;
            float cx=getWidth()/2f,cy=getHeight()/2f;
            matrix.postScale(factor,factor,cx,cy);
            canvasZoom=zoom;saveCurrentTransform();invalidate();
        }
        void resetTransform(){zoom=1f;matrix.reset();canvasZoom=1f;saveCurrentTransform();invalidate();}
        private float distance(MotionEvent e){float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);return (float)Math.sqrt(dx*dx+dy*dy);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);c.drawColor(Color.rgb(2,8,20));
            if(photoBitmap==null||photoBitmap.isRecycled())return;
            float scale=Math.min((float)getWidth()/photoBitmap.getWidth(),(float)getHeight()/photoBitmap.getHeight());
            baseW=photoBitmap.getWidth()*scale;baseH=photoBitmap.getHeight()*scale;
            baseLeft=(getWidth()-baseW)/2f;baseTop=(getHeight()-baseH)/2f;
            c.save();c.concat(matrix);
            RectF dst=new RectF(baseLeft,baseTop,baseLeft+baseW,baseTop+baseH);
            c.drawBitmap(photoBitmap,null,dst,paint);
            // Show a blue four-sided selection frame after the user taps the photo.
            if(selectionVisible){
                c.drawRect(dst,selectionPaint);
                Paint handles=new Paint(Paint.ANTI_ALIAS_FLAG);
                handles.setColor(Color.WHITE);
                handles.setStyle(Paint.Style.FILL);
                float r=dp(4);
                c.drawCircle(dst.left,dst.top,r,handles);
                c.drawCircle(dst.right,dst.top,r,handles);
                c.drawCircle(dst.left,dst.bottom,r,handles);
                c.drawCircle(dst.right,dst.bottom,r,handles);
                handles.setColor(Color.rgb(0,145,255));
                handles.setStyle(Paint.Style.STROKE);
                handles.setStrokeWidth(dp(1.5f));
                c.drawCircle(dst.left,dst.top,r,handles);
                c.drawCircle(dst.right,dst.top,r,handles);
                c.drawCircle(dst.left,dst.bottom,r,handles);
                c.drawCircle(dst.right,dst.bottom,r,handles);
            }
            c.restore();
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(photoBitmap==null)return true;
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    selectionVisible=true;
                    invalidate();
                    lastX=e.getX();lastY=e.getY();dragging=true;return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if(e.getPointerCount()>=2){startDist=distance(e);startZoom=zoom;}return true;
                case MotionEvent.ACTION_MOVE:
                    if(e.getPointerCount()>=2&&startDist>0){
                        float d=distance(e);float target=Math.max(1f,Math.min(2f,startZoom*(d/startDist)));
                        float factor=target/zoom;zoom=target;
                        matrix.postScale(factor,factor,getWidth()/2f,getHeight()/2f);
                        canvasZoom=zoom;
                        if(zoomSeek!=null)zoomSeek.setProgress(Math.round((zoom-1f)*100));
                        if(zoomValue!=null)zoomValue.setText(Math.round(zoom*100)+"%");
                    }else if(dragging){
                        float x=e.getX(),y=e.getY();matrix.postTranslate(x-lastX,y-lastY);lastX=x;lastY=y;
                    }
                    saveCurrentTransform();
                    invalidate();updateStreamFrame();return true;
                case MotionEvent.ACTION_POINTER_UP:
                    startDist=0;return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging=false;startDist=0;performClick();updateStreamFrame();return true;
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        byte[] renderJpeg(int w,int h){
            if(photoBitmap==null)return null;
            Bitmap output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(output);c.drawColor(Color.rgb(2,8,20));
            float scale=Math.min((float)w/photoBitmap.getWidth(),(float)h/photoBitmap.getHeight());
            float bw=photoBitmap.getWidth()*scale,bh=photoBitmap.getHeight()*scale;
            float left=(w-bw)/2f,top=(h-bh)/2f;
            float sx=(float)w/Math.max(1,getWidth()),sy=(float)h/Math.max(1,getHeight());
            c.save();c.scale(sx,sy);
            c.concat(matrix);
            c.drawBitmap(photoBitmap,null,new RectF(left/sx,top/sy,(left+bw)/sx,(top+bh)/sy),paint);
            c.restore();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();output.compress(Bitmap.CompressFormat.JPEG,88,bytes);output.recycle();return bytes.toByteArray();
        }
    }
    private void ensureSelection(){if(selected==null||!selected.visible){selected=null;for(Photo p:photos)if(p.visible){selected=p;break;}}syncActive();}
    private void syncActive(){activePhoto=(selected!=null&&selected.visible)?selected:null;}
    private void refresh(){if(layerList==null)return;ensureSelection();layerList.removeAllViews();emptyText.setVisibility(photos.isEmpty()?View.VISIBLE:View.GONE);if(selected!=null){if(sceneThumb!=null)sceneThumb.setImageBitmap(selected.bitmap);if(preview!=null)preview.setPhoto(selected);if(zoomSeek!=null)zoomSeek.setProgress(Math.max(0,Math.min(100,Math.round((canvasZoom-1f)*100))));if(zoomValue!=null)zoomValue.setText(Math.round(canvasZoom*100)+"%");}else{if(sceneThumb!=null)sceneThumb.setImageDrawable(null);if(preview!=null)preview.setPhoto(null);if(zoomSeek!=null)zoomSeek.setProgress(0);if(zoomValue!=null)zoomValue.setText(Math.round(canvasZoom*100)+"%");}
        for(Photo p:new ArrayList<>(photos)){LinearLayout row=horiz();row.setBackgroundColor(panel2);row.setPadding(dp(3),dp(3),dp(3),dp(3));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(58));rp.bottomMargin=dp(6);layerList.addView(row,rp);
            TextView type=txt("▧",21,cyan,true);type.setGravity(Gravity.CENTER);row.addView(type,new LinearLayout.LayoutParams(dp(30),-1));TextView name=txt(p.name,12,white,true);name.setSingleLine(true);name.setPadding(dp(5),0,dp(2),0);name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-1,1);np.leftMargin=dp(3);name.setGravity(Gravity.CENTER_VERTICAL);name.setHintTextColor(muted);row.addView(name,np);
            // Tapping the photo/name selects that photo. Rename is handled only by the pencil button.
            name.setOnClickListener(v->{if(p.visible){selected=p;syncActive();refresh();}});
            Button edit=btn("✏",blue);LinearLayout.LayoutParams editLp=new LinearLayout.LayoutParams(dp(34),dp(42));editLp.leftMargin=dp(3);row.addView(edit,editLp);edit.setOnClickListener(v->renamePhoto(p));
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
        input.setSelectAllOnFocus(true);
        input.setHint("Enter layer name");
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Rename Layer")
            .setMessage("Naya naam type karein. Purana naam pehle se select hai.")
            .setView(input)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Save",(d,w)->{
                String value=input.getText().toString().trim();
                if(value.isEmpty()){toast("Name khali nahi ho sakta");return;}
                p.name=value;
                refresh();
                toast("Layer name updated");
            }).create();
        dialog.setOnShowListener(d->{
            input.requestFocus();
            input.selectAll();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null)input.postDelayed(()->{input.selectAll();imm.showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);},120);
        });
        dialog.show();
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
