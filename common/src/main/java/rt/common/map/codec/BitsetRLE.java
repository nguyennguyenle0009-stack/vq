package rt.common.map.codec;

import java.io.ByteArrayOutputStream;
import java.util.BitSet;

public final class BitsetRLE {
    private BitsetRLE(){}
    public static byte[] encode(BitSet bs, int n){
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i=0;
        while (i<n){
            boolean v = bs.get(i);
            int run=1;
            while (i+run<n && bs.get(i+run)==v && run<255) run++;
            out.write(v?1:0);
            out.write(run);
            i += run;
        }
        return out.toByteArray();
    }
    public static BitSet decode(byte[] rle, int n){
        BitSet bs = new BitSet(n);
        int i=0;
        for (int k=0; k+1<rle.length; k+=2){
            boolean v = (rle[k] & 0xFF) != 0;
            int run = rle[k+1] & 0xFF;
            if (v) bs.set(i, i+run);
            i += run;
            if (i>=n) break;
        }
        return bs;
    }
}
