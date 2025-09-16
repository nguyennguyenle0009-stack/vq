package rt.common.msg.s2c;

import rt.common.msg.modul.MessageTypes;

public class AckS2C {
	private String type = MessageTypes.getAck();
	private int seq;
	
	public AckS2C() { }
	public AckS2C(int seq) {
		this.seq = seq;
	}
	
	public String getType() { return type; }
	public AckS2C setType(String type) { this.type = type; return this; }
	public int getSeq() { return seq; }
	public AckS2C setSeq(int seq) { this.seq = seq; return this; }
}
