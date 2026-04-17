package reactivejson;


import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.reactivestreams.Publisher;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectReader;

import java.nio.ByteBuffer;

public class Rx3ObjectReader {

	private final JsonFactory jsonFactory;

	public Rx3ObjectReader(JsonFactory jsonFactory) {
		this.jsonFactory = jsonFactory;
	}

	/**
	 * Decode a stream of byte buffers into a sequence of values, treating top-level
	 * JSON arrays as element streams (one element per emission).
	 */
	public <T> Flowable<T> readElements(Publisher<ByteBuffer> input, ObjectReader objectReader) {
		return Flowable.using(
				() -> new NonBlockingObjectReader(jsonFactory, true, objectReader),
				nbr -> this.readImpl(input, nbr),
				NonBlockingObjectReader::close);
	}

	/**
	 * Decode a stream of byte buffers into a single top-level JSON value.
	 *
	 * <p>The input must decode to at least one top-level value. If it produces zero,
	 * the returned {@link Single} fails with {@link java.util.NoSuchElementException}
	 * (per {@link Flowable#firstOrError()}).
	 */
	public <T> Single<T> read(Publisher<ByteBuffer> input, ObjectReader objectReader) {
		return Flowable.using(
				() -> new NonBlockingObjectReader(jsonFactory, false, objectReader),
				nbr -> this.<T>readImpl(input, nbr),
				NonBlockingObjectReader::close)
				.firstOrError();
	}

	private <T> Flowable<T> readImpl(Publisher<ByteBuffer> input, NonBlockingObjectReader reader) {
		return Flowable.fromPublisher(input)
				.concatMap(byteBuffer -> Flowable.fromIterable(reader.<T>readObjects(byteBuffer)))
				.concatWith(Flowable.defer(() -> Flowable.fromIterable(reader.endOfInput())));
	}
}
