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
import org.springframework.web.bind.annotation.RequestParam;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;

/**
 * TODO: RequestParam注解处理器
 * {@link RequestParam} parameter processor.
 *
 * @author Jakub Narloch
 * @author Abhijit Sarkar
 * @see AnnotatedParameterProcessor
 */
public class RequestParamParameterProcessor implements AnnotatedParameterProcessor {

	/**
	 * 对RequestParam注解的支持
	 */
	private static final Class<RequestParam> ANNOTATION = RequestParam.class;

	@Override
	public Class<? extends Annotation> getAnnotationType() {
		return ANNOTATION;
	}

	@Override
	public boolean processArgument(AnnotatedParameterContext context,
			Annotation annotation, Method method) {
		// TODO: 把此注解对应的参数的角标拿到
		int parameterIndex = context.getParameterIndex();
		// TODO: 把注解标注的参数 的参数类型拿到
		Class<?> parameterType = method.getParameterTypes()[parameterIndex];
		// TODO: 获取方法元数据
		MethodMetadata data = context.getMethodMetadata();
		// TODO: 判断参数类型是否是Map
		if (Map.class.isAssignableFrom(parameterType)) {
			checkState(data.queryMapIndex() == null,
					"Query map can only be present once.");
			// TODO: 如果是个Map，那就设置queryMap的Index，需要注意queryMapIndex只能有一个
			data.queryMapIndex(parameterIndex);

			return true;
		}
		// TODO: 进行注解类型的强转
		RequestParam requestParam = ANNOTATION.cast(annotation);
		// TODO: 把value值取到
		String name = requestParam.value();
		// TODO: name不能为空，name值为空，直接抛出异常
		checkState(emptyToNull(name) != null,
				"RequestParam.value() was empty on parameter %s", parameterIndex);
		context.setParameterName(name);

		Collection<String> query = context.setTemplateParameter(name,
				data.template().queries().get(name));
		// TODO: 设置queryTemplate，设置query模板
		data.template().query(name, query);
		return true;
	}

}
