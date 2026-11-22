package be.jorisclaes.gatesplus.storage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs all storage calls on one background thread (keeps write order) so the server tick never
 * waits on the database. Also records the numbers shown by /gp devstats.
 */
public final class AsyncStorage {
	private static final Logger LOGGER = LoggerFactory.getLogger("gatesplus/storage");

	@FunctionalInterface
	public interface StorageCall<T> {
		T run(GateStorage storage) throws StorageException;
	}

	@FunctionalInterface
	public interface StorageAction {
		void run(GateStorage storage) throws StorageException;
	}

	private final GateStorage storage;
	private final ExecutorService executor;
	private final AtomicInteger pending = new AtomicInteger();
	private final AtomicLong completed = new AtomicLong();
	private final AtomicLong totalNanos = new AtomicLong();
	private volatile String lastError;

	public AsyncStorage(GateStorage storage) {
		this.storage = storage;
		this.executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "GatesPlus-Storage");
			t.setDaemon(true);
			return t;
		});
	}

	public GateStorage blocking() {
		return storage;
	}

	public <T> CompletableFuture<T> call(StorageCall<T> call) {
		pending.incrementAndGet();
		return CompletableFuture.supplyAsync(() -> {
			long start = System.nanoTime();
			try {
				return call.run(storage);
			} catch (StorageException e) {
				lastError = e.getMessage();
				LOGGER.error("Storage error", e);
				throw new CompletionException(e);
			} finally {
				totalNanos.addAndGet(System.nanoTime() - start);
				completed.incrementAndGet();
				pending.decrementAndGet();
			}
		}, executor);
	}

	/** Fire-and-forget write; errors are logged and shown in /gp devstats. */
	public CompletableFuture<Void> run(StorageAction action) {
		return call(s -> {
			action.run(s);
			return null;
		});
	}

	public int pendingCount() {
		return pending.get();
	}

	public double averageMillis() {
		long n = completed.get();
		return n == 0 ? 0 : totalNanos.get() / 1_000_000.0 / n;
	}

	public String lastError() {
		return lastError;
	}

	public String describe() {
		return storage.describe();
	}

	/** Waits for queued writes, then closes the connection pool. */
	public void shutdown() {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
				LOGGER.warn("GatesPlus storage did not finish {} pending writes in time", pending.get());
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		storage.close();
	}
}
