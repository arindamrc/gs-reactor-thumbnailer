package hello;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import reactor.core.Environment;
import reactor.core.Reactor;
import reactor.core.processor.Processor;
import reactor.core.processor.spec.ProcessorSpec;
import reactor.core.spec.Reactors;
import reactor.event.dispatch.Dispatcher;
import reactor.event.dispatch.RingBufferDispatcher;
import reactor.event.dispatch.ThreadPoolExecutorDispatcher;
import reactor.function.Consumer;
import reactor.function.Supplier;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;

@Configuration
public class AuxConfig {

	private static final int TP_QUEUE_SIZE = 131072;
	private static final int TP_COUNT = 50;
	private static final int RB_SIZE = 65536;
	private static final String WORKER_EVENT_HANDLER = "worker-tp-service";
	private static final String MAIN_EVENT_LOOP = "main-rb-http";

	@Bean
	public Dispatcher mainDispatcher() {
		return new RingBufferDispatcher(MAIN_EVENT_LOOP, RB_SIZE,
				new Consumer<Throwable>() {

					@Override
					public void accept(Throwable arg0) {
						throw new RuntimeException(arg0);
					}
				}, ProducerType.MULTI, new BusySpinWaitStrategy());
	}

	@Bean
	public Dispatcher workerDispatcher() {
		return new ThreadPoolExecutorDispatcher(TP_COUNT, TP_QUEUE_SIZE,
				WORKER_EVENT_HANDLER);
	}

	@Bean
	public Environment reactorEnvironment() {
		return new Environment();
	}

//	@Bean
//	@DependsOn("multiThreadedConsumer")
	public Processor<HttpContext> httpProcessor(MultiThreadedConsumer consumer) {
		Processor<HttpContext> processor = new ProcessorSpec<HttpContext>()
				.singleThreadedProducer()
				.yieldingWaitStrategy()
				.dataSupplier(new Supplier<HttpContext>() {
					@Override
					public HttpContext get() {
						return new HttpContext();
					}
				})
				.consume(consumer)
				.get();
		return processor;
	}

}
