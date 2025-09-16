package rt.common.msg.modul;

public final class MessageTypes {
	private MessageTypes() {};
	private static final String HELLO = "hello";
	private static final String INPUT = "input";
	private static final String ACK	  = "ack";
	private static final String STATE = "state";
	
	public static String getHello() { return HELLO; }
	public static String getInput() { return INPUT; }
	public static String getAck() 	{ return ACK; }
	public static String getState() { return STATE; }
}