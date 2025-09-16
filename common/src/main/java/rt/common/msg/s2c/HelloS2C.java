package rt.common.msg.s2c;

import com.fasterxml.jackson.annotation.JsonInclude;

import rt.common.msg.modul.MessageTypes;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HelloS2C {
	private String type = MessageTypes.getHello();
	private String you;
	
	public HelloS2C() { }
	public HelloS2C(String you) {
		this.you = you;
	}
	
	public String getType() { return type; }
	public HelloS2C setType(String type) { this.type = type; return this; }
	public String getYou() { return you; }
	public HelloS2C setYou(String you) { this.you = you; return this; }
}
