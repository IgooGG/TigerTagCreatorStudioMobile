package io.tigertagcreator.mobile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** TigerTag payload codec. Field offsets follow the public TigerTag RFID Guide. */
final class TigerCodec {
    static final Map<String,Integer> MATERIALS=new ConcurrentHashMap<>(), BRANDS=new ConcurrentHashMap<>();
    static final Map<Integer,String> MATERIAL_LABELS=new ConcurrentHashMap<>(), BRAND_LABELS=new ConcurrentHashMap<>();
    static {MATERIALS.put("pla",38219);MATERIALS.put("petg",38256);MATERIALS.put("abs",20562);MATERIALS.put("tpu",43518);MATERIALS.put("asa",12844);MATERIALS.put("pla-cf",48310);BRANDS.put("unspecified",0);BRANDS.put("rosa3d",19961);}
    static long u(byte[] b,int at,int count){long v=0;for(int i=0;i<count;i++)v=(v<<8)|(b[at+i]&255);return v;}
    static void put(byte[] b,int at,int count,long v){for(int i=count-1;i>=0;i--){b[at+i]=(byte)v;v>>>=8;}}
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder(bytes.length*2);for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02X",b&255));return s.toString();}
    static byte[] unhex(String text)throws Exception{if((text.length()&1)!=0)throw new Exception("Invalid binary data.");byte[] b=new byte[text.length()/2];for(int i=0;i<b.length;i++){int n=Character.digit(text.charAt(i*2),16),m=Character.digit(text.charAt(i*2+1),16);if(n<0||m<0)throw new Exception("Invalid hexadecimal data.");b[i]=(byte)((n<<4)|m);}return b;}
    static int resolve(String s,Map<String,Integer> table,String field)throws Exception {s=s.trim();Integer v=table.get(s.toLowerCase(Locale.ROOT));if(v!=null)return v;try{int n=Integer.parseInt(s);if(n>=0&&n<=65535)return n;}catch(NumberFormatException ignored){}throw new Exception("Unknown "+field+". Select a catalogue product or enter its numeric ID.");}
    static String label(int id,Map<String,Integer> table,String fallback){if(id==0&&table==BRANDS)return "Unspecified";if(id==19961&&table==BRANDS)return "Rosa3D";String name=(table==BRANDS?BRAND_LABELS:MATERIAL_LABELS).get(id);if(name!=null)return name;for(Map.Entry<String,Integer> e:table.entrySet())if(e.getValue()==id)return e.getKey().toUpperCase(Locale.ROOT);return fallback;}
    static long number(String[] f,int i,long max,String name)throws Exception {try{long v=Long.parseLong(f[i].trim());if(v<0||v>max)throw new NumberFormatException();return v;}catch(NumberFormatException e){throw new Exception("Invalid "+name+".");}}
    static byte[] color(String value)throws Exception {String s=value.trim();if(s.startsWith("#"))s=s.substring(1);if(s.length()!=6)throw new Exception("Colors must be six hex digits.");return unhex(s);}
    static String[] fields(JSONObject d){String[] f=new String[18];for(int i=0;i<18;i++)f[i]=d.optString("f"+i,"");return f;}
    static byte[] encode(String[] f,long timestamp,int tagCode)throws Exception {
        byte[] b=new byte[80];put(b,0,4,0x5bf59264L);put(b,4,4,0xffffffffL);int material=resolve(f[0],MATERIALS,"material");if(material==0)throw new Exception("Choose a material.");put(b,8,2,material);b[10]=(byte)number(f,2,255,"aspect 1");b[11]=(byte)number(f,3,255,"aspect 2");b[12]=(byte)0x8e;
        if(f[4].trim().equals("1.75"))b[13]=0x38;else if(f[4].trim().equals("2.85"))b[13]=(byte)0xdd;else throw new Exception("Diameter must be 1.75 or 2.85 mm.");
        put(b,14,2,resolve(f[1],BRANDS,"brand"));System.arraycopy(color(f[5]),0,b,16,3);b[19]=(byte)255;put(b,20,3,number(f,12,0xffffff,"initial weight"));b[23]=21;
        long min=number(f,6,65535,"nozzle minimum"),max=number(f,7,65535,"nozzle maximum");if(min>max)throw new Exception("Nozzle minimum exceeds maximum.");put(b,24,2,min);put(b,26,2,max);
        b[28]=(byte)number(f,10,255,"drying temperature");b[29]=(byte)number(f,11,255,"drying time");b[30]=(byte)number(f,8,255,"bed minimum");b[31]=(byte)number(f,9,255,"bed maximum");if((b[30]&255)>(b[31]&255))throw new Exception("Bed minimum exceeds maximum.");
        put(b,32,4,timestamp);System.arraycopy(color(f[16]),0,b,36,3);b[39]=(byte)tagCode;System.arraycopy(color(f[17]),0,b,40,3);
        double td;try{td=Double.parseDouble(f[14].trim());}catch(NumberFormatException e){throw new Exception("Invalid HueForge TD.");}if(!Double.isFinite(td)||td<0||td>100)throw new Exception("TD must be between 0 and 100.");put(b,44,2,Math.round(td*10));byte[] message=f[15].getBytes(StandardCharsets.UTF_8);if(message.length>28)throw new Exception("Message must fit in 28 UTF-8 bytes.");System.arraycopy(message,0,b,48,message.length);put(b,76,3,number(f,13,0xffffff,"remaining weight"));return b;
    }
    static byte[] parse(byte[] raw)throws Exception{if(raw.length!=80&&raw.length!=144)throw new Exception("A TigerTag payload must be 80 or 144 bytes.");byte[] b=Arrays.copyOf(raw,144);decode(b);return b;}
    static String[] decode(byte[] b)throws Exception {
        if(b.length!=144)throw new Exception("Expected 144 bytes.");long marker=u(b,0,4);if(marker!=0x5bf59264L&&marker!=0xbc0fcb97L)throw new Exception("Not a recognized TigerTag record.");if((b[12]&255)!=0x8e||(b[23]&255)!=21)throw new Exception("Editing currently supports filament records in grams.");
        String diameter=(b[13]&255)==0x38?"1.75":(b[13]&255)==0xdd?"2.85":"unknown";int end=48;while(end<76&&b[end]!=0)end++;
        return new String[]{label((int)u(b,8,2),MATERIALS,Long.toString(u(b,8,2))),label((int)u(b,14,2),BRANDS,Long.toString(u(b,14,2))),Integer.toString(b[10]&255),Integer.toString(b[11]&255),diameter,hex(Arrays.copyOfRange(b,16,19)),Long.toString(u(b,24,2)),Long.toString(u(b,26,2)),Integer.toString(b[30]&255),Integer.toString(b[31]&255),Integer.toString(b[28]&255),Integer.toString(b[29]&255),Long.toString(u(b,20,3)),Long.toString(u(b,76,3)),String.format(Locale.ROOT,"%.1f",u(b,44,2)/10.0),new String(b,48,end-48,StandardCharsets.UTF_8),hex(Arrays.copyOfRange(b,36,39)),hex(Arrays.copyOfRange(b,40,43))};
    }
    static JSONObject preview(JSONObject input)throws Exception{
        String[] f=fields(input);int code=input.optInt("tagcode",17);if(code<0||code>255)throw new Exception("Invalid tag count.");long ts=input.optLong("timestamp",System.currentTimeMillis()/1000L-946684800L);
        String old=input.optString("original","");byte[] raw=null;boolean preserved=false;
        if(!old.isEmpty()){byte[] prior=unhex(old);if(prior.length==144){try{String[] o=decode(prior);boolean same=true;for(int i=0;i<18;i++){if(i==0)same &= resolve(o[i],MATERIALS,"material")==resolve(f[i],MATERIALS,"material");else if(i==1)same &= resolve(o[i],BRANDS,"brand")==resolve(f[i],BRANDS,"brand");else same &= o[i].equals(f[i]);}if(same&&(prior[39]&255)==code){raw=prior;preserved=true;}}catch(Exception ignored){}}}
        if(raw==null)raw=Arrays.copyOf(encode(f,ts,code),144);
        JSONObject out=new JSONObject();out.put("raw",hex(raw));out.put("preserved",preserved);return out;
    }
    static JSONObject decoded(byte[] raw)throws Exception{byte[] b=parse(raw);JSONObject o=new JSONObject();o.put("raw",hex(b));o.put("fields",new JSONArray(Arrays.asList(decode(b))));return o;}
}
