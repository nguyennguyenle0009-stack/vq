package rt.common.net.dto;

public record ChunkS2C(String type, int ver, int cx, int cy, int size,
                       byte[] layer1, byte[] layer2, byte[] collisionBits) implements Msg {
    public ChunkS2C(int cx,int cy,int size,byte[] l1,byte[] l2,byte[] coll){
        this("chunk", 1, cx, cy, size, l1, l2, coll);
    }
}
