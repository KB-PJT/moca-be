package com.moca.mocabe.global.config;

import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FrameworkServlet;

public class WebApplicationInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class<?>[]{PersistenceConfig.class, RedisConfig.class, AuthConfig.class, LocalTestAuthConfig.class,
                AppConfig.class};
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[]{WebMvcConfig.class};
    }

    @Override
    protected String[] getServletMappings() {
        return new String[]{"/"};
    }

    @Override
    protected FrameworkServlet createDispatcherServlet(WebApplicationContext servletApplicationContext) {
        DispatcherServlet dispatcherServlet = new DispatcherServlet(servletApplicationContext);
        dispatcherServlet.setThrowExceptionIfNoHandlerFound(true);
        return dispatcherServlet;
    }

    @Override
    protected javax.servlet.Filter[] getServletFilters() {
        return new javax.servlet.Filter[]{new DelegatingFilterProxy("springSecurityFilterChain")};
    }
}
