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

package org.springframework.cloud.openfeign.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

import feign.MethodMetadata;

import org.springframework.cloud.openfeign.AnnotatedParameterProcessor;
import org.springframework.web.bind.annotation.RequestHeader;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;

/**
 * {@link RequestHeader} parameter processor.
 *
 * @author Jakub Narloch
 * @author Abhijit Sarkar
 * @see AnnotatedParameterProcessor
 */
public class RequestHeaderParameterProcessor implements AnnotatedParameterProcessor {

	private static final Class<RequestHeader> ANNOTATION = RequestHeader.class;

	@Override
	public Class<? extends Annotation> getAnnotationType() {
		return ANNOTATION;
	}

	@Override
	public boolean processArgument(AnnotatedParameterContext context,
			Annotation annotation, Method method) {
		// TODO: 也是先取到注解 参数的角标
		int parameterIndex = context.getParameterIndex();
		// TODO: 获取参数类型
		Class<?> parameterType = method.getParameterTypes()[parameterIndex];
		// TODO: 把方法的元信息拿到
		MethodMetadata data = context.getMethodMetadata();

		// TODO: 同样的，如果是个map，那就设置 headerMap的下标
		if (Map.class.isAssignableFrom(parameterType)) {
			checkState(data.headerMapIndex() == null,
					"Header map can only be present once.");
			data.headerMapIndex(parameterIndex);

			return true;
		}
		// TODO: 对注解进行强转
		String name = ANNOTATION.cast(annotation).value();
		// TODO: 检查name不能为空
		checkState(emptyToNull(name) != null,
				"RequestHeader.value() was empty on parameter %s", parameterIndex);
		// TODO: 加到indexToName
		context.setParameterName(name);

		// TODO: 最后构建 header，{name}  加到header中
		Collection<String> header = context.setTemplateParameter(name,
				data.template().headers().get(name));
		data.template().header(name, header);
		return true;
	}

}
