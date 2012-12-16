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
package org.openapplication.json.test;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openapplication.json.JSON;
import org.openapplication.json.JSONCallback;
import org.openapplication.json.JSONException;
import org.openapplication.json.JSONReviver;
import org.openapplication.json.JSONSerializable;
import org.openapplication.json.JSONSerializer;
import org.openapplication.json.JSONToken;


public class JSONTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testParse() throws IOException, JSONException {
		String string = "{\"test\":[1234,true,false,null,\"te\\u005Cst\",\"test\\uD834\\uDD1E\",[[5.567E-5,[\"coolers\"]]]]}";
		InputStream in = new ByteArrayInputStream(string.getBytes(Charset
				.forName("UTF-8")));
		// JSON.parse(in, new JSONCallback() {
		// @Override
		// public void token(JSONToken token, String value) {
		// //System.out.println(token + " " + value);
		// }
		// });
		Object parsed = JSON.parse(in);
		System.out.println(parsed);

		parsed = JSON.parse(string);
		System.out.println(parsed);

		System.out.println(JSON.stringify(parsed));

		Map<Object, Object> map = new HashMap<Object, Object>();
		map.put(new JSONSerializable() {
			@Override
			public void toJSON(JSONSerializer out) throws IOException {
				out.writeBoolean(false);
			}
		}, 5678);
		System.out.println(JSON.stringify(map));
		System.out.println(JSON.parse(JSON.stringify(Collections.EMPTY_LIST)));
		System.out.println(JSON.parse(JSON.stringify(Collections.EMPTY_MAP)));

		System.out
				.println(JSON
						.parse(" { \"a\" : [ \"test2\" , \"test3\" ] ,  \"5\" :5 , \"3\":3   } "));
	}

}
