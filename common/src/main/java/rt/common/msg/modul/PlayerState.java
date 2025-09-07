package rt.common.msg.modul;

public class PlayerState {
	private String id;	// player id (optional on client)
	private double x;
	private double y;
	
	public PlayerState() {}
	public PlayerState(String id, double x, double y) {
		this.id = id;
		this.x = x;
		this.y = y;
	}
	
	public String getId() { return id; }
	public PlayerState setId(String id) { this.id = id; return this; }
	public double getX() { return x; }
	public PlayerState setX(double x) { this.x = x; return this; }
	public double getY() { return y; }
	public PlayerState setY(double y) { this.y = y; return this; }
}
