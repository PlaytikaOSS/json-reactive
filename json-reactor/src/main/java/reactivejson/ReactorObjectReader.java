package reactivejson;


import  org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectReader;

import java.nio.ByteBuffer;

public class ReactorObjectReader {

	private final JsonFactory jsonFactory;

	public ReactorObjectReader(JsonFactory jsonFactory) {
		this.jsonFactory = jsonFactory;
	}

	/**
	 * Decode a stream of byte buffers into a sequence of values, treating top-level
	 * JSON arrays as element streams (one element per emission).
	 */
	public <T> Flux<T> readElements(Publisher<ByteBuffer> input, ObjectReader objectReader) {
		return Flux.using(
				() -> new NonBlockingObjectReader(jsonFactory, true, objectReader),
				nbr -> this.readImpl(input, nbr),
				NonBlockingObjectReader::close);
	}

	/**
	 * Decode a stream of byte buffers into a single top-level JSON value.
	 *
	 * <p>The input must decode to exactly zero or one top-level value. If it produces
	 * more than one, the returned {@link Mono} fails with
	 * {@link IndexOutOfBoundsException} (per {@link Flux#singleOrEmpty()}); empty
	 * input completes empty.
	 */
	public <T> Mono<T> read(Publisher<ByteBuffer> input, ObjectReader objectReader) {
		return Flux.using(
				() -> new NonBlockingObjectReader(jsonFactory, false, objectReader),
				nbr -> this.<T>readImpl(input, nbr),
				NonBlockingObjectReader::close)
				.singleOrEmpty();
	}

	private <T> Flux<T> readImpl(Publisher<ByteBuffer> input, NonBlockingObjectReader reader) {
		return Flux.concat(
				Flux.from(input).concatMap(
						byteBuffer -> Flux.fromIterable(reader.readObjects(byteBuffer))),
				Flux.defer(() -> Flux.fromIterable(reader.endOfInput())));
	}
}
