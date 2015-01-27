package hello;

import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.function.Consumer;

//@Component
public class MultiThreadedConsumer implements Consumer<HttpContext> {

	private static final byte[] CONTENT = { 'H', 'e', 'l', 'l', 'o', ' ', 'W',
			'o', 'r', 'l', 'd' };

	private static final Logger LOG = LoggerFactory
			.getLogger(MultiThreadedConsumer.class);

	@Override
	public void accept(HttpContext httpContext) {
		LOG.info("Consuming..");

		HttpRequest req = httpContext.getRequest();
		ChannelHandlerContext ctx = httpContext.getCtx();

		if (HttpHeaders.is100ContinueExpected(req)) {
			ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
		}
		boolean keepAlive = HttpHeaders.isKeepAlive(req);
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK,
				Unpooled.wrappedBuffer(CONTENT));
		response.headers().set("Content-type", "text/plain");
		response.headers().set("Content-length",
				response.content().readableBytes());

		if (!keepAlive) {
			ctx.write(response).addListener(ChannelFutureListener.CLOSE)
					.addListener(new ContextFlushListener(ctx));
		} else {
			response.headers().set("Connection", "keep-alive");
			ctx.write(response).addListener(new ContextFlushListener(ctx));
		}
		ctx.flush();
	}

	private class ContextFlushListener implements
			GenericFutureListener<Future<? super Void>> {

		private ChannelHandlerContext ctx;

		public ContextFlushListener(ChannelHandlerContext ctx) {
			this.ctx = ctx;
		}

		@Override
		public void operationComplete(Future<? super Void> arg0)
				throws Exception {
			ctx.flush();
		}
	}
}
