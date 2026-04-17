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

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.async.ByteBufferFeeder;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.util.TokenBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Allows to read nonblocking a JSON stream of arbitrary size, byte array
 * chunks as TokenBuffers where each token buffer is a
 * well-formed JSON object.
 * <p>
 * Copied from Spring's Jackson2Tokenizer
 *
 */
class Tokenizer implements AutoCloseable {

	private final JsonParser parser;

	private final boolean tokenizeArrayElements;

	private TokenBuffer tokenBuffer;

	private int objectDepth;

	private int arrayDepth;

	private final ByteBufferFeeder inputFeeder;

	Tokenizer(JsonFactory jsonFactory, boolean tokenizeArrayElements) {
		this.parser = jsonFactory.createNonBlockingByteBufferParser(ObjectReadContext.empty());
		this.tokenizeArrayElements = tokenizeArrayElements;
		this.tokenBuffer = TokenBuffer.forBuffering(parser, ObjectReadContext.empty());
		this.inputFeeder = (ByteBufferFeeder) this.parser.nonBlockingInputFeeder();
	}

	List<TokenBuffer> tokenize(ByteBuffer byteBuffer) {
		inputFeeder.feedInput(byteBuffer);
		return parse();
	}

	List<TokenBuffer> endOfInput() {
		inputFeeder.endOfInput();
		return parse();
	}

	@Override
	public void close() {
		parser.close();
	}

	private List<TokenBuffer> parse() {
		List<TokenBuffer> result = new ArrayList<>();

		while (true) {
			JsonToken token = this.parser.nextToken();
			if (token == JsonToken.NOT_AVAILABLE) {
				break;
			}
			if (token == null) {
				// Spring's original Jackson2Tokenizer probed once more here because the Smile
				// binary format uses null tokens as document separators (SPR-16151). We only
				// run on JsonFactory, so any null on the JSON path is a true end-of-input.
				break;
			}
			updateDepth(token);

			if (!this.tokenizeArrayElements) {
				processTokenNormal(token, result);
			}
			else {
				processTokenArray(token, result);
			}
		}
		return result;
	}

	private void updateDepth(JsonToken token) {
		switch (token) {
			case START_OBJECT:
				this.objectDepth++;
				break;
			case END_OBJECT:
				this.objectDepth--;
				break;
			case START_ARRAY:
				this.arrayDepth++;
				break;
			case END_ARRAY:
				this.arrayDepth--;
				break;
		}
	}

	private void processTokenNormal(JsonToken token, List<TokenBuffer> result) {
		this.tokenBuffer.copyCurrentEvent(this.parser);

		if ((token.isStructEnd() || token.isScalarValue()) &&
				this.objectDepth == 0 && this.arrayDepth == 0) {
			result.add(this.tokenBuffer);
			this.tokenBuffer = TokenBuffer.forBuffering(this.parser, ObjectReadContext.empty());
		}

	}

	private void processTokenArray(JsonToken token, List<TokenBuffer> result) {
		if (!isTopLevelArrayToken(token)) {
			this.tokenBuffer.copyCurrentEvent(this.parser);
		}

		if (this.objectDepth == 0 &&
				(this.arrayDepth == 0 || this.arrayDepth == 1) &&
				(token == JsonToken.END_OBJECT || token.isScalarValue())) {
			result.add(this.tokenBuffer);
			this.tokenBuffer = TokenBuffer.forBuffering(this.parser, ObjectReadContext.empty());
		}
	}

	private boolean isTopLevelArrayToken(JsonToken token) {
		return this.objectDepth == 0 && ((token == JsonToken.START_ARRAY && this.arrayDepth == 1) ||
				(token == JsonToken.END_ARRAY && this.arrayDepth == 0));
	}

}
