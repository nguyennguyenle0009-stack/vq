package rt.common.msg.s2c;

import java.util.HashMap;
import java.util.Map;

import rt.common.msg.modul.MessageTypes;
import rt.common.msg.modul.PlayerState;

public class StateS2C {
	private String type = MessageTypes.getState();
	private long tick;
	private long ts;	//server timestamp ms
	private Map<String, PlayerState> ents = new HashMap<>();
	
    public StateS2C() {}
    public StateS2C(long tick, long ts, Map<String, PlayerState> ents) {
        this.tick = tick; 
        this.ts = ts; 
        this.ents = ents;
    }
    
	public String getType() { return type; }
	public StateS2C setType(String type) { this.type = type; return this; }
	public long getTick() { return tick; }
	public StateS2C setTick(long tick) { this.tick = tick; return this; }
	public long getTs() { return ts; }
	public StateS2C setTs(long ts) { this.ts = ts; return this; }
	public Map<String, PlayerState> getEnts() { return ents; }
	public StateS2C setEnts(Map<String, PlayerState> ents) { this.ents = ents; return this; }
}