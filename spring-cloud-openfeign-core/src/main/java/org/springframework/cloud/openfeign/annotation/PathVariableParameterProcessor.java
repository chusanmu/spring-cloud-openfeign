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
import org.springframework.web.bind.annotation.PathVariable;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;

/**
 * {@link PathVariable} parameter processor.
 *
 * @author Jakub Narloch
 * @author Abhijit Sarkar
 * @see AnnotatedParameterProcessor
 */
public class PathVariableParameterProcessor implements AnnotatedParameterProcessor {

	private static final Class<PathVariable> ANNOTATION = PathVariable.class;

	@Override
	public Class<? extends Annotation> getAnnotationType() {
		return ANNOTATION;
	}

	@Override
	public boolean processArgument(AnnotatedParameterContext context,
			Annotation annotation, Method method) {
		// TODO: 把当前注解转成PathVariable，然后拿到value，当做name
		String name = ANNOTATION.cast(annotation).value();
		// TODO: value为空直接抛出异常
		checkState(emptyToNull(name) != null,
				"PathVariable annotation was empty on param %s.",
				context.getParameterIndex());
		// TODO: 设置参数名称
		context.setParameterName(name);
		// TODO: 把methodMetadata拿到
		MethodMetadata data = context.getMethodMetadata();
		// TODO: 对name补花括号
		String varName = '{' + name + '}';
		// TODO: 当前url不包括 varName, 并且headers中和queries中也不包括varName
		// TODO: 都不包含这个模板，那就把它加到formParams中
		// TODO: 这里说明，你@PathVariable不仅仅可以去填充url上面的模板，还可以去填充query, headers.
		if (!data.template().url().contains(varName)
				&& !searchMapValues(data.template().queries(), varName)
				&& !searchMapValues(data.template().headers(), varName)) {
			// TODO: 则把当前name加到formPrams
			data.formParams().add(name);
		}
		return true;
	}

	/**
	 * 搜索map的value，如果包含search这个值，则返回true
	 * @param map
	 * @param search
	 * @param <K>
	 * @param <V>
	 * @return
	 */
	private <K, V> boolean searchMapValues(Map<K, Collection<V>> map, V search) {
		Collection<Collection<V>> values = map.values();
		if (values == null) {
			return false;
		}
		for (Collection<V> entry : values) {
			if (entry.contains(search)) {
				return true;
			}
		}
		return false;
	}

}
