package reactivejson;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactivejson.domain.TestEntity;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@DisplayName("Rx3ObjectReader")
class Rx3ObjectReaderTest {

	private static final int CHUNK_SIZE = 5;

	private final ObjectMapper objectMapper = JsonMapper.builder().build();
	private final Rx3ObjectReader reader = new Rx3ObjectReader(new JsonFactory());

	@Test
	@DisplayName("should read a single entity from a chunked publisher")
	void shouldReadEntity() {
		//given:
		TestEntity testEntity = new TestEntity(7, "testName");
		Publisher<ByteBuffer> byteBuffers = stringBuffer(objectMapper.writeValueAsString(testEntity));

		//when:
		Single<TestEntity> testEntityRed = reader.read(byteBuffers, objectMapper.readerFor(TestEntity.class));

		//then:
		testEntityRed.test()
				.assertResult(testEntity);
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
		Flowable<TestEntity> testEntityRed = reader.readElements(byteBuffers,
				objectMapper.readerFor(TestEntity.class));

		//then:
		testEntityRed.test()
				.assertResult(testEntities);
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
		Publisher<ByteBuffer> byteBuffers = Flowable.fromArray(testEntities)
				.concatMap(testEntity -> stringBuffer(objectMapper.writeValueAsString(testEntity)));

		//when:
		Flowable<TestEntity> testEntityRed = reader.readElements(byteBuffers,
				objectMapper.readerFor(TestEntity.class));

		//then:
		testEntityRed.test()
				.assertResult(testEntities);
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
		Single<TestEntity[]> testEntitiesRed = reader.read(byteBuffers, objectMapper.readerFor(TestEntity[].class));

		//then:
		testEntitiesRed.test()
				.assertValue(values -> Arrays.equals(values, testEntities))
				.assertNoErrors()
				.assertComplete();
	}

	private Publisher<ByteBuffer> stringBuffer(String value) {
		return Flowable.fromIterable(divideArray(value.getBytes(StandardCharsets.UTF_8)))
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
