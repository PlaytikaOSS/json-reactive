package reactivejson;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactivejson.domain.TestEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@DisplayName("ReactorObjectReader")
class ReactorObjectReaderTest {

	private static final int CHUNK_SIZE = 5;

	private final ObjectMapper objectMapper = JsonMapper.builder().build();
	private final ReactorObjectReader reader = new ReactorObjectReader(new JsonFactory());

	@Test
	@DisplayName("should read a single entity from a chunked publisher")
	void shouldReadEntity() {
		//given:
		TestEntity testEntity = new TestEntity(7, "testName");
		Publisher<ByteBuffer> byteBuffers = stringBuffer(objectMapper.writeValueAsString(testEntity));

		//when:
		Mono<TestEntity> testEntityRed = reader.read(byteBuffers, objectMapper.readerFor(TestEntity.class));

		//then:
		StepVerifier.create(testEntityRed)
				.expectNextMatches(testEntity_ -> testEntity_.equals(testEntity))
				.verifyComplete();
	}

	@Test
	@DisplayName("should read a stream of elements from a JSON array")
	void shouldReadElements() {
		//given:
		TestEntity[] testEntities = new TestEntity[]{
				new TestEntity(1, "testName1"),
				new TestEntity(3, "testName3"),
				new TestEntity(7, "testName7")};

		Publisher<ByteBuffer> byteBuffers = stringBuffer(objectMapper.writeValueAsString(testEntities));

		//when:
		Flux<TestEntity> testEntityRed = reader.readElements(byteBuffers, objectMapper.readerFor(TestEntity.class));

		//then:
		StepVerifier.create(testEntityRed)
				.expectNextMatches(testEntity -> testEntity.equals(testEntities[0]))
				.expectNextMatches(testEntity -> testEntity.equals(testEntities[1]))
				.expectNextMatches(testEntity -> testEntity.equals(testEntities[2]))
				.verifyComplete();
	}

	@Test
	@DisplayName("should read a sequence of JSON objects from the input publisher")
	void shouldReadSequence() {
		//given:
		TestEntity[] testEntities = new TestEntity[]{
				new TestEntity(1, "testName1"),
				new TestEntity(3, "testName3"),
				new TestEntity(7, "testName7")};

		// concatMap (not flatMap) so each entity's byte chunks complete before the next entity's chunks
		// are emitted; flatMap would interleave bytes and corrupt the downstream JSON tokenizer.
		Publisher<ByteBuffer> byteBuffers = Flux.fromArray(testEntities)
				.concatMap(testEntity -> stringBuffer(objectMapper.writeValueAsString(testEntity)));

		//when:
		Flux<TestEntity> testEntityRed = reader.readElements(byteBuffers, objectMapper.readerFor(TestEntity.class));

		//then:
		StepVerifier.create(testEntityRed)
				.expectNextMatches(testEntity -> testEntity.equals(testEntities[0]))
				.expectNextMatches(testEntity -> testEntity.equals(testEntities[1]))
				.expectNextMatches(testEntity -> testEntity.equals(testEntities[2]))
				.verifyComplete();
	}

	@Test
	@DisplayName("should read a whole JSON array as a single value")
	void shouldReadElementsAsArray() {
		//given:
		TestEntity[] testEntities = new TestEntity[]{
				new TestEntity(1, "testName1"),
				new TestEntity(3, "testName3"),
				new TestEntity(7, "testName7")};
		Publisher<ByteBuffer> byteBuffers = stringBuffer(objectMapper.writeValueAsString(testEntities));

		//when:
		Mono<TestEntity[]> testEntityRed = reader.read(byteBuffers, objectMapper.readerFor(TestEntity[].class));

		//then:
		StepVerifier.create(testEntityRed)
				.expectNextMatches(testEntities_ -> Arrays.equals(testEntities_, testEntities))
				.verifyComplete();
	}

	private Publisher<ByteBuffer> stringBuffer(String value) {
		return Flux.fromIterable(divideArray(value.getBytes(StandardCharsets.UTF_8)))
				.map(ByteBuffer::wrap);
	}

	private static List<byte[]> divideArray(byte[] source) {

		List<byte[]> result = new ArrayList<>();
		int start = 0;
		while (start < source.length) {
			int end = Math.min(source.length, start + CHUNK_SIZE);
			result.add(Arrays.copyOfRange(source, start, end));
			start += CHUNK_SIZE;
		}

		return result;
	}

}
