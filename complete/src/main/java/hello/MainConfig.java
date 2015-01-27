package hello;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import reactor.core.Environment;
import reactor.core.composable.Promise;
import reactor.event.dispatch.Dispatcher;
import reactor.function.Consumer;
import reactor.net.NetChannel;
import reactor.net.NetServer;
import reactor.net.config.ServerSocketOptions;
import reactor.net.netty.NettyServerSocketOptions;
import reactor.net.netty.tcp.NettyTcpServer;
import reactor.net.tcp.spec.TcpServerSpec;

//@EnableAutoConfiguration
//@Configuration
//@ComponentScan
//// @EnableReactor
//@Import(AuxConfig.class)
public class MainConfig {

	public static class RequestConsumer {

		private static AtomicInteger counter = new AtomicInteger(0);
		private int currCount;

		private NetChannel<FullHttpRequest, FullHttpResponse> ch;

		public RequestConsumer(NetChannel<FullHttpRequest, FullHttpResponse> ch) {
			currCount = counter.incrementAndGet();
//			LOG.info("New Responder...:" + currCount);
			this.ch = ch;
		}

		public RequestConsumer respond() {
			final FullHttpResponse response = generateResponse();
			ch.send(response).then(new Consumer<Void>() {
				
				@Override
				public void accept(Void arg0) {
					ch.close(new Consumer<Boolean>() {
						
						@Override
						public void accept(Boolean arg0) {
							
						}
					});
				}
			}, new Consumer<Throwable>() {
				
				@Override
				public void accept(Throwable arg0) {
					LOG.error("Problem...", arg0);
				}
			});
			return this;
		}

		public void close() {
			// ch.on().close(new Runnable() {
			//
			// @Override
			// public void run() {
			// LOG.info("Closed...");
			// }
			// });
			ch.close(new Consumer<Boolean>() {

				@Override
				public void accept(Boolean arg0) {
//					LOG.info("Channel closed. Count: " + currCount);
				}
			});
		}
	}

	private static final Logger LOG = LoggerFactory.getLogger(MainConfig.class);

	private static final int SERVER_PORT = 8080;

	@Resource(name = "mainDispatcher")
	private Dispatcher mainDispatcher;

	@Resource(name = "reactorEnvironment")
	private Environment reactorEnvironment;

	private ServerSocketOptions serverOptions() {
		return new NettyServerSocketOptions()
				.pipelineConfigurer(new Consumer<ChannelPipeline>() {

					@Override
					public void accept(ChannelPipeline pipeline) {
						pipeline.addLast(new HttpServerCodec()).addLast(
								new HttpObjectAggregator(16 * 1024 * 1024));
					}
				});
	}

	@Bean
	public CountDownLatch closeLatch() {
		return new CountDownLatch(1);
	}

	@Bean
	public NetServer<FullHttpRequest, FullHttpResponse> netServer(
			final CountDownLatch closeLatch) throws InterruptedException {

		NetServer<FullHttpRequest, FullHttpResponse> server = new TcpServerSpec<FullHttpRequest, FullHttpResponse>(
				NettyTcpServer.class)
				.env(reactorEnvironment)
				.listen(SERVER_PORT)
				.dispatcher(mainDispatcher)
				.options(serverOptions())
				.uncaughtErrorHandler(uncaughtErrorHandler())
				.consume(
						new Consumer<NetChannel<FullHttpRequest, FullHttpResponse>>() {
							@Override
							public void accept(
									final NetChannel<FullHttpRequest, FullHttpResponse> ch) {
								new RequestConsumer(ch).respond();
							}
						}).get();

		server.start().await();

		return server;
	}

	private Consumer<Throwable> uncaughtErrorHandler() {
		return new Consumer<Throwable>() {

			@Override
			public void accept(Throwable arg0) {
				LOG.error("Le Problem..", arg0);
			}
		};
	}

	private static FullHttpResponse generateResponse() {
		DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, OK);

		byte[] bytes = "done".getBytes(StandardCharsets.UTF_8);

		resp.content().writeBytes(bytes);

		resp.headers().set(CONTENT_TYPE, "text/plain")
				.set(CONTENT_LENGTH, resp.content().readableBytes());

		return resp;
	}

	public static void main(String... args) throws InterruptedException {
		ApplicationContext ctx = SpringApplication.run(MainConfig.class, args);

		// Reactor's TCP servers are non-blocking so we have to do something to
		// keep from exiting the main thread
		CountDownLatch closeLatch = ctx.getBean(CountDownLatch.class);
		closeLatch.await();
	}

}
