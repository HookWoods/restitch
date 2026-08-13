package io.github.hookwoods.restitch.boot4;

import io.github.hookwoods.restitch.api.AggregateResponse;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

/** Proxies Spring beans that expose methods annotated with {@link AggregateResponse}. */
public final class AggregateResponseBeanPostProcessor implements BeanPostProcessor {
    private final AggregateResponseInterceptor interceptor;

    /**
     * Creates a post-processor backed by an aggregate response interceptor.
     *
     * @param interceptor interceptor applied to annotated method invocations
     */
    public AggregateResponseBeanPostProcessor(AggregateResponseInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!hasAggregateResponseMethod(bean.getClass())) {
            return bean;
        }
        ProxyFactory proxyFactory = new ProxyFactory(bean);
        proxyFactory.setProxyTargetClass(true);
        MethodInterceptor advice = invocation -> interceptor.invoke(
                invocation.getThis(), invocation.getMethod(), invocation.getArguments());
        proxyFactory.addAdvice(advice);
        return proxyFactory.getProxy(bean.getClass().getClassLoader());
    }

    private static boolean hasAggregateResponseMethod(Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isAnnotationPresent(AggregateResponse.class)) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }
}
