package hello;

import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import reactor.core.processor.Operation;
import reactor.core.processor.Processor;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;

//@Component
public class HttpHelloWorldServerHandler extends ChannelInboundHandlerAdapter {
	
	private static final byte[] CONTENT = { 'H', 'e', 'l', 'l', 'o', ' ', 'W',
			'o', 'r', 'l', 'd' };
	
	private static final Logger LOG = LoggerFactory
			.getLogger(HttpHelloWorldServerHandler.class);

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		ctx.flush();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		putInRingBuffer(ctx, msg);
		// if (msg instanceof HttpRequest) {
		// HttpRequest req = (HttpRequest) msg;
		//
		// if (HttpHeaders.is100ContinueExpected(req)) {
		// ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
		// }
		// boolean keepAlive = HttpHeaders.isKeepAlive(req);
		// FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1,
		// OK, Unpooled.wrappedBuffer(CONTENT));
		// response.headers().set("Content-type", "text/plain");
		// response.headers().set("Content-length",
		// response.content().readableBytes());
		//
		// if (!keepAlive) {
		// ctx.write(response).addListener(ChannelFutureListener.CLOSE);
		// } else {
		// response.headers().set("Connection", "keep-alive");
		// ctx.write(response);
		// }
		// }
	}

	private void putInRingBuffer(ChannelHandlerContext ctx, Object msg) {
		LOG.info("Putting in rb...");
		if (msg instanceof HttpMessage) {
			Operation<HttpContext> op = ProcessorFactory.getInstance()
					.prepare();
			HttpContext httpContext = op.get();
			httpContext.setCtx(ctx);
			httpContext.setRequest((HttpRequest) msg);
			op.commit();
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}
}
