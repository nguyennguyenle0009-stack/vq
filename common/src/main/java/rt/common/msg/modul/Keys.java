package rt.common.msg.modul;

public class Keys {
	private boolean up;
	private boolean down;
	private boolean left;
	private boolean right;
	
	public Keys() { }
	public Keys(boolean up, boolean down, boolean left, boolean right) {
		this.up = up;
		this.down = down;
		this.left = left;
		this.right = right;
	}
	
	public boolean isUp() { return up; }
	public Keys setUp(boolean up) { this.up = up; return this; }
	public boolean isDown() { return down; }
	public Keys setDown(boolean down) { this.down = down; return this; }
	public boolean isLeft() { return left; }
	public Keys setLeft(boolean left) { this.left = left; return this; }
	public boolean isRight() { return right; }
	public Keys setRight(boolean right) { this.right = right; return this; }
}
