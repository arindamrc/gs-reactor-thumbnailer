package hello;

import static hello.ImageThumbnailerRestApi.badRequest;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.composable.Promise;
import reactor.core.composable.Stream;
import reactor.function.Consumer;
import reactor.net.NetChannel;

public class JsonConsumer implements Consumer<FullHttpRequest> {

	private static final Logger LOG = LoggerFactory
			.getLogger(JsonConsumer.class);

	private Stream<FullHttpRequest> reqStream;
	private NetChannel<FullHttpRequest, FullHttpResponse> reqChannel;
	private ObjectMapper mapper;
	private AsyncMyPojoService service;

	public JsonConsumer(Stream<FullHttpRequest> in,
			NetChannel<FullHttpRequest, FullHttpResponse> ch) {
		this.reqStream = in;
		this.reqChannel = ch;
		this.mapper = new ObjectMapper();
		service = SpringContext.getApplicationContext().getBean(
				AsyncMyPojoService.class);
	}

	@Override
	public void accept(FullHttpRequest req) {
		String content = req.content().toString(StandardCharsets.UTF_8);
		req.content().release();
		LOG.info("raw: {}", content);
		MyPojo myPojo = null;
		try {
			myPojo = mapper.readValue(content, MyPojo.class);
			LOG.info("mapped: {}", myPojo);
		} catch (IOException e) {
			e.printStackTrace();
		}
		Promise<Boolean> savePromise = service.save(myPojo);
		savePromise.onComplete(new Consumer<Promise<Boolean>>() {

			@Override
			public void accept(Promise<Boolean> arg0) {
				LOG.info("Done as promised...");
			}
		}).onError(new Consumer<Throwable>() {

			@Override
			public void accept(Throwable arg0) {
				LOG.warn("Problem, but stiff upper lip!!", arg0);
				respond(badRequest("Le Problem..."));
			}
		}).onSuccess(new Consumer<Boolean>() {

			@Override
			public void accept(Boolean arg0) {
				LOG.trace("Success -> nothing like it!");
				LOG.trace("boolean: {}", arg0);
				respond(generateResponse());
			}
		});

	}

	private void respond(FullHttpResponse httpResponse) {
		Promise<Void> sendPromise = reqChannel.send(httpResponse);
		sendPromise.onComplete(new Consumer<Promise<Void>>() {

			@Override
			public void accept(Promise<Void> arg0) {
				LOG.info("Response sent...");
			}
		}).onError(new Consumer<Throwable>() {

			@Override
			public void accept(Throwable arg0) {
				LOG.error("Le problem ??", arg0);
			}
		}).onSuccess(new Consumer<Void>() {

			@Override
			public void accept(Void arg0) {
				LOG.info("should work...");
			}
		});
	}

	private FullHttpResponse generateResponse() {
		DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, OK);

		byte[] bytes = "done".getBytes(StandardCharsets.UTF_8);
		resp.content().writeBytes(bytes);

		resp.headers().set(CONTENT_TYPE, "text/plain");
		resp.headers().set(CONTENT_LENGTH, resp.content().readableBytes());

		return resp;
	}

}
