package hello;

import java.util.concurrent.locks.ReentrantLock;

import reactor.core.processor.Processor;
import reactor.core.processor.spec.ProcessorSpec;
import reactor.function.Supplier;

public class ProcessorFactory {

	private static Processor<HttpContext> processor;

	private static ReentrantLock lock = new ReentrantLock();

	public static Processor<HttpContext> getInstance() {
		if (processor == null) {
			while (lock.isLocked());
			lock.lock();
			if (processor == null) {
				processor = createInstance();
			}
			lock.unlock();
		}
		return processor;
	}

	private static Processor<HttpContext> createInstance() {
		Processor<HttpContext> processor = new ProcessorSpec<HttpContext>()
				.singleThreadedProducer()
				.yieldingWaitStrategy()
				.dataSupplier(new Supplier<HttpContext>() {
					@Override
					public HttpContext get() {
						return new HttpContext();
					}
				})
				.consume(new MultiThreadedConsumer())
				.get();
		return processor;
	}
}
