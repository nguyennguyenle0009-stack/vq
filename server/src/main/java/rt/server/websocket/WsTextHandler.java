package rt.server.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class WsTextHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
		String text = msg.text();
		System.out.println("Client says: " + text);
		
		// Trả lời client
		ctx.channel().writeAndFlush(new TextWebSocketFrame("Hello, I got your message: " + text));
	}
	
	@Override
	public void handlerAdded(ChannelHandlerContext ctx) {
		System.out.println("Client connected: " + ctx.channel().id());
	}
	
	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) {
		System.out.println("Client disconnected: " + ctx.channel().id());
	}
}
