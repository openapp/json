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

import java.io.IOException;

public interface JSONSerializer {

	void writeStartObject() throws IOException;

	void writeEndObject() throws IOException;

	void writeStartArray() throws IOException;

	void writeEndArray() throws IOException;

	void writeFieldName(CharSequence name) throws IOException;

	void writeString(CharSequence value) throws IOException;

	void writeNumber(Number value) throws IOException;

	void writeNumber(CharSequence value) throws IOException;

	void writeBoolean(boolean value) throws IOException;

	void writeNull() throws IOException;

	void write(Object value) throws IOException;

	void write(Object value, JSONReplacer replacer) throws IOException;

}
