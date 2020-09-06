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

import feign.MethodMetadata;

import org.springframework.cloud.openfeign.AnnotatedParameterProcessor;
import org.springframework.web.bind.annotation.RequestPart;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;

/**
 * {@link RequestPart} parameter processor.
 *
 * @author Aaron Whiteside
 * @see AnnotatedParameterProcessor
 */
public class RequestPartParameterProcessor implements AnnotatedParameterProcessor {

	/**
	 * TODO: 支持对@RequestPart
	 */
	private static final Class<RequestPart> ANNOTATION = RequestPart.class;

	@Override
	public Class<? extends Annotation> getAnnotationType() {
		return ANNOTATION;
	}

	@Override
	public boolean processArgument(AnnotatedParameterContext context,
			Annotation annotation, Method method) {
		// TODO: 获得参数角标和方法的元信息
		int parameterIndex = context.getParameterIndex();
		MethodMetadata data = context.getMethodMetadata();
		// TODO: 进行注解的强转，然后获取value值
		String name = ANNOTATION.cast(annotation).value();
		// TODO: check name 不能为空
		checkState(emptyToNull(name) != null,
				"RequestPart.value() was empty on parameter %s", parameterIndex);
		context.setParameterName(name);
		// TODO: 添加到form参数里面去
		data.formParams().add(name);
		Collection<String> names = context.setTemplateParameter(name,
				data.indexToName().get(parameterIndex));
		// TODO: 最后又加了一次，这里不是特别理解，因为平时开发用的也少
		data.indexToName().put(parameterIndex, names);
		return true;
	}

}
