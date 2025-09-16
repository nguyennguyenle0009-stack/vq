package rt.common.msg.s2c;

import com.fasterxml.jackson.annotation.JsonInclude;

import rt.common.msg.modul.Keys;
import rt.common.msg.modul.MessageTypes;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputC2S {
	private String type = MessageTypes.getInput();
	private int seq;		//client input sequence (monotonic++)
	private Keys keys = new Keys();
	
	public InputC2S() { }
	public InputC2S(int seq, Keys keys) {
		this.seq = seq;
		this.keys = keys;
	}
	
	public String getType() { return type; }
	public InputC2S setType(String type) { this.type = type; return this; }
	public int getSeq() { return seq; }
	public InputC2S setSeq(int seq) { this.seq = seq; return this; }
	public Keys getKeys() { return keys; }
	public InputC2S setKeys(Keys keys) { this.keys = keys; return this; }
}
