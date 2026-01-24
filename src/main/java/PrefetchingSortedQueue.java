
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;

public abstract class PrefetchingSortedQueue<T> {

	private static final int PREFETCH = 1000;

	private final ExecutorService executor;
	private final NavigableMap<Integer, T> buffer = new ConcurrentSkipListMap<>();
	private final Set<Integer> loading = ConcurrentHashMap.newKeySet();

	private volatile int headIndex;
	private final int maxIndex;

	protected PrefetchingSortedQueue(int startIndex, int maxIndex, ExecutorService executor) {
		this.headIndex = startIndex;
		this.maxIndex = maxIndex;
		this.executor = executor;
		prefetch();
	}

	protected abstract T load(int index);

	public Optional<T> poll() {
		final T value = buffer.remove(headIndex);
		if (value != null) {
			headIndex++;
			prefetch();
		}
		return Optional.ofNullable(value);
	}

	public T peek() {
		return buffer.get(headIndex);
	}

	private synchronized void prefetch() {
		int end = Math.min(headIndex + PREFETCH, maxIndex + 1);

		for (int i = headIndex; i < end; i++) {
			if (buffer.containsKey(i))
				continue;
			if (!loading.add(i))
				continue;

			final int j = i;
			executor.submit(() -> {
				try {
					T value = load(j);
					if (value != null) {
						buffer.put(j, value);
					}
				} finally {
					loading.remove(j);
				}
			});
		}
	}

	public int size() {
		return buffer.size();
	}
}
