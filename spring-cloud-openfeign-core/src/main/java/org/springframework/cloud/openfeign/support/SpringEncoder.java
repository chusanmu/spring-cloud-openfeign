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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;

import feign.Request;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.encoding.HttpEncoding;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.cloud.openfeign.support.FeignUtils.getHeaders;
import static org.springframework.cloud.openfeign.support.FeignUtils.getHttpHeaders;

/**
 * @author Spencer Gibb
 * @author Scien Jus
 * @author Ahmad Mozafarnia
 * @author Aaron Whiteside
 * @author Darren Foong
 */
public class SpringEncoder implements Encoder {

	private static final Log log = LogFactory.getLog(SpringEncoder.class);

	/**
	 * TODO: 对表单提交的支持
	 */
	private final SpringFormEncoder springFormEncoder;

	/**
	 * TODO: 消息转换器
	 */
	private final ObjectFactory<HttpMessageConverters> messageConverters;

	public SpringEncoder(ObjectFactory<HttpMessageConverters> messageConverters) {
		this.springFormEncoder = new SpringFormEncoder();
		this.messageConverters = messageConverters;
	}

	/**
	 * 把messageConverters拿到，利用内容转换器 进行转换写出
	 * @param springFormEncoder
	 * @param messageConverters
	 */
	public SpringEncoder(SpringFormEncoder springFormEncoder,
			ObjectFactory<HttpMessageConverters> messageConverters) {
		this.springFormEncoder = springFormEncoder;
		this.messageConverters = messageConverters;
	}

	@Override
	public void encode(Object requestBody, Type bodyType, RequestTemplate request)
			throws EncodeException {
		// template.body(conversionService.convert(object, String.class));
		if (requestBody != null) {
			// TODO: 请求体类型
			Collection<String> contentTypes = request.headers()
					.get(HttpEncoding.CONTENT_TYPE);

			MediaType requestContentType = null;
			// TODO: 如果请求体类型不为空
			if (contentTypes != null && !contentTypes.isEmpty()) {
				String type = contentTypes.iterator().next();
				requestContentType = MediaType.valueOf(type);
			}
			// TODO: 如果你的contentType是 MULTIPART_FORM_DATA
			if (Objects.equals(requestContentType, MediaType.MULTIPART_FORM_DATA)) {
				// TODO: 使用表单encoder
				this.springFormEncoder.encode(requestBody, bodyType, request);
				return;
			}
			else {
				// TODO: 如果你请求体是MultipartFile类型的，但是呢 content-Type又不是multipart_form_data, 那这时候就打印一行log
				if (bodyType == MultipartFile.class) {
					log.warn(
							"For MultipartFile to be handled correctly, the 'consumes' parameter of @RequestMapping "
									+ "should be specified as MediaType.MULTIPART_FORM_DATA_VALUE");
				}
			}

			/**
			 * TODO: 这里就是一个个的判断内容转换器，能否将内容写出，如果可以，就采用拿到的messageConvert去写出
			 */
			for (HttpMessageConverter messageConverter : this.messageConverters
					.getObject().getConverters()) {
				FeignOutputMessage outputMessage;
				try {
					if (messageConverter instanceof GenericHttpMessageConverter) {
						outputMessage = checkAndWrite(requestBody, bodyType,
								requestContentType,
								(GenericHttpMessageConverter) messageConverter, request);
					}
					else {
						// TODO: 检查这种消息转换器能否将此body进行写出
						outputMessage = checkAndWrite(requestBody, requestContentType,
								messageConverter, request);
					}
				}
				catch (IOException | HttpMessageConversionException ex) {
					throw new EncodeException("Error converting request body", ex);
				}
				if (outputMessage != null) {
					// clear headers
					request.headers(null);
					// converters can modify headers, so update the request
					// with the modified headers
					// TODO: converters可以修改请求头，所以更新下请求头
					request.headers(getHeaders(outputMessage.getHeaders()));

					// do not use charset for binary data and protobuf
					Charset charset;
					if (messageConverter instanceof ByteArrayHttpMessageConverter) {
						charset = null;
					}
					else if (messageConverter instanceof ProtobufHttpMessageConverter
							&& ProtobufHttpMessageConverter.PROTOBUF.isCompatibleWith(
									outputMessage.getHeaders().getContentType())) {
						charset = null;
					}
					else {
						charset = StandardCharsets.UTF_8;
					}
					// TODO: 最后的最后，把字节流 放到request的body里面, 表示编码好了，可以发送了
					request.body(Request.Body.encoded(
						// TODO: 从输出流里面获取字节信息
							outputMessage.getOutputStream().toByteArray(), charset));
					return;
				}
			}
			// TODO: 这个异常有时候可能会经常看到，没有找到合适的消息转换器 对于这个消息体类型
			String message = "Could not write request: no suitable HttpMessageConverter "
					+ "found for request type [" + requestBody.getClass().getName() + "]";
			if (requestContentType != null) {
				message += " and content type [" + requestContentType + "]";
			}
			throw new EncodeException(message);
		}
	}

	@SuppressWarnings("unchecked")
	private FeignOutputMessage checkAndWrite(Object body, MediaType contentType,
			HttpMessageConverter converter, RequestTemplate request) throws IOException {
		// TODO: 判断能否将这种类型的java对象，根据指定的content-type去写出，如果可以就进行写出
		if (converter.canWrite(body.getClass(), contentType)) {
			// TODO: 在写之前打印log
			logBeforeWrite(body, contentType, converter);
			FeignOutputMessage outputMessage = new FeignOutputMessage(request);
			// TODO: 调用write方法写出，会写到输出流里面
			converter.write(body, contentType, outputMessage);
			return outputMessage;
		}
		else {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private FeignOutputMessage checkAndWrite(Object body, Type genericType,
			MediaType contentType, GenericHttpMessageConverter converter,
			RequestTemplate request) throws IOException {
		if (converter.canWrite(genericType, body.getClass(), contentType)) {
			logBeforeWrite(body, contentType, converter);
			FeignOutputMessage outputMessage = new FeignOutputMessage(request);
			converter.write(body, genericType, contentType, outputMessage);
			return outputMessage;
		}
		else {
			return null;
		}
	}

	private void logBeforeWrite(Object requestBody, MediaType requestContentType,
			HttpMessageConverter messageConverter) {
		if (log.isDebugEnabled()) {
			if (requestContentType != null) {
				log.debug("Writing [" + requestBody + "] as \"" + requestContentType
						+ "\" using [" + messageConverter + "]");
			}
			else {
				log.debug(
						"Writing [" + requestBody + "] using [" + messageConverter + "]");
			}
		}
	}

	private final class FeignOutputMessage implements HttpOutputMessage {

		private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		private final HttpHeaders httpHeaders;

		private FeignOutputMessage(RequestTemplate request) {
			this.httpHeaders = getHttpHeaders(request.headers());
		}

		@Override
		public OutputStream getBody() throws IOException {
			return this.outputStream;
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.httpHeaders;
		}

		public ByteArrayOutputStream getOutputStream() {
			return this.outputStream;
		}

	}

}
