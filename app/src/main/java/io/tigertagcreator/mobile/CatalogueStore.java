package io.tigertagcreator.mobile;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class CatalogueStore {
    private static final String BASE="https://raw.githubusercontent.com/TigerTag-Project/TigerTag-RFID-Guide/main/database/";
    private final File directory;
    private volatile int percent=0;
    private volatile String message="Preparing catalogue";
    private List<String[]> rows;
    CatalogueStore(Context ctx){this(new File(ctx.getFilesDir(),"catalogue"));}
    CatalogueStore(File directory){this.directory=directory;}
    JSONObject progress() throws Exception {return new JSONObject().put("percent",percent).put("message",message);}
    private static String text(JSONObject j,String k){Object v=j.opt(k);return v==null||v==JSONObject.NULL?"":String.valueOf(v);}
    private static byte[] readAll(InputStream in,int max)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[32768];int n;while((n=in.read(x))!=-1){if(b.size()+n>max)throw new IOException("File is too large.");b.write(x,0,n);}return b.toByteArray();}
    private static String jsonText(byte[] data){String s=new String(data,StandardCharsets.UTF_8);return s.startsWith("\uFEFF")?s.substring(1):s;}
    private JSONArray loadFile(String file,int stage)throws Exception{
        if(!directory.exists()&&!directory.mkdirs())throw new IOException("Cannot create catalogue cache.");
        File target=new File(directory,file);boolean stale=!target.exists()||System.currentTimeMillis()-target.lastModified()>24L*60*60*1000;
        percent=stage;message=(stale?"Downloading or updating ":"Using cached ")+file;
        if(stale){File temp=new File(directory,file+".tmp");try{
            HttpURLConnection c=(HttpURLConnection)new URL(BASE+file).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","TigerTagCreatorStudio/1.0");
            try{if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(temp)){byte[] buf=new byte[32768];int n;long count=0;while((n=in.read(buf))!=-1){count+=n;if(count>50_000_000)throw new IOException("Catalogue file too large.");out.write(buf,0,n);}}}finally{c.disconnect();}
            JSONArray checked;try(InputStream in=new FileInputStream(temp)){checked=new JSONArray(jsonText(readAll(in,50_000_000)));}
            if(checked.length()==0)throw new IOException("The downloaded catalogue is empty.");
            File backup=new File(directory,file+".bak");if(backup.exists())backup.delete();boolean hadOld=target.exists();if(hadOld&&!target.renameTo(backup))throw new IOException("Cannot update catalogue cache.");if(!temp.renameTo(target)){if(hadOld)backup.renameTo(target);throw new IOException("Cannot save catalogue cache.");}backup.delete();return checked;
        }catch(Exception ex){temp.delete();if(!target.exists())throw new IOException("Cannot download "+file+". Check your internet connection.",ex);message="Download unavailable; using cached "+file;}}
        try(InputStream in=new FileInputStream(target)){return new JSONArray(jsonText(readAll(in,50_000_000)));}
    }
    private synchronized void ensure()throws Exception{
        if(rows!=null){percent=100;message="Catalogue ready";return;}
        JSONArray catalog=loadFile("id_catalog.json",5),materials=loadFile("id_material.json",20),brands=loadFile("id_brand.json",35),aspects=loadFile("id_aspect.json",50);
        percent=70;message="Preparing material and brand lookups";
        Map<String,JSONObject> mat=new HashMap<>(),brand=new HashMap<>(),aspect=new HashMap<>();
        for(int i=0;i<materials.length();i++){JSONObject o=materials.getJSONObject(i);String k=text(o,"label");mat.put(k.toLowerCase(Locale.ROOT),o);if(!k.isEmpty()){TigerCodec.MATERIALS.put(k.toLowerCase(Locale.ROOT),o.optInt("id"));TigerCodec.MATERIAL_LABELS.put(o.optInt("id"),k);}}
        for(int i=0;i<brands.length();i++){JSONObject o=brands.getJSONObject(i);String k=text(o,"name");brand.put(k.toLowerCase(Locale.ROOT),o);if(!k.isEmpty()){TigerCodec.BRANDS.put(k.toLowerCase(Locale.ROOT),o.optInt("id"));TigerCodec.BRAND_LABELS.put(o.optInt("id"),k);}}
        for(int i=0;i<aspects.length();i++){JSONObject o=aspects.getJSONObject(i);aspect.put(text(o,"label").toLowerCase(Locale.ROOT),o);}
        List<String[]> list=new ArrayList<>();
        for(int i=0;i<catalog.length();i++){if(i%500==0){percent=75+(int)(20L*i/Math.max(1,catalog.length()));message="Indexing catalogue ("+i+" of "+catalog.length()+")";}
            JSONObject p=catalog.optJSONObject(i);if(p==null||!text(p,"product_type").equalsIgnoreCase("Filament"))continue;
            String material=text(p,"material"),brandName=text(p,"brand"),a1=text(p,"aspect1"),a2=text(p,"aspect2");JSONObject m=mat.get(material.toLowerCase(Locale.ROOT)),br=brand.get(brandName.toLowerCase(Locale.ROOT)),x1=aspect.get(a1.toLowerCase(Locale.ROOT)),x2=aspect.get(a2.toLowerCase(Locale.ROOT));JSONObject rec=m==null?null:m.optJSONObject("recommended");
            String weight="",measure=text(p,"measure").trim().toLowerCase(Locale.ROOT);try{if(measure.endsWith(" kg"))weight=Integer.toString((int)Math.round(Double.parseDouble(measure.substring(0,measure.length()-3).trim())*1000));else if(measure.endsWith(" g"))weight=Integer.toString((int)Math.round(Double.parseDouble(measure.substring(0,measure.length()-2).trim())));}catch(NumberFormatException ignored){}
            String color=text(p,"color").replace("#","");if(color.length()>=6)color=color.substring(0,6);else color="";
            list.add(new String[]{text(p,"id"),text(p,"title"),brandName,material,id(m),id(br),id(x1),id(x2),color,weight,val(rec,"nozzleTempMin"),val(rec,"nozzleTempMax"),val(rec,"bedTempMin"),val(rec,"bedTempMax"),val(rec,"dryTemp"),val(rec,"dryTime"),text(p,"sku"),text(p,"barcode"),text(p,"series"),text(p,"img_src")});
        }
        rows=list;percent=100;message="Catalogue ready";
    }
    private static String id(JSONObject o){return o==null?"":text(o,"id");}private static String val(JSONObject o,String k){return o==null?"":text(o,k);}
    JSONObject search(JSONObject query)throws Exception{
        ensure();String q=query.optString("q","").trim().toLowerCase(Locale.ROOT),brand=query.optString("brand",""),series=query.optString("series","");int offset=Math.max(0,query.optInt("offset",0));
        TreeSet<String> allBrands=new TreeSet<>(String.CASE_INSENSITIVE_ORDER),allSeries=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);List<String[]> matched=new ArrayList<>();String[] terms=q.isEmpty()?new String[0]:q.split("\\s+");
        for(String[] r:rows){if(!r[2].isEmpty())allBrands.add(r[2]);if((brand.isEmpty()||r[2].equals(brand))&&!r[18].isEmpty())allSeries.add(r[18]);if(!brand.isEmpty()&&!r[2].equals(brand)||!series.isEmpty()&&!r[18].equals(series))continue;String hay=(r[0]+" "+r[1]+" "+r[2]+" "+r[3]+" "+r[16]+" "+r[17]+" "+r[18]).toLowerCase(Locale.ROOT);boolean hit=true;for(String term:terms)if(!hay.contains(term)){hit=false;break;}if(hit)matched.add(r);}
        matched.sort((a,b)->{boolean ea=!q.isEmpty()&&(a[0].equalsIgnoreCase(q)||a[16].equalsIgnoreCase(q)||a[17].equalsIgnoreCase(q));boolean eb=!q.isEmpty()&&(b[0].equalsIgnoreCase(q)||b[16].equalsIgnoreCase(q)||b[17].equalsIgnoreCase(q));if(ea!=eb)return ea?-1:1;int cmp=a[1].compareToIgnoreCase(b[1]);return cmp!=0?cmp:a[2].compareToIgnoreCase(b[2]);});
        JSONArray items=new JSONArray();for(int i=offset;i<Math.min(matched.size(),offset+100);i++)items.put(new JSONArray(Arrays.asList(matched.get(i))));
        return new JSONObject().put("items",items).put("total",matched.size()).put("brands",new JSONArray(allBrands)).put("series",new JSONArray(allSeries));
    }
}
