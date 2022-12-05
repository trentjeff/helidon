/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.helidon.pico.services.VoidServiceProvider;
import io.helidon.pico.spi.Interceptor;
import io.helidon.pico.spi.InvocationContext;
import io.helidon.pico.spi.ServiceProvider;
import io.helidon.pico.spi.TypedElementName;
import io.helidon.pico.spi.impl.DefaultServices;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.TypeName;

import jakarta.inject.Provider;

/**
 * Provides handling for {@link io.helidon.pico.spi.Interceptor} and {@link io.helidon.pico.spi.InvocationContext}.
 * @param <V> the result of the method invocation.
 */
@SuppressWarnings("unchecked")
public class Invocation<V> implements Interceptor.Chain<V>, InvocationContext {
    private Supplier<V> call;
    private Runnable runnable;
    private final ListIterator<Provider<Interceptor>> interceptorIterator;
    private final ServiceProvider<?> interceptedServiceProvider;
    private final TypeName serviceTypeName;
    private final List<AnnotationAndValue> serviceTypeAnnotations;
    private final TypedElementName methodElement;
    private final TypedElementName[] methodArgInfo;
    private final Object[] methodArgs;
    private Map<String, Object> userData;

    protected Invocation(List<Provider<Interceptor>> interceptors,
                         ServiceProvider<?> interceptedServiceProvider,
                         TypeName serviceTypeName,
                         List<AnnotationAndValue> serviceTypeAnnotations,
                         TypedElementName methodElement,
                         TypedElementName[] methodArgInfo,
                         Object[] methodArgs) {
        this.interceptorIterator = interceptors.listIterator();
        this.interceptedServiceProvider = interceptedServiceProvider;
        this.serviceTypeName = serviceTypeName;
        this.serviceTypeAnnotations = serviceTypeAnnotations;
        this.methodElement = methodElement;
        this.methodArgInfo = methodArgInfo;
        this.methodArgs = methodArgs;
    }

    @Override
    public String toString() {
        return "invocation of " + serviceTypeName + "::" + methodElement;
    }

    protected Invocation<V> setCall(Supplier<V> call) {
        this.call = call;
        return this;
    }

    protected Invocation<V> setCall(Runnable call) {
        this.runnable = call;
        return this;
    }

    /**
     * Creates an instance of {@link io.helidon.pico.spi.ext.Invocation} and invokes it in this context.
     *
     * @param call                  the call to the base service provider's method
     * @param interceptors          the list of interceptors in the chain
     * @param serviceProvider       the base service provider
     * @param serviceTypeName       the service type name of the base service provider
     * @param serviceTypeAnnotations the service type's annotations
     * @param methodElement         the method to call
     * @param methodArgInfo         the method's argument info
     * @param methodArgs            the literal method arguments
     * @param <V>                   the type returned from the method element
     * @return the invocation instance
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <V> V createInvokeAndSupply(Supplier<V> call,
                                              List<Provider<Interceptor>> interceptors,
                                              ServiceProvider serviceProvider,
                                              TypeName serviceTypeName,
                                              List<AnnotationAndValue> serviceTypeAnnotations,
                                              TypedElementName methodElement,
                                              Object[] methodArgs,
                                              TypedElementName... methodArgInfo) {
        if (Objects.isNull(interceptors) || interceptors.isEmpty()) {
            return call.get();
        }

        return (V) new Invocation(interceptors,
                                  serviceProvider,
                                  serviceTypeName,
                                  serviceTypeAnnotations,
                                  methodElement,
                                  methodArgInfo,
                                  methodArgs).setCall(call).proceed();
    }

    /**
     * Creates an instance of {@link io.helidon.pico.spi.ext.Invocation} and invokes it in this context.
     *
     * @param call                  the call to the base service provider's method
     * @param interceptors          the list of interceptors in the chain
     * @param serviceProvider       the base service provider
     * @param serviceTypeName       the service type name of the base service provider
     * @param serviceTypeAnnotations the service type's annotations
     * @param methodArgs            the literal method arguments
     * @param methodElement         the method to call
     * @param methodArgInfo         the method's argument info
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static void createAndInvoke(Runnable call,
                                       List<Provider<Interceptor>> interceptors,
                                       ServiceProvider<?> serviceProvider,
                                       TypeName serviceTypeName,
                                       List<AnnotationAndValue> serviceTypeAnnotations,
                                       TypedElementName methodElement,
                                       Object[] methodArgs,
                                       TypedElementName... methodArgInfo) {
        if (Objects.isNull(interceptors) || interceptors.isEmpty()) {
            call.run();
        } else {
            new Invocation(interceptors,
                           serviceProvider,
                           serviceTypeName,
                           serviceTypeAnnotations,
                           methodElement,
                           methodArgInfo,
                           methodArgs).setCall(call).proceed();
        }
    }

    /**
     * Merges a variable number of lists together, where the net result is the merged set of non-null providers
     * ranked in proper weight order, or null if the list would have otherwise been empty.
     *
     * @param lists the lists to merge
     * @param <T>   the type of the provider
     * @return the merged result, or null instead of empty lists
     */
    public static <T> List<Provider<T>> mergeAndCollapse(List<Provider<T>>... lists) {
        List<Provider<T>> result = null;

        for (List<Provider<T>> list : lists) {
            if (Objects.isNull(list)) {
                continue;
            }

            for (Provider<T> p : list) {
                if (Objects.isNull(p)) {
                    continue;
                }

                if (p instanceof ServiceProvider
                        && VoidServiceProvider.getServiceTypeName()
                                    .equals(((ServiceProvider) p).getServiceInfo().getServiceTypeName())) {
                    continue;
                }

                if (Objects.isNull(result)) {
                    result = new ArrayList<>();
                }

                if (!result.contains(p)) {
                    result.add(p);
                }
            }
        }

        if (Objects.nonNull(result) && result.size() > 1) {
            result.sort(DefaultServices.getServiceProviderComparator());
        }

        return Objects.nonNull(result) ? Collections.unmodifiableList(result) : result;
    }

    @Override
    public V proceed() {
        if (!interceptorIterator.hasNext()) {
            assert (Objects.nonNull(this.call) || Objects.nonNull(this.runnable));

            if (Objects.nonNull(this.call)) {
                Supplier<V> call = this.call;
                this.call = null;
                return call.get();
            } else if (Objects.nonNull(this.runnable)) {
                Runnable call = this.runnable;
                this.runnable = null;
                call.run();
                return null;
            } else {
                throw new AssertionError("unknown call type: " + this);
            }
        } else {
            return interceptorIterator.next()
                    .get()
                    .proceed(this, this);
        }
    }

    @Override
    public ServiceProvider<?> getRootServiceProvider() {
        return interceptedServiceProvider;
    }

    @Override
    public TypeName getServiceTypeName() {
        return serviceTypeName;
    }

    @Override
    public List<AnnotationAndValue> getClassAnnotations() {
        return serviceTypeAnnotations;
    }

    @Override
    public TypedElementName getElementInfo() {
        return methodElement;
    }

    @Override
    public TypedElementName[] getElementArgInfo() {
        return methodArgInfo;
    }

    @Override
    public Object[] getElementArgs() {
        return methodArgs;
    }

    @Override
    public Map<String, Object> getContextData() {
        if (Objects.isNull(userData)) {
            userData = new ConcurrentHashMap<>();
        }
        return userData;
    }
}
