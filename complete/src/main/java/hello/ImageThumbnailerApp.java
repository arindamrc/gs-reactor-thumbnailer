package hello;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static reactor.event.selector.Selectors.$;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;

import reactor.core.Environment;
import reactor.core.Reactor;
import reactor.core.composable.Stream;
import reactor.core.spec.Reactors;
import reactor.event.dispatch.RingBufferDispatcher;
import reactor.function.Consumer;
import reactor.function.Function;
import reactor.net.NetChannel;
import reactor.net.NetServer;
import reactor.net.config.ServerSocketOptions;
import reactor.net.netty.NettyServerSocketOptions;
import reactor.net.netty.tcp.NettyTcpServer;
import reactor.net.tcp.spec.TcpServerSpec;
import reactor.spring.context.config.EnableReactor;

/**
 * Simple Spring Boot app to start a Reactor+Netty-based REST API server for
 * thumbnailing uploaded images.
 */
//@EnableAutoConfiguration
//@Configuration
//@ComponentScan
//@EnableReactor
public class ImageThumbnailerApp {

	private AtomicInteger counter = new AtomicInteger(0);

//	@Bean
	public Reactor reactor(Environment env) {
		Reactor reactor = Reactors.reactor(env, Environment.THREAD_POOL);

		// Register our thumbnailer on the Reactor
		reactor.receive($("thumbnail"), new BufferedImageThumbnailer(250));

		return reactor;
	}

//	@Bean
	public ServerSocketOptions serverSocketOptions() {
		return new NettyServerSocketOptions()
				.pipelineConfigurer(new Consumer<ChannelPipeline>() {

					@Override
					public void accept(ChannelPipeline pipeline) {
						pipeline.addLast(new HttpServerCodec()).addLast(
								new HttpObjectAggregator(16 * 1024 * 1024));
					}
				});
	}

//	@Bean
	public NetServer<FullHttpRequest, FullHttpResponse> restApi(
			Environment env, ServerSocketOptions opts, final Reactor reactor,
			final CountDownLatch closeLatch) throws InterruptedException {

		NetServer<FullHttpRequest, FullHttpResponse> server = new TcpServerSpec<FullHttpRequest, FullHttpResponse>(
				NettyTcpServer.class)
				.env(env)
				.listen(8080)
				.dispatcher(
						new RingBufferDispatcher("EventLoop", 8192,
								new Consumer<Throwable>() {

									@Override
									public void accept(Throwable arg0) {
										throw new RuntimeException(arg0);
									}
								}, ProducerType.SINGLE,
								new YieldingWaitStrategy()))
				.options(opts)
				.consume(
						new Consumer<NetChannel<FullHttpRequest, FullHttpResponse>>() {

							@Override
							public void accept(
									final NetChannel<FullHttpRequest, FullHttpResponse> ch) {

								// filter requests by URI via the input Stream
								Stream<FullHttpRequest> in = ch.in();

								in.filter(
										new Function<FullHttpRequest, Boolean>() {

											@Override
											public Boolean apply(
													FullHttpRequest req) {
												return true;
											}
										})
										.when(Throwable.class,
												ImageThumbnailerRestApi
														.errorHandler(ch))
										.consume(
												new Consumer<FullHttpRequest>() {

													@Override
													public void accept(
															FullHttpRequest arg0) {
														DefaultFullHttpResponse resp = new DefaultFullHttpResponse(
																HTTP_1_1, OK);

														byte[] bytes = "DONE"
																.getBytes(StandardCharsets.UTF_8);
														resp.content()
																.writeBytes(
																		bytes);

														resp.headers().set(
																CONTENT_TYPE,
																"text/plain");
														resp.headers()
																.set(CONTENT_LENGTH,
																		resp.content()
																				.readableBytes());
														counter.incrementAndGet();
														ch.sendAndForget(resp);
													}
												});

								in.filter(
										new Function<FullHttpRequest, Boolean>() {

											@Override
											public Boolean apply(
													FullHttpRequest req) {
												return "/shutdown".equals(req
														.getUri());
											}

										}).consume(
										new Consumer<FullHttpRequest>() {

											@Override
											public void accept(
													FullHttpRequest arg0) {
												closeLatch.countDown();
											}
										});

								in.filter(
										new Function<FullHttpRequest, Boolean>() {

											@Override
											public Boolean apply(
													FullHttpRequest req) {
												return "/print".equals(req
														.getUri());
											}

										}).consume(
										new Consumer<FullHttpRequest>() {

											@Override
											public void accept(
													FullHttpRequest arg0) {
												DefaultFullHttpResponse resp = new DefaultFullHttpResponse(
														HTTP_1_1, OK);

												byte[] bytes = String
														.valueOf(
																counter.intValue())
														.getBytes(
																StandardCharsets.UTF_8);
												resp.content()
														.writeBytes(bytes);

												resp.headers().set(
														CONTENT_TYPE,
														"text/plain");
												resp.headers()
														.set(CONTENT_LENGTH,
																resp.content()
																		.readableBytes());
												ch.sendAndForget(resp);
											}
										});
							}
						}).get();

		server.start().await();

		return server;
	}

//	@Bean
	public CountDownLatch closeLatch() {
		return new CountDownLatch(1);
	}

	public static void main(String... args) throws InterruptedException {
		ApplicationContext ctx = SpringApplication.run(
				ImageThumbnailerApp.class, args);

		// Reactor's TCP servers are non-blocking so we have to do something to
		// keep from exiting the main thread
		CountDownLatch closeLatch = ctx.getBean(CountDownLatch.class);
		closeLatch.await();
	}

}
