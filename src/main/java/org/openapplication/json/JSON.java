/**
 * Copyright 2012 Erik Isaksson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openapplication.json;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class JSON implements JSONSerializer, Closeable {

	public static Object parse(CharSequence text) throws JSONException {
		return parse(text, DEFAULT_REVIVER);
	}

	public static Object parse(CharSequence text, JSONReviver reviver)
			throws JSONException {
		ValueBuilder builder = new ValueBuilder(reviver);
		parse(text, builder);
		return builder.getValue();
	}

	public static String stringify(Object value) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			stringify(value, new JSON(out));
			return out.toString("UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static String stringify(Object value, String space) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			stringify(value, new PrettyJSON(out, space));
			return out.toString("UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static String stringify(Object value, int space) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			stringify(value, new PrettyJSON(out, space));
			return out.toString("UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static String stringify(Object value, JSONReplacer replacer) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			stringify(value, replacer, new JSON(out));
			return out.toString("UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static String stringify(Object value, JSONReplacer replacer,
			String space) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			stringify(value, replacer, new PrettyJSON(out, space));
			return out.toString("UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static String stringify(Object value, JSONReplacer replacer,
			int space) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			stringify(value, replacer, new PrettyJSON(out, space));
			return out.toString("UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void parse(CharSequence text, JSONCallback callback)
			throws JSONException {
		Context context = new Context();
		context.callback = callback;

		int length = text.length();
		for (int i = 0; i < length; i++)
			parseChar(text.charAt(i), context);
		parseEOF(context);
	}

	public static void stringify(Object value, JSON out) throws IOException {
		out.write(value);
	}

	public static void stringify(Object value, JSONReplacer replacer, JSON out)
			throws IOException {
		if (replacer != null)
			replacer.replace(null, null, value, out);
		else
			out.write(value);
	}

	private static class ValueBuilder implements JSONCallback {

		final Deque<Object> stack = new ArrayDeque<Object>();

		final List<Object> root;

		final JSONReviver reviver;

		String fieldName = "";

		final Deque<String> fieldNames = new ArrayDeque<String>();

		ValueBuilder(JSONReviver reviver) {
			stack.push(root = new ArrayList<Object>(1));
			this.reviver = reviver != null ? reviver : DEFAULT_REVIVER;
		}

		@SuppressWarnings("unchecked")
		@Override
		public void token(JSONToken token, String value) throws JSONException {
			Object element = null;
			switch (token) {
			case START_OBJECT:
				stack.push(element = new HashMap<String, Object>());
				fieldNames.push(fieldName);
				return;
			case END_OBJECT:
				element = stack.pop();
				fieldName = fieldNames.pop();
				break;
			case START_ARRAY:
				stack.push(element = new ArrayList<Object>());
				fieldNames.push(fieldName);
				return;
			case END_ARRAY:
				element = stack.pop();
				fieldName = fieldNames.pop();
				break;
			case FIELD_NAME:
				fieldName = value;
				return;
			case VALUE_STRING:
				element = value;
				break;
			case VALUE_NUMBER:
				element = new BigDecimal(value);
				break;
			case VALUE_TRUE:
				element = Boolean.TRUE;
				break;
			case VALUE_FALSE:
				element = Boolean.FALSE;
				break;
			case VALUE_NULL:
				element = null;
				break;
			}
			Object container = stack.peek();
			if (container instanceof Map<?, ?>)
				((Map<String, Object>) container).put(fieldName,
						reviver.revive(container, fieldName, element));
			else if (container instanceof List<?>)
				((List<Object>) container).add(reviver.revive(container,
						container != root ? ((List<Object>) container).size()
								: null, element));
		}

		Object getValue() {
			return root.get(0);
		}

	}

	private static final JSONReviver DEFAULT_REVIVER = new JSONReviver() {

		@Override
		public Object revive(Object holder, Object key, Object value) {
			return value;
		}

	};

	public static Object parse(InputStream in) throws JSONException,
			IOException {
		return parse(in, DEFAULT_REVIVER);
	}

	public static Object parse(InputStream in, JSONReviver reviver)
			throws JSONException, IOException {
		ValueBuilder builder = new ValueBuilder(reviver);
		parse(in, builder);
		return builder.getValue();
	}

	public static void parse(InputStream in, JSONCallback callback)
			throws JSONException, IOException {
		Context context = new Context();
		context.callback = callback;

		// Attempt reading the first bytes
		byte[] buffer = new byte[32]; // At least 4 bytes
		int read = 0, size = 0;
		while (size < 4 // We need at least but not more than 4 bytes for
						// determining the encoding
				&& (read = in.read(buffer, size, buffer.length - size)) != -1)
			size += read;

		// If the stream is (or was) long enough for determining the encoding
		int i = 0;
		if (size >= 4) {
			// Determine encoding from Unicode byte order mark, or pattern of
			// nulls (as noted in RFC 4627,
			// "the first two characters of a JSON text will always be ASCII characters")
			if ((buffer[0] == 0 && buffer[1] == 0 && (buffer[2] & 0xFF) == 0xFE && (buffer[3] & 0xFF) == 0xFF)
					|| (buffer[0] == 0 && buffer[1] == 0 && buffer[2] == 0)) {
				// 00 00 FE FF or
				// 00 00 00 xx: UTF-32BE
				if ((buffer[2] & 0xFF) == 0xFE)
					i += 4; // Skip byte order mark

				// Decode UTF-32BE
				int codepoint;
				for (;;) {
					while (i + 3 < size) {
						codepoint = ((buffer[i++] & 0xFF) << 24)
								| ((buffer[i++] & 0xFF) << 16)
								| ((buffer[i++] & 0xFF) << 8)
								| (buffer[i++] & 0xFF);
						if (codepoint <= Character.MAX_VALUE)
							// Basic Multilingual Plane
							if (codepoint < Character.MIN_SURROGATE
									|| codepoint > Character.MAX_SURROGATE)
								parseChar((char) codepoint, context);
							else
								// Is a surrogate and to be regarded as an error
								throw new JSONException(
										"Invalid JSON: invalid UTF-32BE encoding (invalid codepoint)");
						else if (codepoint <= Character.MAX_CODE_POINT) {
							// In the Supplementary Planes
							// Two UTF-16 code points are required
							codepoint -= Character.MIN_SUPPLEMENTARY_CODE_POINT;
							parseChar(
									(char) (Character.MIN_SURROGATE + (codepoint >>> 10)),
									context);
							parseChar(
									(char) (Character.MIN_LOW_SURROGATE + (codepoint & 0x3FF)),
									context);
						} else
							// Invalid codepoint > 0x10FFFF
							throw new JSONException(
									"Invalid JSON: invalid UTF-32BE encoding (invalid codepoint)");
					}
					System.arraycopy(buffer, i, buffer, 0, size = size - i);
					read = in.read(buffer, size, buffer.length - size);
					if (read != -1) {
						size += read;
						i = 0;
					} else {
						if (size > 0) // EOF within multibyte sequence
							throw new JSONException(
									"Invalid JSON: invalid UTF-32BE encoding (unexpected EOF)");
						break;
					}
				}
				parseEOF(context);
				return;

			} else if (((buffer[0] & 0xFF) == 0xFE && (buffer[1] & 0xFF) == 0xFF)
					|| (buffer[0] == 0 && buffer[2] == 0)) {
				// FE FF or
				// 00 xx 00 xx: UTF-16BE
				if ((buffer[0] & 0xFF) == 0xFE)
					i += 2; // Skip byte order mark

				// Decode UTF-16BE
				for (;;) {
					while (i + 1 < size)
						parseChar(
								(char) (((buffer[i++] & 0xFF) << 8) | (buffer[i++] & 0xFF)),
								context);
					System.arraycopy(buffer, i, buffer, 0, size = size - i);
					read = in.read(buffer, size, buffer.length - size);
					if (read != -1) {
						size += read;
						i = 0;
					} else {
						if (size > 0) // EOF within multibyte sequence
							throw new JSONException(
									"Invalid JSON: invalid UTF-16BE encoding (unexpected EOF)");
						break;
					}
				}
				parseEOF(context);
				return;

			} else if (((buffer[0] & 0xFF) == 0xFF
					&& (buffer[1] & 0xFF) == 0xFE && buffer[2] == 0 && buffer[3] == 0)
					|| (buffer[1] == 0 && buffer[2] == 0 && buffer[3] == 0)) {
				// FF FE 00 00 or
				// xx 00 00 00: UTF-32LE
				if ((buffer[1] & 0xFF) == 0xFE)
					i += 2; // Skip byte order mark

				// Decode UTF-32LE
				int codepoint;
				for (;;) {
					while (i + 3 < size) {
						codepoint = (buffer[i++] & 0xFF)
								| ((buffer[i++] & 0xFF) << 8)
								| ((buffer[i++] & 0xFF) << 16)
								| ((buffer[i++] & 0xFF) << 24);
						if (codepoint <= Character.MAX_VALUE) {
							// Basic Multilingual Plane
							if (codepoint < Character.MIN_SURROGATE
									|| codepoint > Character.MAX_SURROGATE)
								parseChar((char) codepoint, context);
							else
								// Is a surrogate and to be regarded as an error
								throw new JSONException(
										"Invalid JSON: invalid UTF-32LE encoding (invalid codepoint)");
						} else if (codepoint <= Character.MAX_CODE_POINT) {
							// In the Supplementary Planes
							// Two UTF-16 code points are required
							codepoint -= Character.MIN_SUPPLEMENTARY_CODE_POINT;
							parseChar(
									(char) (Character.MIN_SURROGATE + (codepoint >>> 10)),
									context);
							parseChar(
									(char) (Character.MIN_LOW_SURROGATE + (codepoint & 0x3FF)),
									context);
						} else
							// Invalid codepoint > max
							throw new JSONException(
									"Invalid JSON: invalid UTF-32LE encoding (invalid codepoint)");
					}
					System.arraycopy(buffer, i, buffer, 0, size = size - i);
					read = in.read(buffer, size, buffer.length - size);
					if (read != -1) {
						size += read;
						i = 0;
					} else {
						if (size > 0) // EOF within multibyte sequence
							throw new JSONException(
									"Invalid JSON: invalid UTF-32LE encoding (unexpected EOF)");
						break;
					}
				}
				parseEOF(context);
				return;

			} else if (((buffer[0] & 0xFF) == 0xFF && (buffer[1] & 0xFF) == 0xFE)
					|| (buffer[1] == 0 && buffer[3] == 0)) {
				// FF FE or
				// xx 00 xx 00: UTF-16LE
				if ((buffer[1] & 0xFF) == 0xFE)
					i += 2; // Skip byte order mark

				// Decode UTF-16LE
				for (;;) {
					while (i + 1 < size)
						parseChar(
								(char) ((buffer[i++] & 0xFF) | ((buffer[i++] & 0xFF) << 8)),
								context);
					System.arraycopy(buffer, i, buffer, 0, size = size - i);
					read = in.read(buffer, size, buffer.length - size);
					if (read != -1) {
						size += read;
						i = 0;
					} else {
						if (size > 0) // EOF within multibyte sequence
							throw new JSONException(
									"Invalid JSON: invalid UTF-16LE encoding (unexpected EOF)");
						break;
					}
				}
				parseEOF(context);
				return;

			} else if ((buffer[0] & 0xFF) == 0xEF && (buffer[1] & 0xFF) == 0xBB
					&& (buffer[2] & 0xFF) == 0xBF) {
				// EF BB BF: UTF-8
				i += 3; // Skip byte order mark
			} else
				// xx xx xx xx: UTF-8
				;
		} else if (size == 3
				&& ((buffer[0] & 0xFF) == 0xEF && (buffer[1] & 0xFF) == 0xBB && (buffer[2] & 0xFF) == 0xBF)) {
			// Stream only contains a UTF-8 byte order mark
			parseEOF(context);
			return;
		} else if (size == 0) {
			parseEOF(context);
			return;
		}

		// No encoding could be determined or UTF-8 was determined
		// Decode UTF-8
		int b, octets = 0, remaining = 0, codepoint = 0;
		for (;;) {
			while (i < size) {
				b = buffer[i] & 0xFF;
				if (remaining == 0) {
					if ((b >>> 7) == 0) // 0xxxxxxx, 7 bits
						parseChar((char) b, context);
					else if ((b >>> 5) == 0x6) { // 110xxxxx, 11 bits
						remaining = (octets = 2) - 1;
						codepoint = (b & 0x1F) << 6;
					} else if ((b >>> 4) == 0xE) { // 1110xxxx, 16 bits
						remaining = (octets = 3) - 1;
						codepoint = (b & 0xF) << 12;
					} else if ((b >>> 3) == 0x1E) { // 11110xxx, 21 bits
						remaining = (octets = 4) - 1;
						codepoint = (b & 0x7) << 18;
					} else if ((b >>> 2) == 0x3E) { // 111110xx, 26 bits
						// Overlong for any code point, but try to process the
						// whole sequence and then throw an exception
						remaining = (octets = 5) - 1;
						codepoint = (b & 0x3) << 24;
					} else if ((b >>> 1) == 0x7E) { // 1111110x, 31 bits
						// Overlong for any code point, but try to process the
						// whole sequence and then throw an exception
						remaining = (octets = 6) - 1;
						codepoint = (b & 0x1) << 30;
					} else
						throw new JSONException(
								"Invalid JSON: invalid UTF-8 encoding (invalid sequence)");
				} else if ((b >>> 6) == 0x2) { // 10xxxxxx
					codepoint |= (b & 0x3F) << (6 * --remaining);
					if (remaining == 0) {
						if (codepoint <= 0x7F // octets > 1
								|| (codepoint <= 0x7FF && octets > 2)
								|| (codepoint <= Character.MAX_VALUE && octets > 3)
								|| (codepoint <= Character.MAX_CODE_POINT && octets > 4))
							// Overlong sequence (see RFC 3629)
							throw new JSONException(
									"Invalid JSON: invalid UTF-8 encoding (overlong sequence)");
						else if (codepoint < Character.MIN_SURROGATE)
							// Basic Multilingual Plane below surrogates
							parseChar((char) codepoint, context);
						else if (codepoint <= Character.MAX_VALUE) {
							if (codepoint > Character.MAX_SURROGATE)
								// Basic Multilingual Plane above surrogates
								parseChar((char) codepoint, context);
							else
								// Is a surrogate and to be regarded as an error
								throw new JSONException(
										"Invalid JSON: invalid UTF-8 encoding (invalid codepoint)");
						} else if (codepoint <= Character.MAX_CODE_POINT) {
							// In the Supplementary Planes
							// Two UTF-16 code points are required
							codepoint -= Character.MIN_SUPPLEMENTARY_CODE_POINT;
							parseChar(
									(char) (Character.MIN_SURROGATE + (codepoint >>> 10)),
									context);
							parseChar(
									(char) (Character.MIN_LOW_SURROGATE + (codepoint & 0x3FF)),
									context);
						} else
							// Invalid codepoint > max
							throw new JSONException(
									"Invalid JSON: invalid UTF-8 encoding (invalid codepoint)");
					}
				} else {
					// remaining = 0;
					throw new JSONException(
							"Invalid JSON: invalid UTF-8 encoding (invalid sequence)");
				}
				i++;
			}
			read = in.read(buffer);
			if (read != -1) {
				size = read;
				i = 0;
			} else {
				if (remaining > 0) // EOF within multibyte sequence
					throw new JSONException(
							"Invalid JSON: invalid UTF-8 encoding (unexpected EOF)");
				break;
			}
		}
		parseEOF(context);

	}

	private static class Context {

		JSONCallback callback;

		Deque<Mode> mode = new ArrayDeque<Mode>();

		StringBuilder value = new StringBuilder();

		int codepoint;

		int remaining;

		Context() {
			mode.push(Mode.ROOT);
		}

	}

	private static enum Mode {

		ROOT, TEXT, VALUE, VALUE_REQUIRED, OBJECT, ARRAY, MEMBER, MEMBER_REQUIRED, MEMBER_VALUE, STRING, NUMBER, ESCAPE, CODEPOINT, SURROGATE, LITERAL

	}

	static final Pattern REGEX_NUMBER = Pattern
			.compile("-?(?=[1-9]|0(?!\\d))\\d+(\\.\\d+)?([eE][+-]?\\d+)?");

	private static void parseChar(char c, Context context) throws JSONException {
		mode: switch (context.mode.peek()) {
		case ROOT:
			switch (c) { // JSON-text = object / array
			case '{': // object
				context.mode.push(Mode.TEXT);
				context.mode.push(Mode.OBJECT);
				context.callback.token(JSONToken.START_OBJECT, null);
				break;
			case '[': // array
				context.mode.push(Mode.TEXT);
				context.mode.push(Mode.ARRAY);
				context.mode.push(Mode.VALUE);
				context.callback.token(JSONToken.START_ARRAY, null);
				break;
			default:
				if (!isWhitespace(c))
					throw new JSONException(
							"Invalid JSON: expected object / array");
			}
			break;
		case TEXT:
			if (!isWhitespace(c))
				throw new JSONException("Invalid JSON: expected EOF");
		case VALUE_REQUIRED: // Disallow trailing commas in arrays
			if (c == ']')
				throw new JSONException("Invalid JSON: trailing comma");
			if (isWhitespace(c))
				break;
			context.mode.pop(); // VALUE_REQUIRED
			// Continue to case VALUE
		case VALUE:
			switch (c) { // value = false / null / true / object / array /
							// number / string
			case '"': // string
				context.mode.push(Mode.STRING);
				break;
			case '{': // object
				context.mode.push(Mode.OBJECT);
				context.callback.token(JSONToken.START_OBJECT, null);
				break;
			case '[': // array
				context.mode.push(Mode.ARRAY);
				context.mode.push(Mode.VALUE);
				context.callback.token(JSONToken.START_ARRAY, null);
				break;
			case ']': // end-array
				context.mode.pop(); // VALUE
				if (context.mode.peek() == Mode.ARRAY)
					parseChar(c, context); // ARRAY
				else
					throw new JSONException("Invalid JSON: expected value");
				break;
			case 't': // true
				context.mode.push(Mode.LITERAL);
				context.value.append(c);
				context.remaining = 3;
				break;
			case 'f': // false
				context.mode.push(Mode.LITERAL);
				context.value.append(c);
				context.remaining = 4;
				break;
			case 'n': // null
				context.mode.push(Mode.LITERAL);
				context.value.append(c);
				context.remaining = 3;
				break;
			case '-': // number (leading digit of int or minus sign)
			case '0': // 0 only allowed if int is zero; otherwise will be caught
						// by regex as leading zeros are not allowed
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				context.mode.push(Mode.NUMBER);
				context.value.append(c);
				break;
			default:
				if (!isWhitespace(c))
					throw new JSONException("Invalid JSON: expected value");
			}
			break;
		case MEMBER_REQUIRED:
			if (c == '}') // Disallow trailing commas in objects
				throw new JSONException("Invalid JSON: trailing comma");
			if (isWhitespace(c))
				break;
			context.mode.pop(); // MEMBER_REQUIRED
			// Continue to case OBJECT
		case OBJECT:
			switch (c) { // object = begin-object [ member *( value-separator
							// member ) ] end-object
			case '"': // member
				context.mode.push(Mode.MEMBER);
				context.mode.push(Mode.STRING);
				break;
			case '}': // end-object
				context.mode.pop(); // OBJECT
				if (context.mode.peek() == Mode.VALUE)
					context.mode.pop(); // VALUE
				context.callback.token(JSONToken.END_OBJECT, null);
				break;
			default:
				if (!isWhitespace(c))
					throw new JSONException(
							"Invalid JSON: expected string / end-object");
			}
			break;
		case MEMBER:
			switch (c) { // member = string name-separator value
			case ':': // name-separator
				context.mode.push(Mode.MEMBER_VALUE);
				context.mode.push(Mode.VALUE);
				break;
			default:
				if (!isWhitespace(c))
					throw new JSONException(
							"Invalid JSON: expected name-separator");
			}
			break;
		case MEMBER_VALUE:
			switch (c) { // *( value-separator member ) ] end-object
			case ',': // value-separator
				context.mode.pop(); // MEMBER_VALUE
				context.mode.pop(); // MEMBER
				context.mode.push(Mode.MEMBER_REQUIRED);
				break;
			case '}': // end-object
				context.mode.pop(); // MEMBER_VALUE
				context.mode.pop(); // MEMBER
				parseChar(c, context); // OBJECT
				break;
			default:
				if (!isWhitespace(c))
					throw new JSONException(
							"Invalid JSON: expected value-separator / end-object");
			}
			break;
		case STRING:
			switch (c) { // quotation-mark *char quotation-mark
			case '"':
				context.mode.pop(); // STRING
				switch (context.mode.peek()) {
				case MEMBER:
					context.callback.token(JSONToken.FIELD_NAME,
							context.value.toString());
					break;
				case VALUE:
					context.mode.pop(); // VALUE
					// No break
				default: // MEMBER_VALUE:
					context.callback.token(JSONToken.VALUE_STRING,
							context.value.toString());
				}
				context.value.delete(0, Integer.MAX_VALUE);
				break;
			case '\\':
				context.mode.push(Mode.ESCAPE);
				break;
			default:
				context.value.append(c);
			}
			break;
		case ESCAPE:
			char e;
			switch (c) {
			case '"': // quotation mark U+0022
			case '\\': // reverse solidus U+005C
			case '/': // solidus U+002F
				context.value.append(c);
				context.mode.pop(); // ESCAPE
				break mode;
			case 'b': // backspace U+0008
				e = '\b';
				break;
			case 'f': // form feed U+000C
				e = '\f';
				break;
			case 'n': // line feed U+000A
				e = '\n';
				break;
			case 'r': // carriage return U+000D
				e = '\r';
				break;
			case 't': // tab U+0009
				e = '\t';
				break;
			case 'u': // uXXXX U+XXXX
				context.mode.push(Mode.CODEPOINT);
				context.remaining = 4;
				context.codepoint = 0;
				break mode;
			default:
				throw new JSONException("Invalid JSON: invalid escape sequence");
			}
			context.value.append(e); // e
			context.mode.pop(); // ESCAPE
			break;
		case CODEPOINT:
			int digit = -1;
			if (c <= '9') {
				if (c >= '0')
					digit = (int) (c - '0');
			} else if (c <= 'F') {
				if (c >= 'A')
					digit = (int) (c - 55); // 55='A'-0xA
			} else if (c <= 'f')
				if (c >= 'a')
					digit = (c - 87); // 87='a'-0xA
			if (digit == -1)
				throw new JSONException("Invalid JSON: invalid escape sequence");
			context.codepoint |= digit << 4 * --context.remaining;
			if (context.remaining == 0) {
				context.value.append((char) context.codepoint);
				context.mode.pop(); // CODEPOINT
				if (context.mode.peek() == Mode.SURROGATE) {
					if (context.codepoint < Character.MIN_LOW_SURROGATE
							|| context.codepoint > Character.MAX_SURROGATE)
						throw new JSONException(
								"Invalid JSON: invalid escaped surrogate pair");
					context.mode.pop(); // SURROGATE
					context.mode.pop(); // ESCAPE
				} else if (context.codepoint >= Character.MIN_SURROGATE
						&& context.codepoint <= Character.MAX_SURROGATE) {
					if (context.codepoint >= Character.MIN_LOW_SURROGATE)
						throw new JSONException(
								"Invalid JSON: invalid escaped surrogate pair");
					context.mode.push(Mode.SURROGATE);
					context.remaining = 2;
				} else
					context.mode.pop(); // ESCAPE
			}
			break;
		case SURROGATE:
			if ((context.remaining == 2 && c != '\\')
					|| (context.remaining == 1 && c != 'u'))
				throw new JSONException(
						"Invalid JSON: invalid escaped surrogate pair; expected next six-character sequence");
			if (--context.remaining == 0) {
				context.mode.push(Mode.CODEPOINT);
				context.remaining = 4;
				context.codepoint = 0;
			}
			break;
		case NUMBER:
			switch (c) { // number = [ minus ] int [ frac ] [ exp ]
			case '0': // int/frac/exp
			case '1': // int/frac/exp
			case '2': // int/frac/exp
			case '3': // int/frac/exp
			case '4': // int/frac/exp
			case '5': // int/frac/exp
			case '6': // int/frac/exp
			case '7': // int/frac/exp
			case '8': // int/frac/exp
			case '9': // int/frac/exp
			case '.': // frac
			case 'e': // exp
			case 'E': // exp
			case '-': // exp
			case '+': // exp
				context.value.append(c);
				break;
			default: // first character after number
				String value = context.value.toString();
				context.value.delete(0, Integer.MAX_VALUE);
				if (!REGEX_NUMBER.matcher(value).matches())
					throw new JSONException("Invalid JSON: invalid number");
				context.mode.pop(); // NUMBER
				context.mode.pop(); // VALUE
				context.callback.token(JSONToken.VALUE_NUMBER, value);
				parseChar(c, context);
			}
			break;
		case ARRAY:
			switch (c) { // array = begin-array [ value *( value-separator value
							// ) ] end-arrayF
			case ',': // value-separator
				context.mode.push(Mode.VALUE);
				context.mode.push(Mode.VALUE_REQUIRED);
				break;
			case ']': // end-array
				context.mode.pop(); // ARRAY
				if (context.mode.peek() == Mode.VALUE)
					context.mode.pop(); // VALUE
				context.callback.token(JSONToken.END_ARRAY, null);
				break;
			default:
				if (!isWhitespace(c))
					throw new JSONException(
							"Invalid JSON: expected value-separator / end-array");
			}
			break;
		case LITERAL:
			context.value.append(c);
			if (--context.remaining == 0) {
				String literal = context.value.toString();
				context.value.delete(0, Integer.MAX_VALUE);
				if (literal.equals("true"))
					context.callback.token(JSONToken.VALUE_TRUE, null);
				else if (literal.equals("false"))
					context.callback.token(JSONToken.VALUE_FALSE, null);
				else if (literal.equals("null"))
					context.callback.token(JSONToken.VALUE_NULL, null);
				else
					throw new JSONException(
							"Invalid JSON: expected false / null / true");
				context.mode.pop(); // LITERAL
				context.mode.pop(); // VALUE
			}
			break;
		}
	}

	private static void parseEOF(Context context) throws JSONException {
		switch (context.mode.pop()) {
		case TEXT:
			// We're done!
			break;
		default:
			throw new JSONException("Invalid JSON: unexpected EOF");
		}
	}

	private static boolean isWhitespace(char c) {
		// "Insignificant whitespace is allowed before or after any of the six
		// structural characters."
		switch (c) {
		case 0x20: // Space
		case 0x09: // Horizontal tab
		case 0x0A: // Line feed or New line
		case 0x0D: // Carriage return
			return true;
		default:
			return false;
		}
	}

	protected final OutputStream out;

	private boolean insertValueSeparator = false;

	private static final byte[] FALSE = new byte[] { 'f', 'a', 'l', 's', 'e' };

	private static final byte[] NULL = new byte[] { 'n', 'u', 'l', 'l' };

	private static final byte[] TRUE = new byte[] { 't', 'r', 'u', 'e' };

	public JSON(OutputStream out) {
		this.out = out;
	}

	@Override
	public void writeStartObject() throws IOException {
		if (insertValueSeparator)
			writeValueSeparator();
		else
			writeBlankSeparator();
		out.write('{');
		insertValueSeparator = false;
	}

	@Override
	public void writeEndObject() throws IOException {
		writeBlankSeparator();
		out.write('}');
		insertValueSeparator = true;
	}

	@Override
	public void writeStartArray() throws IOException {
		if (insertValueSeparator)
			writeValueSeparator();
		else
			writeBlankSeparator();
		out.write('[');
		insertValueSeparator = false;
	}

	@Override
	public void writeEndArray() throws IOException {
		writeBlankSeparator();
		out.write(']');
		insertValueSeparator = true;
	}

	@Override
	public void writeFieldName(CharSequence name) throws IOException {
		writeString(name);
		writeNameSeparator();
	}

	private static char toHexChar(int digit) {
		return (char) (digit < 0xA ? (digit + '0') : (digit + 'A' - 0xA));
	}

	@Override
	public void writeString(CharSequence value) throws IOException {
		if (insertValueSeparator)
			writeValueSeparator();
		else
			writeBlankSeparator();
		out.write('"');
		int length = value.length();
		for (int i = 0; i < length; i++) {
			// Get the next character, which for surrogate pairs is two chars
			char c = value.charAt(i);
			int cc = c;
			if (c < Character.MIN_SURROGATE || c > Character.MAX_SURROGATE) {
				// Not a surrogate
				cc = c;
			} else { // Surrogate
				if (c > Character.MAX_HIGH_SURROGATE) // If not lead surrogate
					throw new IllegalArgumentException(
							"Tail surrogate without lead surrogate");
				char c2 = value.charAt(++i); // Fetch tail surrogate
				if (c2 < Character.MIN_LOW_SURROGATE
						|| c2 > Character.MAX_SURROGATE)
					throw new IllegalArgumentException("Invalid tail surrogate");
				cc = Character.MIN_SUPPLEMENTARY_CODE_POINT
						| ((c - Character.MIN_SURROGATE) << 10)
						| (c2 - Character.MIN_LOW_SURROGATE);
			}

			// UTF-8 encode
			if (cc <= 0x7F) {
				// JSON escape
				if (cc <= 0x1F) {
					out.write('\\');
					switch (cc) {
					case '\b':
						out.write('b');
						break;
					case '\f':
						out.write('f');
						break;
					case '\n':
						out.write('n');
						break;
					case '\r':
						out.write('r');
						break;
					case '\t':
						out.write('t');
						break;
					default:
						out.write('u');
						// First two hex digits will always be '0' because cc <=
						// 0x7F <= 0x00FF
						out.write('0');
						out.write('0');
						out.write(toHexChar((cc & 0xF0) >>> 4));
						out.write(toHexChar(cc & 0xF));
					}
				} else if (cc == '"') {
					out.write('\\');
					out.write('"');
				} else if (cc == '\\') {
					out.write('\\');
					out.write('\\');
				} else {
					// JSON escaping is not required
					out.write(cc);
				}
			} else if (cc <= 0x7FF) {
				// JSON escaping is not required
				out.write(0xC0 | (cc >>> 6));
				out.write(0x80 | (cc & 0x3F));
			} else if (cc <= 0xFFFF) {
				// JSON escaping is not required
				out.write(0xE0 | (cc >>> 12));
				out.write(0x80 | ((cc >>> 6) & 0x3F));
				out.write(0x80 | (cc & 0x3F));
			} else { // if (cc <= 0x1FFFFF) {
				// JSON escaping is not required
				out.write(0xF0 | (cc >>> 18));
				out.write(0x80 | ((cc >>> 12) & 0x3F));
				out.write(0x80 | ((cc >>> 6) & 0x3F));
				out.write(0x80 | (cc & 0x3F));
			}
		}
		out.write('"');
		insertValueSeparator = true;
	}

	@Override
	public void writeNumber(Number value) throws IOException {
		if (insertValueSeparator)
			writeValueSeparator();
		else
			writeBlankSeparator();
		String string = value.toString();
		if (value instanceof Integer || value instanceof Long
				|| value instanceof BigDecimal || value instanceof BigInteger
				|| REGEX_NUMBER.matcher(string).matches()) {
			int length = string.length();
			for (int i = 0; i < length; i++)
				out.write(string.charAt(i));
		} else
			out.write(NULL);
		insertValueSeparator = true;
	}

	@Override
	public void writeNumber(CharSequence value) throws IOException {
		if (!REGEX_NUMBER.matcher(value).matches())
			throw new IllegalArgumentException(
					"Value is not a valid JSON number");
		if (insertValueSeparator)
			writeValueSeparator();
		else
			writeBlankSeparator();
		int length = value.length();
		for (int i = 0; i < length; i++)
			out.write(value.charAt(i));
		insertValueSeparator = true;
	}

	@Override
	public void writeBoolean(boolean value) throws IOException {
		if (insertValueSeparator)
			writeValueSeparator();
		else
			writeBlankSeparator();
		if (value)
			out.write(TRUE);
		else
			out.write(FALSE);
		insertValueSeparator = true;
	}

	@Override
	public void writeNull() throws IOException {
		if (insertValueSeparator)
			writeValueSeparator();
		else
			writeBlankSeparator();
		out.write(NULL);
		insertValueSeparator = true;
	}

	protected void writeNameSeparator() throws IOException {
		out.write(':');
		insertValueSeparator = false;
	}

	protected void writeValueSeparator() throws IOException {
		out.write(',');
	}

	protected void writeBlankSeparator() throws IOException {
	}

	private static final JSONReplacer DEFAULT_REPLACER = new JSONReplacer() {

		@Override
		public void replace(Object holder, Object key, Object value, JSON out)
				throws IOException {
			out.write(value, this);
		}

	};

	public final void write(Object value) throws IOException {
		write(value, DEFAULT_REPLACER);
	}

	private JSON fieldNameSerializer;

	private JSON getFieldNameSerializer() {
		if (fieldNameSerializer != null)
			return fieldNameSerializer;
		return fieldNameSerializer = new JSON(null) {

			private static final String ILLEGAL_STATE_MSG = "In field name writing mode; must write string/number/boolean/null value";

			@Override
			public void writeStartObject() throws IOException {
				throw new IllegalStateException(ILLEGAL_STATE_MSG);
			}

			@Override
			public void writeEndObject() throws IOException {
				throw new IllegalStateException(ILLEGAL_STATE_MSG);
			}

			@Override
			public void writeStartArray() throws IOException {
				throw new IllegalStateException(ILLEGAL_STATE_MSG);
			}

			@Override
			public void writeEndArray() throws IOException {
				throw new IllegalStateException(ILLEGAL_STATE_MSG);
			}

			@Override
			public void writeFieldName(CharSequence name) throws IOException {
				throw new IllegalStateException(ILLEGAL_STATE_MSG);
			}

			@Override
			public void writeString(CharSequence value) throws IOException {
				JSON.this.writeFieldName(value);
			}

			@Override
			public void writeNumber(Number value) throws IOException {
				String string = value.toString();
				if (!REGEX_NUMBER.matcher(string).matches())
					string = "null";
				JSON.this.writeFieldName(string);
			}

			@Override
			public void writeNumber(CharSequence value) throws IOException {
				if (!REGEX_NUMBER.matcher(value).matches())
					throw new IllegalArgumentException(
							"Value is not a valid JSON number");
				JSON.this.writeFieldName(value);
			}

			@Override
			public void writeBoolean(boolean value) throws IOException {
				JSON.this.writeFieldName(((Boolean) value).toString());
			}

			@Override
			public void writeNull() throws IOException {
				JSON.this.writeFieldName("null");
			}

		};
	}

	@SuppressWarnings("unchecked")
	public final void write(Object value, JSONReplacer replacer)
			throws IOException {
		if (value instanceof JSONSerializable)
			((JSONSerializable) value).toJSON(this);
		else if (value instanceof CharSequence)
			writeString((CharSequence) value);
		else if (value instanceof Map<?, ?>) {
			writeStartObject();
			for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) value)
					.entrySet()) {
				Object key = entry.getKey();
				getFieldNameSerializer().write(key);
				replacer.replace(value, key, entry.getValue(), this);
			}
			writeEndObject();
		} else if (value instanceof List<?>) {
			writeStartArray();
			int index = 0;
			for (Object element : (List<Object>) value)
				replacer.replace(value, index++, element, this);
			writeEndArray();
		} else if (value instanceof Number)
			writeNumber((Number) value);
		else if (value instanceof Boolean)
			writeBoolean((Boolean) value);
		else if (value == null)
			writeNull();
		else
			throw new IllegalArgumentException(value.getClass()
					+ " is not JSON serializable");
	}

	public final void flush() throws IOException {
		out.flush();
	}

	@Override
	public final void close() throws IOException {
		out.close();
	}

}
