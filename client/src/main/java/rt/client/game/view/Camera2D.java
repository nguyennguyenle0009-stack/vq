package rt.client.game.view;

public final class Camera2D {
    public double x = 0, y = 0;
    public int viewTilesW = 32, viewTilesH = 18;
    public int minTx(){ return (int)Math.floor(x - viewTilesW/2.0); }
    public int minTy(){ return (int)Math.floor(y - viewTilesH/2.0); }
    public int maxTx(){ return minTx() + viewTilesW; }
    public int maxTy(){ return minTy() + viewTilesH; }
}
