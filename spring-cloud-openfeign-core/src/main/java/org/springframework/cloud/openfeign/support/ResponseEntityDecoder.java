/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.openfeign.support;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedList;

import feign.FeignException;
import feign.Response;
import feign.codec.Decoder;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * TODO: 对ResponseEntity进行解码
 * Decoder adds compatibility for Spring MVC's ResponseEntity to any other decoder via
 * composition.
 *
 * @author chad jaros
 */
public class ResponseEntityDecoder implements Decoder {

	private Decoder decoder;

	public ResponseEntityDecoder(Decoder decoder) {
		this.decoder = decoder;
	}

	@Override
	public Object decode(final Response response, Type type)
			throws IOException, FeignException {
		// TODO: 如果泛型里面的类型是HttpEntity类型的
		if (isParameterizeHttpEntity(type)) {
			// TODO: 把它类型拿到进行解码
			type = ((ParameterizedType) type).getActualTypeArguments()[0];
			Object decodedObject = this.decoder.decode(response, type);
			// TODO: 把解码返回来的结果 封装成 ResponseEntity 返回回去
			return createResponse(decodedObject, response);
		}
		// TODO: 如果返回你写的就是ResponseEntity，会把结果封装成ResponseEntity进行返回
		else if (isHttpEntity(type)) {
			return createResponse(null, response);
		}
		else {
			// TODO: 如果是其他类型的，那就交给decoder去解码吧
			return this.decoder.decode(response, type);
		}
	}

	private boolean isParameterizeHttpEntity(Type type) {
		if (type instanceof ParameterizedType) {
			return isHttpEntity(((ParameterizedType) type).getRawType());
		}
		return false;
	}

	private boolean isHttpEntity(Type type) {
		if (type instanceof Class) {
			Class c = (Class) type;
			return HttpEntity.class.isAssignableFrom(c);
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private <T> ResponseEntity<T> createResponse(Object instance, Response response) {
		// TODO: 会把请求头拿过来
		MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
		for (String key : response.headers().keySet()) {
			headers.put(key, new LinkedList<>(response.headers().get(key)));
		}
		// TODO: 创建一个ResponseEntity返回回去
		return new ResponseEntity<>((T) instance, headers,
				HttpStatus.valueOf(response.status()));
	}

}
