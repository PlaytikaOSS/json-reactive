package reactivejson;

import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.util.TokenBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stateful, single-stream wrapper around a non-blocking Jackson parser.
 * Not thread-safe. Owns a {@link tools.jackson.core.JsonParser}; callers
 * must invoke {@link #close()} when finished.
 */
public class NonBlockingObjectReader implements AutoCloseable {

	private final Tokenizer tokenizer;
	private final ObjectReader reader;

	public NonBlockingObjectReader(
			JsonFactory jsonFactory, boolean tokenizeArrayElements,
			ObjectReader reader) {

		this.tokenizer = new Tokenizer(jsonFactory, tokenizeArrayElements);
		this.reader = reader;
	}

	public <T> List<T> readObjects(ByteBuffer byteBuffer) {
		return readFrom(tokenizer.tokenize(byteBuffer));
	}

	public <T> List<T> endOfInput() {
		return readFrom(tokenizer.endOfInput());
	}

	@Override
	public void close() {
		tokenizer.close();
	}

	private <T> List<T> readFrom(List<TokenBuffer> tokenBuffers) {
		if (tokenBuffers.isEmpty()) {
			return Collections.emptyList();
		}
		List<T> objects = new ArrayList<>(tokenBuffers.size());
		for (TokenBuffer tokenBuffer : tokenBuffers) {
			// Pass the TokenBuffer directly so ObjectReader binds the parser to its own
			// ObjectReadContext (DeserializationContextExt). The asParser() no-arg overload
			// would use ObjectReadContext.empty(), breaking custom deserializers that delegate
			// back to the parser (e.g. p.readValueAsTree() / p.readValueAs(...)).
			objects.add(reader.readValue(tokenBuffer));
		}
		return objects;
	}

}
