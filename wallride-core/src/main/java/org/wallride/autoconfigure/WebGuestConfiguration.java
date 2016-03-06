/*
 * Copyright 2014 Tagbangers, Inc.
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

package org.wallride.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.validation.MessageCodesResolver;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.wallride.repository.MediaRepository;
import org.wallride.service.BlogService;
import org.wallride.service.PageService;
import org.wallride.web.controller.guest.IndexController;
import org.wallride.web.controller.guest.page.PageDescribeController;
import org.wallride.web.support.*;

import java.text.DateFormat;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Configuration
@ComponentScan(basePackageClasses = IndexController.class)
public class WebGuestConfiguration extends WebMvcConfigurationSupport {

	private static final String CLASSPATH_RESOURCE_LOCATION = "classpath:/resources/guest/";

	@Autowired
	private WallRideProperties wallRideProperties;

	@Autowired
	private MessageCodesResolver messageCodesResolver;

	@Autowired
	private BlogService blogService;

	@Autowired
	private PageService pageService;

	@Autowired
	private MediaRepository mediaRepository;

	@Autowired
	private ResourceLoader resourceLoader;

	@Override
	public RequestMappingHandlerMapping requestMappingHandlerMapping() {
		RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();

//		handlerMapping.setUrlPathHelper(new LanguageUrlPathHelper(blogService));
		handlerMapping.setDefaultHandler(new PageDescribeController(blogService, pageService));

		handlerMapping.setOrder(Integer.MAX_VALUE);
		handlerMapping.setInterceptors(getInterceptors());
		handlerMapping.setContentNegotiationManager(mvcContentNegotiationManager());
		return handlerMapping;
//		return super.requestMappingHandlerMapping();    //To change body of overridden methods use File | Settings | File Templates.
	}

	@Override
	public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
//		converters.add(new FormHttpMessageConverter());

		converters.add(new ByteArrayHttpMessageConverter());
		converters.add(new ResourceHttpMessageConverter());

		MappingJackson2HttpMessageConverter jackson = new MappingJackson2HttpMessageConverter();
		ObjectMapper objectMapper = new ObjectMapper();
//	    objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZ");
		objectMapper.setDateFormat(dateFormat);
		jackson.setObjectMapper(objectMapper);
		converters.add(jackson);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/resources/**").addResourceLocations(
				wallRideProperties.getHome() + "themes/default/resources/",
				CLASSPATH_RESOURCE_LOCATION);
		registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
		registry.setOrder(Integer.MIN_VALUE);
	}

	@Override
	public void addFormatters(FormatterRegistry registry) {
		registry.addFormatter(new org.springframework.format.Formatter<String>() {
			@Override
			public String print(String object, Locale locale) {
				return (!object.equals("") ? object : null);
			}

			@Override
			public String parse(String text, Locale locale) throws ParseException {
				String value = StringUtils.trimWhitespace(text);
				return Normalizer.normalize(value, Normalizer.Form.NFKC);
			}
		});
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		BlogLanguageMethodArgumentResolver blogLanguageMethodArgumentResolver = new BlogLanguageMethodArgumentResolver();
		blogLanguageMethodArgumentResolver.setBlogService(blogService);

		argumentResolvers.add(new PageableHandlerMethodArgumentResolver());
		argumentResolvers.add(new AuthorizedUserMethodArgumentResolver());
		argumentResolvers.add(blogLanguageMethodArgumentResolver);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(defaultModelAttributeInterceptor());
		registry.addInterceptor(setupRedirectInterceptor());
	}

	@Override
	public MessageCodesResolver getMessageCodesResolver() {
		return messageCodesResolver;
	}

	// additional webmvc-related beans

	@Bean
	public SimpleUrlHandlerMapping faviconHandlerMapping(WebApplicationContext context) {
		ResourceHttpRequestHandler requestHandler = new ResourceHttpRequestHandler();
		requestHandler.setApplicationContext(context);

		List<org.springframework.core.io.Resource> locations = new ArrayList<>();
		locations.add(resourceLoader.getResource(wallRideProperties.getHome() + "themes/default/resources/"));
		locations.add(resourceLoader.getResource(CLASSPATH_RESOURCE_LOCATION));
		locations.add(resourceLoader.getResource("classpath:/"));
		requestHandler.setLocations(locations);

		SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
		mapping.setOrder(Integer.MIN_VALUE + 1);
		mapping.setUrlMap(Collections.singletonMap("**/favicon.ico", requestHandler));
		return mapping;
	}

	@Bean
	public SimpleUrlHandlerMapping mediaUrlHandlerMapping() {
		MediaHttpRequestHandler mediaHttpRequestHandler = new MediaHttpRequestHandler();
		mediaHttpRequestHandler.setWallRideProperties(wallRideProperties);
//		mediaHttpRequestHandler.setBlogService(blogService);
		mediaHttpRequestHandler.setMediaRepository(mediaRepository);
		mediaHttpRequestHandler.setResourceLoader(resourceLoader);
		mediaHttpRequestHandler.setCacheSeconds(86400);

		Map<String, HttpRequestHandler> urlMap = new LinkedHashMap<>();
		urlMap.put("/media/{key}", mediaHttpRequestHandler);

		SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
		handlerMapping.setOrder(0);
		handlerMapping.setUrlMap(urlMap);
		return handlerMapping;
	}

	@Bean
	public DefaultModelAttributeInterceptor defaultModelAttributeInterceptor() {
		DefaultModelAttributeInterceptor defaultModelAttributeInterceptor = new DefaultModelAttributeInterceptor();
		defaultModelAttributeInterceptor.setBlogService(blogService);
		return defaultModelAttributeInterceptor;
	}

	@Bean
	public SetupRedirectInterceptor setupRedirectInterceptor() {
		SetupRedirectInterceptor setupRedirectInterceptor = new SetupRedirectInterceptor();
		setupRedirectInterceptor.setBlogService(blogService);
		return setupRedirectInterceptor;
	}

	@Bean
	public LocaleResolver localeResolver() {
		BlogLanguageLocaleResolver blogLanguageLocaleResolver = new BlogLanguageLocaleResolver();
		blogLanguageLocaleResolver.setBlogService(blogService);
		return blogLanguageLocaleResolver;
	}
}
