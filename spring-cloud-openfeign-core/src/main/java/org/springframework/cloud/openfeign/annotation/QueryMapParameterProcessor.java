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

import feign.MethodMetadata;

import org.springframework.cloud.openfeign.AnnotatedParameterProcessor;
import org.springframework.cloud.openfeign.SpringQueryMap;

/**
 * TODO: 主要处理SpringQueryMap注解，SpringQueryMap在平时的开发中，用的挺多的
 * {@link SpringQueryMap} parameter processor.
 *
 * @author Aram Peres
 * @see AnnotatedParameterProcessor
 */
public class QueryMapParameterProcessor implements AnnotatedParameterProcessor {

	/**
	 * TODO: SpringQueryMap 生效原理
	 */
	private static final Class<SpringQueryMap> ANNOTATION = SpringQueryMap.class;

	@Override
	public Class<? extends Annotation> getAnnotationType() {
		return ANNOTATION;
	}

	@Override
	public boolean processArgument(AnnotatedParameterContext context,
			Annotation annotation, Method method) {
		// TODO: 获得参数下标，以及方法元信息
		int paramIndex = context.getParameterIndex();
		MethodMetadata metadata = context.getMethodMetadata();
		// TODO: 如果queryMapIndex为空，才去处理
		if (metadata.queryMapIndex() == null) {
			// TODO: 把当前参数角标设置为queryMap的下标，其实和 feign 原生注解 QueryMap 处理一样
			metadata.queryMapIndex(paramIndex);
			// TODO: 设置map是否编码
			metadata.queryMapEncoded(SpringQueryMap.class.cast(annotation).encoded());
		}
		return true;
	}

}
