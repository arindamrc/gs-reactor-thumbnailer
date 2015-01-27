package hello;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.FatalExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.WorkerPool;

public class DisruptorTest {

	@SuppressWarnings({ "unchecked" })
	public static void main(String[] args) throws InterruptedException,
			IOException, AlertException, TimeoutException {
		final int cores = Runtime.getRuntime().availableProcessors();
		final int bufferSize = 4 * 1024;
		final int ringSize = 2048;
		Executor executor = Executors.newFixedThreadPool(cores);

		final BlockingDeque<ByteBuffer> bufferPool = new LinkedBlockingDeque<>(
				ringSize);
		for (int i = 0; i < ringSize; i++) {
			bufferPool.add(ByteBuffer.allocate(bufferSize));
		}

		final Map<Long, SocketChannel> channels = new ConcurrentSkipListMap<>();
		final Map<Long, SelectionKey> selectionKeys = new ConcurrentSkipListMap<>();

		int handlerCount = cores;
		WorkHandler<SelectionEvent>[] handlers = new WorkHandler[handlerCount];
		final Sequence[] sequences = new Sequence[handlerCount];
		for (int i = 0; i < handlerCount; i++) {
			final int handlerId = i;
			handlers[i] = new WorkHandler<SelectionEvent>() {

				ByteBuffer msg = ByteBuffer.wrap(("HTTP/1.1 200 OK\r\n"
						+ "Connection: Keep-Alive\r\n"
						+ "Content-Type: text/plain\r\n"
						+ "Content-Length: 12\r\n\r\n" + "Hello World!")
						.getBytes());
				Sequence sequence = new Sequence(-1);

				{
					sequences[handlerId] = sequence;
				}

				@Override
				public void onEvent(SelectionEvent ev) throws Exception {
					// Allocate a ByteBuffer from a RingBuffer
					ByteBuffer buffer = bufferPool.take();
					if (buffer.position() > 0) {
						buffer.clear();
					}

					SocketChannel channel = channels.get(ev.id);
					SelectionKey key = selectionKeys.get(ev.id);

					try {
						int read = safeRead(channel, buffer);
						while (read > 0) {
							safeWrite(channel, msg.duplicate());

							// Read the data into memory
							buffer.flip();
							byte[] bytes = new byte[buffer.remaining()];
							buffer.get(bytes);
							// String input = new String(bytes);
							buffer.clear();
							read = safeRead(channel, buffer);
						}
						if (read < 0) {
							key.cancel();
							selectionKeys.remove(ev.id);
						}
					} finally {
						// Put the ByteBuffer back into the RingBuffer for
						// re-use
						bufferPool.add(buffer);
						sequence.set(ev.id);
					}
				}
			};
		}

		// Use a WorkerPool for handling requests
		WorkerPool workerPool = new WorkerPool(
				new EventFactory<SelectionEvent>() {
					@Override
					public SelectionEvent newInstance() {
						return new SelectionEvent();
					}
				}, new FatalExceptionHandler(), handlers);
		final RingBuffer<SelectionEvent> workerRing = workerPool
				.start(executor);
		workerRing.addGatingSequences(sequences);

		final ServerSocketChannel serverSocketChannel = ServerSocketChannel
				.open();
		serverSocketChannel.configureBlocking(false);

		Selector selector = Selector.open();
		serverSocketChannel
				.bind(new InetSocketAddress("127.0.0.1", 3000), 1024);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

		// Allocate the first worker
		long workerId = workerRing.next();
		while (true) {
			int cnt = 0;
			try {
				cnt = selector.select();
			} catch (CancelledKeyException e) {
			}
			if (cnt > 0) {
				Iterator<SelectionKey> keys = selector.selectedKeys()
						.iterator();
				while (keys.hasNext()) {
					SelectionKey key = keys.next();
					keys.remove();
					if (key.isValid()) {
						SelectionEvent event;
						if (key.isAcceptable()) {
							ServerSocket serverSocket = serverSocketChannel
									.socket();
							serverSocket.setReceiveBufferSize(bufferSize);
							serverSocket.setReuseAddress(true);

							boolean hasSocket = true;
							do {
								SocketChannel channel = serverSocketChannel
										.accept();
								if (null != channel) {
									channel.configureBlocking(false);
									channel.setOption(
											StandardSocketOptions.SO_KEEPALIVE,
											true);
									channel.setOption(
											StandardSocketOptions.TCP_NODELAY,
											true);
									channel.setOption(
											StandardSocketOptions.SO_RCVBUF,
											bufferSize);
									channel.setOption(
											StandardSocketOptions.SO_SNDBUF,
											bufferSize);
									SelectionKey readKey = channel.register(
											selector, SelectionKey.OP_READ);

									// Allocate an Event object for dispatching
									// to the handler
									event = workerRing.get(workerId);
									event.id = workerId;
									channels.put(workerId, channel);
									selectionKeys.put(workerId, readKey);
									// Dispatch this event to a handler
									workerRing.publish(workerId);
									// Immediately allocate the next worker ID
									workerId = workerRing.next();
								} else {
									hasSocket = false;
								}
							} while (hasSocket);
						} else if (key.isReadable()) {
							// Allocate an Event object for dispatching to the
							// handler
							event = workerRing.get(workerId);
							event.id = workerId;
							channels.put(workerId,
									(SocketChannel) key.channel());
							selectionKeys.put(workerId, key);
							// Dispatch this event to a handler
							workerRing.publish(workerId);
							// Immediately allocate the next worker ID
							workerId = workerRing.next();
						}
					}
				}
			}
		}
	}

	static int safeRead(ReadableByteChannel channel, ByteBuffer dst)
			throws IOException {
		int read = -1;
		try {
			// Read data from the Channel
			read = channel.read(dst);
		} catch (IOException e) {
			switch ("" + e.getMessage()) {
			case "null":
			case "Connection reset by peer":
			case "Broken pipe":
				break;
			default:
				e.printStackTrace();
			}
			channel.close();
		} catch (CancelledKeyException e) {
			channel.close();
		}
		return read;
	}

	static int safeWrite(WritableByteChannel channel, ByteBuffer src)
			throws IOException {
		int written = -1;
		try {
			// Write the response immediately
			written = channel.write(src);
		} catch (IOException e) {
			switch ("" + e.getMessage()) {
			case "null":
			case "Connection reset by peer":
			case "Broken pipe":
				break;
			default:
				e.printStackTrace();
			}
			channel.close();
		} catch (CancelledKeyException e) {
			channel.close();
		}
		return written;
	}

	static class SelectionEvent {

		Long id;

		public SelectionEvent() {
		}

	}

}