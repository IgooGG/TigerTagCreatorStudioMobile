package io.tigertagcreator.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final String ASSET_HOST="tigertag.local";
    private static final int OPEN_FILE=41, SAVE_FILE=42;
    private final ExecutorService worker=Executors.newCachedThreadPool();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean scanning=new AtomicBoolean(false);
    private WebView web;
    private NfcAdapter adapter;
    private CatalogueStore catalogue;
    private Pending pending;
    private ValueCallback<Uri[]> chooser;
    private String saveId;
    private byte[] saveBytes;
    private static final class Pending {final String id,action;final byte[] payload;Pending(String i,String a,byte[] p){id=i;action=a;payload=p;}}

    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(0xff111217);getWindow().setNavigationBarColor(0xff111217);
        catalogue=new CatalogueStore(this);adapter=NfcAdapter.getDefaultAdapter(this);web=new WebView(this);setContentView(web);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(),"TigerBridge");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !ASSET_HOST.equalsIgnoreCase(request.getUrl().getHost());}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri u=request.getUrl();if(!ASSET_HOST.equalsIgnoreCase(u.getHost()))return null;
                String path=u.getPath();if(path==null||path.equals("/"))path="/editor.html";
                String file=path.substring(1);if(!file.equals("editor.html")&&!file.equals("icon-192.png")&&!file.equals("icon-256.png"))return new WebResourceResponse("text/plain","utf-8",new java.io.ByteArrayInputStream(new byte[0]));
                try{InputStream stream=getAssets().open(file);return new WebResourceResponse(file.endsWith(".html")?"text/html":"image/png",file.endsWith(".html")?"utf-8":null,stream);}catch(Exception e){return null;}
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("Write",(d,w)->result.confirm()).setNegativeButton("Cancel",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;}
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){if(chooser!=null)chooser.onReceiveValue(null);chooser=callback;try{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(intent,OPEN_FILE);return true;}catch(Exception e){chooser=null;callback.onReceiveValue(null);return false;}}
        });
        web.loadUrl("https://"+ASSET_HOST+"/editor.html");
    }
    @Override protected void onResume(){super.onResume();if(adapter!=null&&adapter.isEnabled())adapter.enableReaderMode(this,this::onTag,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,null);}
    @Override protected void onPause(){if(adapter!=null)try{adapter.disableReaderMode(this);}catch(Exception ignored){}super.onPause();}
    @Override protected void onDestroy(){worker.shutdownNow();if(web!=null){web.removeJavascriptInterface("TigerBridge");web.destroy();}super.onDestroy();}
    private void reply(String id,JSONObject data){main.post(()->{if(web!=null)web.evaluateJavascript("window.__nativeReply("+JSONObject.quote(id)+","+data.toString()+")",null);});}
    private void error(String id,String message){try{reply(id,new JSONObject().put("error",message));}catch(Exception ignored){}}
    private void notifyStatus(String text){main.post(()->{if(web!=null)web.evaluateJavascript("window.__nativeStatus("+JSONObject.quote(text)+")",null);});}
    private synchronized Pending takePending(){Pending p=pending;pending=null;return p;}
    private synchronized void queue(Pending p){if(pending!=null){error(p.id,"Another NFC operation is already waiting for a tag.");return;}pending=p;notifyStatus("Hold the tag against the back of your phone until the operation finishes.");main.postDelayed(()->{synchronized(MainActivity.this){if(pending==p){pending=null;error(p.id,"No tag detected. Choose the operation again and hold the tag against the phone.");}}},60000);}
    private void onTag(Tag tag){if(!scanning.compareAndSet(false,true))return;Pending p=takePending();if(p==null){scanning.set(false);return;}
        try{if(tag.getId()==null||tag.getId().length!=7)throw new Exception("Expected a seven-byte UID NTAG Type 2 tag.");NfcA nfc=NfcA.get(tag);if(nfc==null)throw new Exception("This tag does not support NFC-A.");
            nfc.connect();try{nfc.setTimeout(2500);TagIo.Transport t=nfc::transceive;
                if(p.action.equals("read")){byte[] raw=TagIo.read(t);String info="UID: "+TigerCodec.hex(tag.getId())+"\n";JSONObject result=new JSONObject().put("raw",TigerCodec.hex(raw)).put("uid",TigerCodec.hex(tag.getId()));try{String[] f=TigerCodec.decode(raw);info+=(TigerCodec.u(raw,0,4)==0xbc0fcb97L?"TigerTag+":"TigerTag")+" · "+f[1]+" · "+f[0]+"\nNozzle: "+f[6]+"–"+f[7]+" °C  ·  Bed: "+f[8]+"–"+f[9]+" °C\nDrying: "+f[10]+" °C for "+f[11]+" h";result.put("fields",new JSONArray(Arrays.asList(f)));}catch(Exception ex){info+="This tag does not contain a supported TigerTag filament record.";result.put("fields",JSONObject.NULL);}result.put("message",info);reply(p.id,result);
                }else{TagIo.write(t,p.payload);reply(p.id,new JSONObject().put("message","Write verified on tag "+TigerCodec.hex(tag.getId())+". Standard unsigned TigerTag created."));}
            }finally{nfc.close();}
        }catch(Exception e){error(p.id,e.getMessage()==null?"NFC operation failed.":e.getMessage());}finally{scanning.set(false);}
    }
    private final class Bridge {
        @JavascriptInterface public void request(String id,String action,String text){
            if(id==null||id.length()>32||action==null||text==null||text.length()>100000)return;
            worker.execute(()->{try{JSONObject data=new JSONObject(text);switch(action){
                case "catalog-progress":reply(id,catalogue.progress());break;
                case "catalog":reply(id,catalogue.search(data));break;
                case "decode":reply(id,TigerCodec.decoded(TigerCodec.unhex(data.optString("data",""))));break;
                case "encode":reply(id,TigerCodec.preview(data));break;
                case "read":case "write":
                    if(adapter==null)throw new Exception("This phone does not have NFC.");if(!adapter.isEnabled())throw new Exception("Turn on NFC in your phone settings and try again.");
                    byte[] bytes=null;if(action.equals("write")){int code=data.optInt("tagcode",17);if(code<0||code>255||(code!=0&&((code&15)==0||(code>>4)>(code&15))))throw new Exception("Invalid tag index and count.");bytes=TigerCodec.encode(TigerCodec.fields(data),data.optLong("timestamp",System.currentTimeMillis()/1000L-946684800L),code);}
                    queue(new Pending(id,action,bytes));break;
                case "save":byte[] raw=TigerCodec.unhex(data.optString("data",""));if(raw.length!=144)throw new Exception("Expected 144 payload bytes.");main.post(()->startSave(id,raw));break;
                default:throw new Exception("Unsupported operation.");
            }}catch(Exception e){error(id,e.getMessage()==null?"Operation failed.":e.getMessage());}});
        }
    }
    private void startSave(String id,byte[] data){if(saveId!=null){error(id,"A save operation is already open.");return;}saveId=id;saveBytes=data;try{Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"tigertag.bin");startActivityForResult(intent,SAVE_FILE);}catch(Exception e){saveId=null;saveBytes=null;error(id,"Could not open the save dialog.");}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==OPEN_FILE){if(chooser!=null){Uri uri=resultCode==RESULT_OK&&data!=null?data.getData():null;chooser.onReceiveValue(uri==null?null:new Uri[]{uri});chooser=null;}}
        else if(requestCode==SAVE_FILE){String id=saveId;byte[] bytes=saveBytes;saveId=null;saveBytes=null;if(id==null)return;Uri uri=resultCode==RESULT_OK&&data!=null?data.getData():null;if(uri==null){error(id,"Save cancelled.");return;}worker.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new Exception("Cannot open the chosen file.");out.write(bytes);reply(id,new JSONObject().put("ok",true));}catch(Exception ex){error(id,"Could not save file: "+ex.getMessage());}});}
    }
}
