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
import java.io.OutputStream;
import java.nio.charset.Charset;

public class PrettyJSON extends JSON {

	protected final byte[] space;

	protected int level = 0;

	protected boolean noIndent = true;

	public PrettyJSON(OutputStream out) {
		this(out, 2);
	}

	public PrettyJSON(OutputStream out, String space) {
		super(out);
		this.space = space.getBytes(Charset.forName("UTF-8"));
	}

	public PrettyJSON(OutputStream out, int space) {
		super(out);
		this.space = new byte[space];
		for (int i = 0; i < this.space.length; i++)
			this.space[i] = ' ';
	}

	@Override
	public void writeStartObject() throws IOException {
		super.writeStartObject();
		level++;
	}

	@Override
	public void writeEndObject() throws IOException {
		level--;
		super.writeEndObject();
	}

	@Override
	public void writeStartArray() throws IOException {
		super.writeStartArray();
		level++;
	}

	@Override
	public void writeEndArray() throws IOException {
		level--;
		super.writeEndArray();
	}

	@Override
	public void writeFieldName(CharSequence name) throws IOException {
		super.writeFieldName(name);
	}

	@Override
	public void writeString(CharSequence value) throws IOException {
		super.writeString(value);
	}

	@Override
	public void writeNumber(CharSequence value) throws IOException {
		super.writeNumber(value);
	}

	@Override
	public void writeBoolean(boolean value) throws IOException {
		super.writeBoolean(value);
	}

	@Override
	public void writeNull() throws IOException {
		super.writeNull();
	}

	@Override
	protected void writeNameSeparator() throws IOException {
		super.writeNameSeparator();
		out.write(' ');
		noIndent = true;
	}

	@Override
	protected void writeValueSeparator() throws IOException {
		super.writeValueSeparator();
		indent(level);
	}

	@Override
	protected void writeBlankSeparator() throws IOException {
		indent(level);
	}

	private void indent(int level) throws IOException {
		if (noIndent)
			noIndent = false;
		else {
			out.write('\n');
			for (int i = 0; i < level; i++)
				out.write(space);
		}
	}

}
