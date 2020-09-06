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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import feign.Contract;
import feign.Feign;
import feign.MethodMetadata;
import feign.Param;
import feign.Request;

import org.springframework.cloud.openfeign.AnnotatedParameterProcessor;
import org.springframework.cloud.openfeign.annotation.MatrixVariableParameterProcessor;
import org.springframework.cloud.openfeign.annotation.PathVariableParameterProcessor;
import org.springframework.cloud.openfeign.annotation.QueryMapParameterProcessor;
import org.springframework.cloud.openfeign.annotation.RequestHeaderParameterProcessor;
import org.springframework.cloud.openfeign.annotation.RequestParamParameterProcessor;
import org.springframework.cloud.openfeign.annotation.RequestPartParameterProcessor;
import org.springframework.cloud.openfeign.encoding.HttpEncoding;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;
import static org.springframework.cloud.openfeign.support.FeignUtils.addTemplateParameter;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

/**
 * TODO: 注意这个是 spring cloud 支持的feign 对mvc注解的支持
 * TODO: 此类没有继承 DeclarativeContract 而是直接继承了BaseContract, 实现了里面的一些抽象方法
 *
 * @author Spencer Gibb
 * @author Abhijit Sarkar
 * @author Halvdan Hoem Grelland
 * @author Aram Peres
 * @author Olga Maciaszek-Sharma
 * @author Aaron Whiteside
 * @author Artyom Romanenko
 * @author Darren Foong
 */
public class SpringMvcContract extends Contract.BaseContract
		implements ResourceLoaderAware {

	private static final String ACCEPT = "Accept";

	private static final String CONTENT_TYPE = "Content-Type";

	private static final TypeDescriptor STRING_TYPE_DESCRIPTOR = TypeDescriptor
			.valueOf(String.class);

	private static final TypeDescriptor ITERABLE_TYPE_DESCRIPTOR = TypeDescriptor
			.valueOf(Iterable.class);

	/**
	 * TODO: 参数名称解析器
	 */
	private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

	private final Map<Class<? extends Annotation>, AnnotatedParameterProcessor> annotatedArgumentProcessors;

	private final Map<String, Method> processedMethods = new HashMap<>();

	/**
	 * TODO: 类型转换器，用来支持类型转换
	 */
	private final ConversionService conversionService;

	private final ConvertingExpanderFactory convertingExpanderFactory;

	/**
	 * todo: 默认的资源加载器
	 */
	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	public SpringMvcContract() {
		this(Collections.emptyList());
	}

	/**
	 * 支持你自定义传进来参数解析器
	 * @param annotatedParameterProcessors
	 */
	public SpringMvcContract(
			List<AnnotatedParameterProcessor> annotatedParameterProcessors) {
		this(annotatedParameterProcessors, new DefaultConversionService());
	}

	public SpringMvcContract(
			List<AnnotatedParameterProcessor> annotatedParameterProcessors,
			ConversionService conversionService) {
		Assert.notNull(annotatedParameterProcessors,
				"Parameter processors can not be null.");
		Assert.notNull(conversionService, "ConversionService can not be null.");
		// TODO: 这里面有一些默认的参数处理器
		List<AnnotatedParameterProcessor> processors = getDefaultAnnotatedArgumentsProcessors();
		// TODO: 默认的加上你用户传进来的annotatedParameterProcessors
		processors.addAll(annotatedParameterProcessors);
		// TODO: 把处理器转成一个Map ，key作为当前支持注解的类型，value就是注解处理器
		this.annotatedArgumentProcessors = toAnnotatedArgumentProcessorMap(processors);
		this.conversionService = conversionService;
		this.convertingExpanderFactory = new ConvertingExpanderFactory(conversionService);
	}

	private static TypeDescriptor createTypeDescriptor(Method method, int paramIndex) {
		// TODO: 把角标对应的Parameter拿到
		Parameter parameter = method.getParameters()[paramIndex];
		// TODO: 转为MethodParameter
		MethodParameter methodParameter = MethodParameter.forParameter(parameter);
		// TODO: 封成TypeDescriptor
		TypeDescriptor typeDescriptor = new TypeDescriptor(methodParameter);

		// Feign applies the Param.Expander to each element of an Iterable, so in those
		// cases we need to provide a TypeDescriptor of the element.
		// TODO: 如果typeDescriptor类型是 Iterable
		if (typeDescriptor.isAssignableTo(ITERABLE_TYPE_DESCRIPTOR)) {
			// TODO: 把跌代器里 也就是 集合中元素的类型拿到
			TypeDescriptor elementTypeDescriptor = getElementTypeDescriptor(
					typeDescriptor);

			checkState(elementTypeDescriptor != null,
					"Could not resolve element type of Iterable type %s. Not declared?",
					typeDescriptor);

			typeDescriptor = elementTypeDescriptor;
		}
		return typeDescriptor;
	}

	private static TypeDescriptor getElementTypeDescriptor(
			TypeDescriptor typeDescriptor) {
		// TODO: 把集合中元素的类型拿到
		TypeDescriptor elementTypeDescriptor = typeDescriptor.getElementTypeDescriptor();
		// that means it's not a collection but it is iterable, gh-135
		// TODO: 如果elementTypeDescriptor为空，并且typeDescriptor的类型是Iterable
		if (elementTypeDescriptor == null
				&& Iterable.class.isAssignableFrom(typeDescriptor.getType())) {
			// TODO: 解析Iterable类型的泛型
			ResolvableType type = typeDescriptor.getResolvableType().as(Iterable.class)
					.getGeneric(0);
			// TODO: 如果为空，就返回null
			if (type.resolve() == null) {
				return null;
			}
			// TODO: 重新生成一个TypeDescriptor
			return new TypeDescriptor(type, null, typeDescriptor.getAnnotations());
		}
		// TODO: 返回集合中元素的类型
		return elementTypeDescriptor;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	/**
	 * TODO: 处理类上面的注解，注意这里只处理了 uri, 没有处理 produces, consumes, headers等，留到了最后 parseAndValidateMetadata 去后置处理
	 * @param data
	 * @param clz
	 */
	@Override
	protected void processAnnotationOnClass(MethodMetadata data, Class<?> clz) {
		// TODO: 当前接口的父接口的个数为0时，才去处理
		if (clz.getInterfaces().length == 0) {
			// TODO: 去把RequestMapping注解find到，找到
			RequestMapping classAnnotation = findMergedAnnotation(clz,
					RequestMapping.class);
			// TODO: 然后不为空，并且里面的值的个数大于0
			if (classAnnotation != null && classAnnotation.value().length > 0) {
				// Prepend path from class annotation if specified
				String pathValue = emptyToNull(classAnnotation.value()[0]);
				// TODO: 不等于空，然后去处理
				if (pathValue != null) {
					// TODO: 把pathValue拿到
					pathValue = resolve(pathValue);
					// TODO: 如果pathValue不是/就加到uri里面去，可以看出如果你 @RequestMapping("/") 在类上 这样是不生效的
					if (!pathValue.equals("/")) {
						data.template().uri(pathValue);
					}
				}
			}
		}
	}

	/**
	 * TODO: 这个方法在解析接口时,首先会被调用，之后调用解析class, 解析method, 解析parameter方法
	 * @param targetType
	 * @param method
	 * @return
	 */
	@Override
	public MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
		// TODO: 这里首先进行了一次缓存，缓存下来了所有的method
		this.processedMethods.put(Feign.configKey(targetType, method), method);
		// TODO: 然后调用父类parse...方法，得到md
		MethodMetadata md = super.parseAndValidateMetadata(targetType, method);
		// TODO: 接着在类上查找 RequestMapping注解，进行最后的处理
		RequestMapping classAnnotation = findMergedAnnotation(targetType,
				RequestMapping.class);
		// TODO: 如果找到了不为 null
		if (classAnnotation != null) {
			// produces - use from class annotation only if method has not specified this
			// TODO: 仅在方法未指定时，然后使用类上指定的
			if (!md.template().headers().containsKey(ACCEPT)) {
				// TODO: 去处理produces, 使用classAnnotation
				parseProduces(md, method, classAnnotation);
			}

			// consumes -- use from class annotation only if method has not specified this
			// TODO: 同produces一样，仅在方法未指定时，使用类上面的
			if (!md.template().headers().containsKey(CONTENT_TYPE)) {
				// TODO: 重新设置CONTENT_TYPE, 使用类上注解的属性设置
				parseConsumes(md, method, classAnnotation);
			}

			// headers -- class annotation is inherited to methods, always write these if
			// present
			// TODO: 去处理headers,如果类上面有，那就继续 append
			parseHeaders(md, method, classAnnotation);
		}
		return md;
	}

	/**
	 * TODO: 处理方法上的注解
	 * @param data
	 * @param methodAnnotation
	 * @param method
	 */
	@Override
	protected void processAnnotationOnMethod(MethodMetadata data,
			Annotation methodAnnotation, Method method) {
		// TODO: 如果当前methodAnnotation不是RequestMapping注解类型的，并且它里面的元注解也不存在RequestMapping注解，那就直接返回掉
		if (!RequestMapping.class.isInstance(methodAnnotation) && !methodAnnotation
				.annotationType().isAnnotationPresent(RequestMapping.class)) {
			return;
		}

		// TODO: 把RequestMapping注解拿到
		RequestMapping methodMapping = findMergedAnnotation(method, RequestMapping.class);
		// HTTP Method
		// TODO: 把httpMethod拿到
		RequestMethod[] methods = methodMapping.method();
		// TODO: 如果没写或者是没有,那么默认就用GET
		if (methods.length == 0) {
			methods = new RequestMethod[] { RequestMethod.GET };
		}
		// TODO: 看看是不是只有一个GET或者是POST或者PUT，如果找到了多个就报错
		checkOne(method, methods, "method");
		// TODO: 把httpMethod放进去
		data.template().method(Request.HttpMethod.valueOf(methods[0].name()));

		// path
		// TODO: 开始处理path
		// TODO: 这里如果你的value有多个，那就报错吧
		checkAtMostOne(method, methodMapping.value(), "value");
		// TODO: 下面如果value有值，并且不为空，并且不仅仅是个 / 那就加到uri里面去，注意添加模式是append.
		if (methodMapping.value().length > 0) {
			String pathValue = emptyToNull(methodMapping.value()[0]);
			if (pathValue != null) {
				pathValue = resolve(pathValue);
				if (!pathValue.equals("/")) {
					data.template().uri(pathValue, true);
				}
			}
		}

		// produces
		// TODO: 开始处理produces，添加ACCEPT头，默认value添加的是第一个
		parseProduces(data, method, methodMapping);

		// consumes
		// TODO: 开始处理consumes，添加Content-type头，默认添加第一个
		parseConsumes(data, method, methodMapping);

		// headers
		// TODO: 开始处理headers
		parseHeaders(data, method, methodMapping);

		// TODO: 这里indexToExpander放了一个空Map
		data.indexToExpander(new LinkedHashMap<Integer, Param.Expander>());
	}

	private String resolve(String value) {
		// TODO: 如果当前value不为空，使用了resolvePlaceholders，就可以看出来它支持el表达式
		if (StringUtils.hasText(value)
				&& this.resourceLoader instanceof ConfigurableApplicationContext) {
			return ((ConfigurableApplicationContext) this.resourceLoader).getEnvironment()
					.resolvePlaceholders(value);
		}
		return value;
	}

	private void checkAtMostOne(Method method, Object[] values, String fieldName) {
		checkState(values != null && (values.length == 0 || values.length == 1),
				"Method %s can only contain at most 1 %s field. Found: %s",
				method.getName(), fieldName,
				values == null ? null : Arrays.asList(values));
	}

	private void checkOne(Method method, Object[] values, String fieldName) {
		checkState(values != null && values.length == 1,
				"Method %s can only contain 1 %s field. Found: %s", method.getName(),
				fieldName, values == null ? null : Arrays.asList(values));
	}

	/**
	 * TODO: 处理参数上的注解
	 * @param data
	 * @param annotations
	 * @param paramIndex
	 * @return
	 */
	@Override
	protected boolean processAnnotationsOnParameter(MethodMetadata data,
			Annotation[] annotations, int paramIndex) {
		boolean isHttpAnnotation = false;
		// TODO: 对data以及paramIndex的一个简单封装
		AnnotatedParameterProcessor.AnnotatedParameterContext context = new SimpleAnnotatedParameterContext(data, paramIndex);
		// TODO: 从缓存里面把这个方法拿到
		Method method = this.processedMethods.get(data.configKey());
		// TODO: 把当前位置的注解进行遍历
		for (Annotation parameterAnnotation : annotations) {
			// TODO: 根据注解类型把注解处理器拿到
			AnnotatedParameterProcessor processor = this.annotatedArgumentProcessors
					.get(parameterAnnotation.annotationType());
			// TODO: 注解处理器不为空
			if (processor != null) {
				Annotation processParameterAnnotation;
				// synthesize, handling @AliasFor, while falling back to parameter name on
				// missing String #value():
				// TODO: 使用参数名 去降级 @RequestParam value为默认值的情况，如果你不指定value值，如果参数名合法，会去使用参数名
				processParameterAnnotation = synthesizeWithMethodParameterNameAsFallbackValue(
						parameterAnnotation, method, paramIndex);
				// TODO: 这里就开始去处理这个注解了, 会使用特定的注解处理器去处理
				isHttpAnnotation |= processor.processArgument(context,
						processParameterAnnotation, method);
			}
		}
		// TODO: 如果不是表单数据，并且是httpAnnotation，并且expander为空
		if (!isMultipartFormData(data) && isHttpAnnotation
				&& data.indexToExpander().get(paramIndex) == null) {
			// TODO: 会使用conversionService 去转换参数
			TypeDescriptor typeDescriptor = createTypeDescriptor(method, paramIndex);
			// TODO: 判断能否转成String类型
			if (this.conversionService.canConvert(typeDescriptor,
					STRING_TYPE_DESCRIPTOR)) {
				// TODO: 创建一个expander
				Param.Expander expander = this.convertingExpanderFactory
						.getExpander(typeDescriptor);
				// TODO: expander不为空，会进行缓存起来
				if (expander != null) {
					data.indexToExpander().put(paramIndex, expander);
				}
			}
		}
		return isHttpAnnotation;
	}

	private void parseProduces(MethodMetadata md, Method method,
			RequestMapping annotation) {
		// TODO: 把produces的值拿到
		String[] serverProduces = annotation.produces();
		String clientAccepts = serverProduces.length == 0 ? null
				: emptyToNull(serverProduces[0]);
		if (clientAccepts != null) {
			// TODO: 设置ACCEPT头，注意，只取第一个value
			md.template().header(ACCEPT, clientAccepts);
		}
	}

	private void parseConsumes(MethodMetadata md, Method method,
			RequestMapping annotation) {
		String[] serverConsumes = annotation.consumes();
		String clientProduces = serverConsumes.length == 0 ? null
				: emptyToNull(serverConsumes[0]);
		if (clientProduces != null) {
			// TODO: 添加Content_type头，默认添加第一个
			md.template().header(CONTENT_TYPE, clientProduces);
		}
	}

	private void parseHeaders(MethodMetadata md, Method method,
			RequestMapping annotation) {
		// TODO: only supports one header value per key
		// TODO: 注意这里添加了一个注释，目前只支持一个key 一个value
		if (annotation.headers() != null && annotation.headers().length > 0) {
			for (String header : annotation.headers()) {
				// TODO: 类似于 Content-Type=Application/json
				int index = header.indexOf('=');
				if (!header.contains("!=") && index >= 0) {
					// TODO: key和value都会进行一个resolve
					md.template().header(resolve(header.substring(0, index)),
							resolve(header.substring(index + 1).trim()));
				}
			}
		}
	}

	private Map<Class<? extends Annotation>, AnnotatedParameterProcessor> toAnnotatedArgumentProcessorMap(
			List<AnnotatedParameterProcessor> processors) {
		Map<Class<? extends Annotation>, AnnotatedParameterProcessor> result = new HashMap<>();
		for (AnnotatedParameterProcessor processor : processors) {
			result.put(processor.getAnnotationType(), processor);
		}
		return result;
	}

	private List<AnnotatedParameterProcessor> getDefaultAnnotatedArgumentsProcessors() {

		List<AnnotatedParameterProcessor> annotatedArgumentResolvers = new ArrayList<>();
		// TODO: 注册了一些默认的 注解处理器 可以看到支持一些常用的 @PathVariable @RequestParam @RequestHeader @QueryMap @RequestPartParameter
		// TODO: 这几个处理器，之后我会进行介绍
		annotatedArgumentResolvers.add(new MatrixVariableParameterProcessor());
		// TODO: @PathVariableParameterProcessor注解支持
		annotatedArgumentResolvers.add(new PathVariableParameterProcessor());
		// TODO: @RequestParam注解支持
		annotatedArgumentResolvers.add(new RequestParamParameterProcessor());
		// TODO: @RequestHeader注解的支持
		annotatedArgumentResolvers.add(new RequestHeaderParameterProcessor());
		// TODO: @SpringQueryMap注解支持
		annotatedArgumentResolvers.add(new QueryMapParameterProcessor());
		// TODO: @RequestPart注解支持
		annotatedArgumentResolvers.add(new RequestPartParameterProcessor());

		return annotatedArgumentResolvers;
	}

	/**
	 * TODO: 此方法主要作用就是降级，因为有时候使用
	 *    void method(@RequestParam Integer id)
	 *  经常会这样 @RequestParam中没有写value,这种情况会默认去使用参数名
	 * @param parameterAnnotation
	 * @param method
	 * @param parameterIndex
	 * @return
	 */
	private Annotation synthesizeWithMethodParameterNameAsFallbackValue(
			Annotation parameterAnnotation, Method method, int parameterIndex) {
		// TODO: 拿到当前注解的属性
		Map<String, Object> annotationAttributes = AnnotationUtils
				.getAnnotationAttributes(parameterAnnotation);
		// TODO: 把当前注解的默认值拿到
		Object defaultValue = AnnotationUtils.getDefaultValue(parameterAnnotation);
		// TODO: 这里就是判断你是不是使用的默认值
		if (defaultValue instanceof String
				&& defaultValue.equals(annotationAttributes.get(AnnotationUtils.VALUE))) {
			// TODO: 获得 带泛型的参数类型
			Type[] parameterTypes = method.getGenericParameterTypes();
			// TODO: 使用参数发现器(解析器)拿到当前参数的名称，获得参数名
			// TODO: 注意这里获取接口方法上的参数名，是很有可能拿到null的原因是,PARAMETER_NAME_DISCOVERER 默认支持了两种方式，1.根据反射拿，2.根据ASM来操作字节码，来取
			// TODO: 那么第一种方式，反射获取，如果你编译的时候不加 -parameters 参数，那么是不会将方法参数名编译到文件中的，第二种方式，通过ASM来取接口方法参数也取不到，因为没有局部变量表
			// TODO: 所以这里仅适用 编译时开启 -parameters参数，所以强烈建议使用 @RequestParam,@PathVariable时，把value加上.
			String[] parameterNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
			if (shouldAddParameterName(parameterIndex, parameterTypes, parameterNames)) {
				// TODO: 这里就使用参数名 去覆盖掉 默认的value值
				annotationAttributes.put(AnnotationUtils.VALUE,
						parameterNames[parameterIndex]);
			}
		}
		// TODO: 使用你给定的annotationAttributes，以及注解类型，去获得一个Annotation
		return AnnotationUtils.synthesizeAnnotation(annotationAttributes,
				parameterAnnotation.annotationType(), null);
	}

	/**
	 * TODO: 主要判断参数名是否合法，参数名的长度要大于 parameterIndex
	 * @param parameterIndex
	 * @param parameterTypes
	 * @param parameterNames
	 * @return
	 */
	private boolean shouldAddParameterName(int parameterIndex, Type[] parameterTypes,
			String[] parameterNames) {
		// has a parameter name
		// TODO: 参数名列表不能为空，并且参数名数组的长度要大于parameterIndex
		return parameterNames != null && parameterNames.length > parameterIndex
		// has a type
			// TODO: 参数类型也不能为空，并且参数类型的长度也要大于parameterIndex
				&& parameterTypes != null && parameterTypes.length > parameterIndex;
	}

	private boolean isMultipartFormData(MethodMetadata data) {
		Collection<String> contentTypes = data.template().headers()
				.get(HttpEncoding.CONTENT_TYPE);

		if (contentTypes != null && !contentTypes.isEmpty()) {
			String type = contentTypes.iterator().next();
			return Objects.equals(MediaType.valueOf(type), MediaType.MULTIPART_FORM_DATA);
		}

		return false;
	}

	/**
	 * TODO: 实现了Param中的Expander，之前param中的是默认是调用toString方法去填充，而这里进行了一个修改，采用使用转换器的方式去统一的转成String类型
	 * TODO: 目前的状态是过时的，采用了ExpanderFactory去取
	 * @deprecated Not used internally anymore. Will be removed in the future.
	 */
	@Deprecated
	public static class ConvertingExpander implements Param.Expander {

		/**
		 * TODO: 转换服务
		 */
		private final ConversionService conversionService;

		public ConvertingExpander(ConversionService conversionService) {
			this.conversionService = conversionService;
		}

		@Override
		public String expand(Object value) {
			// TODO: 调用它的convert去转换
			return this.conversionService.convert(value, String.class);
		}

	}

	private static class ConvertingExpanderFactory {

		private final ConversionService conversionService;

		ConvertingExpanderFactory(ConversionService conversionService) {
			this.conversionService = conversionService;
		}

		/**
		 * TODO: 获得一个expander
		 * @param typeDescriptor
		 * @return
		 */
		Param.Expander getExpander(TypeDescriptor typeDescriptor) {
			return value -> {
				Object converted = this.conversionService.convert(value, typeDescriptor,
						STRING_TYPE_DESCRIPTOR);
				return (String) converted;
			};
		}

	}

	/**
	 * TODO: 对方法元信息和参数角标的一个简单封装
	 */
	private class SimpleAnnotatedParameterContext
			implements AnnotatedParameterProcessor.AnnotatedParameterContext {

		private final MethodMetadata methodMetadata;

		private final int parameterIndex;

		SimpleAnnotatedParameterContext(MethodMetadata methodMetadata,
				int parameterIndex) {
			this.methodMetadata = methodMetadata;
			this.parameterIndex = parameterIndex;
		}

		@Override
		public MethodMetadata getMethodMetadata() {
			return this.methodMetadata;
		}

		@Override
		public int getParameterIndex() {
			return this.parameterIndex;
		}

		@Override
		public void setParameterName(String name) {
			nameParam(this.methodMetadata, name, this.parameterIndex);
		}

		@Override
		public Collection<String> setTemplateParameter(String name,
				Collection<String> rest) {
			return addTemplateParameter(rest, name);
		}

	}

}
