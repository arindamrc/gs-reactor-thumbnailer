package hello;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.Environment;
import reactor.core.composable.Deferred;
import reactor.core.composable.Promise;
import reactor.core.composable.Stream;
import reactor.core.composable.spec.Promises;
import reactor.core.composable.spec.Streams;
import reactor.function.Consumer;

//@Service
public class AsyncMyPojoService {

	private static final Logger LOG = LoggerFactory
			.getLogger(AsyncMyPojoService.class);

	@Autowired
	private MyPojoRepository mpr;

	@Autowired
	private Environment env;

	public Promise<Boolean> save(MyPojo myPojo) {
		LOG.trace("In async service...");
		final Deferred<Boolean, Promise<Boolean>> deferredPromise = Promises
				.defer(env, Environment.RING_BUFFER);
		Promise<Boolean> composedPromise = deferredPromise.compose();
		Deferred<MyPojo, Stream<MyPojo>> deferred = Streams.defer(env,
				Environment.THREAD_POOL);
		Stream<MyPojo> serviceStream = deferred.compose();
		serviceStream.consume(new Consumer<MyPojo>() {

			@Override
			public void accept(MyPojo myPojo) {
				LOG.trace("consuming pojo...");
				MyPojo saved = mpr.save(myPojo);
				deferredPromise.accept(saved != null);
			}
		});
		deferred.accept(myPojo);
		return composedPromise;
	}

}
