/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactivejson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.core.TreeNode;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.TokenBuffer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tokenizer")
class TokenizerTest {

	private ObjectReader objectReader;

	private JsonFactory jsonFactory;

	@BeforeEach
	void createReader() {
		//given: a fresh factory and reader per test
		this.jsonFactory = new JsonFactory();
		ObjectMapper mapper = JsonMapper.builder(jsonFactory).build();
		this.objectReader = mapper.reader();
	}

	@Nested
	@DisplayName("when array elements are not tokenized")
	class DoNotTokenizeArrayElements {

		@Test
		@DisplayName("should emit whole top-level objects, arrays and scalars")
		void doNotTokenizeArrayElements() {
			//given: single complete object
			//when/then:
			testTokenize(
					singletonList("{\"foo\": \"foofoo\", \"bar\": \"barbar\"}"),
					singletonList("{\"foo\": \"foofoo\", \"bar\": \"barbar\"}"), false);

			//given: object split across two chunks
			//when/then:
			testTokenize(
					asList("{\"foo\": \"foofoo\"",
							", \"bar\": \"barbar\"}"),
					singletonList("{\"foo\":\"foofoo\",\"bar\":\"barbar\"}"), false);

			//given: top-level array as single chunk
			//when/then:
			testTokenize(
					singletonList("[" +
							"{\"foo\": \"foofoo\", \"bar\": \"barbar\"}," +
							"{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}]"),
					singletonList("[" +
							"{\"foo\": \"foofoo\", \"bar\": \"barbar\"}," +
							"{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}]"), false);

			testTokenize(
					singletonList("[{\"foo\": \"bar\"},{\"foo\": \"baz\"}]"),
					singletonList("[{\"foo\": \"bar\"},{\"foo\": \"baz\"}]"), false);

			testTokenize(
					asList("[" +
							"{\"foo\": \"foofoo\", \"bar\"", ": \"barbar\"}," +
							"{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}]"),
					singletonList("[" +
							"{\"foo\": \"foofoo\", \"bar\": \"barbar\"}," +
							"{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}]"), false);

			testTokenize(
					asList("[",
							"{\"id\":1,\"name\":\"Robert\"}", ",",
							"{\"id\":2,\"name\":\"Raide\"}", ",",
							"{\"id\":3,\"name\":\"Ford\"}", "]"),
					singletonList("[" +
							"{\"id\":1,\"name\":\"Robert\"}," +
							"{\"id\":2,\"name\":\"Raide\"}," +
							"{\"id\":3,\"name\":\"Ford\"}]"), false);

			//given: top-level scalar values split across chunks (SPR-16166)
			//when/then:
			testTokenize(asList("\"foo", "bar\""), singletonList("\"foobar\""), false);
			testTokenize(asList("12", "34"), singletonList("1234"), false);
			testTokenize(asList("12.", "34"), singletonList("12.34"), false);
		}
	}

	@Nested
	@DisplayName("when array elements are tokenized")
	class TokenizeArrayElements {

		@Test
		@DisplayName("should emit each top-level element independently")
		void tokenizeArrayElements() {
			testTokenize(
					singletonList("{\"foo\": \"foofoo\", \"bar\": \"barbar\"}"),
					singletonList("{\"foo\": \"foofoo\", \"bar\": \"barbar\"}"), true);

			testTokenize(
					asList("{\"foo\": \"foofoo\"", ", \"bar\": \"barbar\"}"),
					singletonList("{\"foo\":\"foofoo\",\"bar\":\"barbar\"}"), true);

			testTokenize(
					singletonList("[" +
							"{\"foo\": \"foofoo\", \"bar\": \"barbar\"}," +
							"{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}]"),
					asList(
							"{\"foo\": \"foofoo\", \"bar\": \"barbar\"}",
							"{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}"), true);

			testTokenize(
					singletonList("[{\"foo\": \"bar\"},{\"foo\": \"baz\"}]"),
					asList("{\"foo\": \"bar\"}", "{\"foo\": \"baz\"}"), true);

			//given: nested array inside each element (SPR-15803)
			//when/then:
			testTokenize(
					singletonList("[" +
							"{\"id\":\"0\",\"start\":[-999999999,1,1],\"end\":[999999999,12,31]}," +
							"{\"id\":\"1\",\"start\":[-999999999,1,1],\"end\":[999999999,12,31]}," +
							"{\"id\":\"2\",\"start\":[-999999999,1,1],\"end\":[999999999,12,31]}" +
							"]"),
					asList(
							"{\"id\":\"0\",\"start\":[-999999999,1,1],\"end\":[999999999,12,31]}",
							"{\"id\":\"1\",\"start\":[-999999999,1,1],\"end\":[999999999,12,31]}",
							"{\"id\":\"2\",\"start\":[-999999999,1,1],\"end\":[999999999,12,31]}"), true);

			//given: nested array without a top-level array (SPR-15803)
			//when/then:
			testTokenize(
					singletonList("{\"speakerIds\":[\"tastapod\"],\"language\":\"ENGLISH\"}"),
					singletonList("{\"speakerIds\":[\"tastapod\"],\"language\":\"ENGLISH\"}"), true);

			testTokenize(
					asList("[" +
							"{\"foo\": \"foofoo\", \"bar\"", ": \"barbar\"}," +
							"{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}]"),
					asList(
							"{\"foo\": \"foofoo\", \"bar\": \"barbar\"}",
							"{\"foo\": \"foofoofoo\", \"bar\": \"barbarbar\"}"), true);

			testTokenize(
					asList("[",
							"{\"id\":1,\"name\":\"Robert\"}",
							",",
							"{\"id\":2,\"name\":\"Raide\"}",
							",",
							"{\"id\":3,\"name\":\"Ford\"}",
							"]"),
					asList("{\"id\":1,\"name\":\"Robert\"}",
							"{\"id\":2,\"name\":\"Raide\"}",
							"{\"id\":3,\"name\":\"Ford\"}"), true);

			testTokenize(asList("\"foo", "bar\""), singletonList("\"foobar\""), true);
			testTokenize(asList("12", "34"), singletonList("1234"), true);
			testTokenize(asList("12.", "34"), singletonList("12.34"), true);

			//given: top-level scalars inside array (SPR-16407)
			//when/then:
			testTokenize(asList("[1", ",2,", "3]"), asList("1", "2", "3"), true);
		}
	}

	@Test
	@DisplayName("should read a sequence of top-level JSON objects")
	void shouldReadSequence() {
		//given: two back-to-back objects
		//when: tokenized with array-elements true and false
		//then: both modes return the two objects verbatim
		testTokenize(
				asList("{\"foo\": \"foofoo1\", \"bar\": \"barbar1\"}", "{\"foo\": \"foofoo2\", \"bar\": \"barbar2\"}"),
				asList("{\"foo\": \"foofoo1\", \"bar\": \"barbar1\"}", "{\"foo\": \"foofoo2\", \"bar\": \"barbar2\"}"),
				true);

		testTokenize(
				asList("{\"foo\": \"foofoo1\", \"bar\": \"barbar1\"}", "{\"foo\": \"foofoo2\", \"bar\": \"barbar2\"}"),
				asList("{\"foo\": \"foofoo1\", \"bar\": \"barbar1\"}", "{\"foo\": \"foofoo2\", \"bar\": \"barbar2\"}"),
				false);
	}

	private void testTokenize(List<String> source, List<String> expected, boolean tokenizeArrayElements) {
		//given:
		List<TreeNode> expectedTrees = expected.stream()
				.map(objectReader::readTree)
				.collect(Collectors.toList());

        List<TokenBuffer> tokenBuffers;
        try (Tokenizer tokenizer = new Tokenizer(this.jsonFactory, tokenizeArrayElements)) {

            //when:
            tokenBuffers = new ArrayList<>(source.size());
            for (String s : source) {
                tokenBuffers.addAll(tokenizer.tokenize(stringBuffer(s)));
            }
            tokenBuffers.addAll(tokenizer.endOfInput());
        }

        List<TreeNode> actual = new ArrayList<>(source.size());
		tokenBuffers.forEach(tokenBuffer -> actual.add(this.objectReader.readTree(tokenBuffer.asParser())));

		//then:
		assertThat(actual).containsExactlyElementsOf(expectedTrees);
	}

	private ByteBuffer stringBuffer(String value) {
		return ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
	}

}
