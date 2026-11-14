package io.tigertagcreator.mobile;

import java.io.IOException;
import java.util.Arrays;

/** NTAG Type 2 transport. Only user pages 04..17 are ever written. */
public final class TagIo {
    public interface Transport { byte[] transceive(byte[] request) throws IOException; }
    private TagIo() {}
    private static byte[] pages(Transport t, int page) throws IOException {
        byte[] result = t.transceive(new byte[]{0x30, (byte)page});
        if (result == null || result.length != 16) throw new IOException("Could not read page " + page + ". Keep the tag against the phone.");
        return result;
    }
    public static byte[] read(Transport t) throws IOException {
        byte[] cc = pages(t, 3);
        if ((cc[0] & 255) != 0xe1 || (cc[2] & 255) * 8 < 144) throw new IOException("This tag does not have enough Type 2 memory for TigerTag.");
        if (((cc[3] & 255) >>> 4) != 0) throw new IOException("This tag is not freely readable.");
        byte[] result = new byte[144];
        for (int i=0; i<9; i++) System.arraycopy(pages(t,4+i*4),0,result,i*16,16);
        return result;
    }
    public static boolean blank(byte[] data) {
        if (data.length != 144) return false;
        boolean zero=true;for(byte b:data) if(b!=0){zero=false;break;}if(zero)return true;
        if(data[0]==3 && data[1]==0 && (data[2]&255)==0xfe){for(int i=3;i<144;i++)if(data[i]!=0)return false;return true;}
        if((data[0]&255)==0x6c && (data[1]&255)==0x41 && (data[2]&255)==0xa2 && (data[3]&255)==0xe1){for(int i=80;i<144;i++)if(data[i]!=0)return false;return true;}
        return false;
    }
    public static void write(Transport t, byte[] payload) throws IOException {
        if (payload.length != 80) throw new IOException("Expected an 80-byte TigerTag record.");
        byte[] cc=pages(t,3);
        if ((cc[0]&255)!=0xe1 || (cc[2]&255)*8<144 || (cc[3]&15)!=0) throw new IOException("This tag is too small or reports write protection.");
        if(!blank(read(t))) throw new IOException("Tag contains existing data or a signature. Use a blank tag.");
        // Publish the format marker only after every other data page succeeds.
        for(int i=0;i<20;i++) {
            int page=(i==19)?4:i+5, off=(page-4)*4;
            byte[] cmd={(byte)0xa2,(byte)page,payload[off],payload[off+1],payload[off+2],payload[off+3]};
            byte[] ack=t.transceive(cmd);
            if(ack==null||ack.length!=1||(ack[0]&15)!=0x0a) throw new IOException("Write stopped at page "+page+"; the tag may be partially written.");
        }
        byte[] actual=read(t);
        if(!Arrays.equals(Arrays.copyOf(actual,80),payload)) throw new IOException("Read-back differs from the requested data. Do not rely on this tag.");
    }
}
