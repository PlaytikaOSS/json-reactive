package reactivejson;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonParser;
import tools.jackson.core.TreeNode;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NonBlockingObjectReader")
class NonBlockingObjectReaderTest {

	/**
	 * Regression test for the {@code TokenBuffer.asParser()} bug:
	 * the no-arg overload binds the parser to {@link tools.jackson.core.ObjectReadContext#empty()},
	 * whose {@code tokenStreamFactory()}, {@code readTree(...)} and {@code readValueAs(...)} methods
	 * throw {@link UnsupportedOperationException}. Any custom deserializer that delegates back to the
	 * parser (e.g. via {@link JsonParser#readValueAsTree()}) therefore blows up.
	 * <p>
	 * The fix is to call {@code reader.readValue(tokenBuffer)} (or {@code asParser(ctxt)}), which
	 * propagates the reader's own {@code ObjectReadContext}.
	 */
	@Test
	@DisplayName("custom deserializer delegating back to the parser must work (asParser() bug)")
	void customDeserializerDelegatingBackToParserMustWork() {
		//given: an ObjectMapper whose custom deserializer uses p.readValueAsTree()
		ObjectMapper mapper = JsonMapper.builder()
				.addModule(new SimpleModule().addDeserializer(Wrapper.class, new WrapperDeserializer()))
				.build();
		JsonFactory jsonFactory = new JsonFactory();
        List<Wrapper> first;
        List<Wrapper> tail;
        try (NonBlockingObjectReader reader = new NonBlockingObjectReader(
				jsonFactory, false, mapper.readerFor(Wrapper.class))
		) {

            //when: we feed a complete object as one chunk
            String json = "{\"payload\":{\"id\":42,\"name\":\"answer\"}}";
            first = reader.readObjects(stringBuffer(json));
            tail = reader.endOfInput();
        }

        //then: the custom deserializer succeeds and the tree is round-tripped
		assertThat(first).hasSize(1);
		assertThat(tail).isEmpty();
		assertThat(first.get(0).payload.toString()).contains("\"id\":42").contains("\"name\":\"answer\"");
	}

	private static ByteBuffer stringBuffer(String value) {
		return ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
	}

	record Wrapper(TreeNode payload) {}

	static final class WrapperDeserializer extends ValueDeserializer<Wrapper> {
		@Override
		public Wrapper deserialize(JsonParser p, DeserializationContext ctxt) {
			//given: cursor is at START_OBJECT, advance to property name then to value
			p.nextToken(); // PROPERTY_NAME "payload"
			p.nextToken(); // START_OBJECT of the payload
			//when: delegate to the parser to read the nested value as a tree
			TreeNode tree = p.readValueAsTree();
			p.nextToken(); // END_OBJECT of the wrapper
			//then:
			return new Wrapper(tree);
		}
	}
}
