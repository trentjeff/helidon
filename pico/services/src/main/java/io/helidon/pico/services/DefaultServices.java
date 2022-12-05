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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.InjectionException;
import io.helidon.pico.Intercepted;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.ServiceBinder;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.ServiceProviderProvider;
import io.helidon.pico.Services;
import io.helidon.pico.spi.ExtendedServices;
import io.helidon.pico.spi.Metrics;
import io.helidon.pico.spi.Resetable;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * The default reference implementation of the service registry {@link Services}.
 */
@Singleton
public class DefaultServices implements ExtendedServices {
    private final ConcurrentHashMap<String, ServiceProvider<?>> servicesByTypeName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<ServiceProvider<?>>> servicesByContract = new ConcurrentHashMap<>();
    private final Map<ServiceInfo, List<ServiceProvider<?>>> cache = Collections.synchronizedMap(new WeakHashMap<>());
    private final boolean isDynamic;
    private final boolean isCacheEnabled;
    private final AtomicInteger lookupCount = new AtomicInteger();
    private final AtomicInteger cacheLookupCount = new AtomicInteger();
    private final AtomicInteger cacheHitCount = new AtomicInteger();

    /**
     * The default constructor.
     */
    public DefaultServices() {
        this(PicoServicesConfig.DEFAULT_SUPPORTS_DYNAMIC, DEFAULT_IS_CACHE_ENABLED);
    }

    /**
     * The constructor taking a configuration.
     *
     * @param config the config
     */
    public DefaultServices(PicoServicesConfig config) {
        this(config.getValue(PicoServicesConfig.KEY_SUPPORTS_DYNAMIC,
                PicoServicesConfig.defaultValue(PicoServicesConfig.DEFAULT_SUPPORTS_DYNAMIC)), DEFAULT_IS_CACHE_ENABLED);
    }

    /**
     * The constructor taking a configuration.
     *
     * @param defaultServicesDynamic flag indicating whether the services registry is dynamic
     * @param isCacheEnabled flag indicating whether caching should be used (will require more memory)
     */
    public DefaultServices(Boolean defaultServicesDynamic, Boolean isCacheEnabled) {
        this.isDynamic = (Objects.nonNull(defaultServicesDynamic) && defaultServicesDynamic);
        this.isCacheEnabled = (!isDynamic && ((Objects.nonNull(isCacheEnabled) && isCacheEnabled)));
    }

    public Integer getSize() {
        return servicesByTypeName.size();
    }

    /**
     * Clear the registry and cache.
     */
    @Override
    public void hardReset() {
        servicesByTypeName.values().forEach((sp) -> {
            if (sp instanceof Resetable) {
                ((Resetable) sp).hardReset();
            }
        });
        servicesByTypeName.clear();
        servicesByContract.clear();
        cache.clear();
        lookupCount.set(0);
        cacheLookupCount.set(0);
        cacheHitCount.set(0);
    }

    @Override
    public boolean softReset() {
        return false;
    }

    /**
     * Clear the registry and cache, without attempting to deactivate anything.
     */
    @Override
    public void clear() {
        servicesByTypeName.values().forEach((sp) -> {
            if (sp instanceof Resetable) {
                ((Resetable) sp).clear();
            }
        });
        servicesByTypeName.clear();
        servicesByContract.clear();
        cache.clear();
    }

    public boolean isDynamic() {
        return isDynamic;
    }

    public boolean isCacheEnabled() {
        return isCacheEnabled;
    }

    @Override
    public <T> ServiceProvider<T> lookupFirst(Class<T> type, String name, boolean expected) {
        DefaultServiceInfo serviceInfo = DefaultServiceInfo.builder()
                .contractImplemented(type.getName())
                .named(name)
                .build();
        List<ServiceProvider<T>> result = lookup(serviceInfo, expected);
        assert (!expected || !result.isEmpty());
        return (result.isEmpty()) ? null : result.get(0);
    }

    @Override
    public <T> ServiceProvider<T> lookupFirst(ServiceInfo serviceInfo, boolean expected) {
        List<ServiceProvider<T>> result = lookup(serviceInfo, expected);
        assert (!expected || !result.isEmpty());
        return result.isEmpty() ? null : result.get(0);
    }

    @Override
    public <T> List<ServiceProvider<T>> lookupAll(Class<T> type) {
        return null;
    }

    @Override
    public <T> List<ServiceProvider<T>> lookupAll(ServiceInfo criteria) {
        return null;
    }

    @Override
    public <T> List<ServiceProvider<T>> lookupAll(ServiceInfo criteria, boolean expected) {
        return null;
    }

    @Override
    public <T> List<ServiceProvider<T>> lookup(Class<T> type) {
        DefaultServiceInfo serviceInfo = DefaultServiceInfo.builder()
                .contractImplemented(type.getName())
                .build();
        return lookup(serviceInfo, false);
    }

    @Override
    public <T> List<ServiceProvider<T>> lookup(ServiceInfo criteria) {
        return lookup(criteria, true);
    }

    @Override
    public Optional<Metrics> metrics() {
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    <T> List<ServiceProvider<T>> getAllServiceProviders(boolean explode) {
        if (explode) {
            return (List) explodeAndSort(servicesByTypeName.values(), null, false);
        }

        return new ArrayList<>((Collection) servicesByTypeName.values());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> List<ServiceProvider<T>> lookup(ServiceInfo criteria, boolean expected) {
        List<ServiceProvider<?>> result;

        lookupCount.incrementAndGet();

        if (ServiceInfoBasics.hasContracts(criteria)) {
            String serviceTypeName = criteria.getServiceTypeName();
            boolean hasOneContractInCriteria = (1 == criteria.getContractsImplemented().size());
            String theOnlyContractRequested = (hasOneContractInCriteria)
                    ? criteria.getContractsImplemented().iterator().next() : null;
            if (Objects.isNull(serviceTypeName) && hasOneContractInCriteria && criteria.getQualifiers().isEmpty()) {
                serviceTypeName = theOnlyContractRequested;
            }
            if (Objects.nonNull(serviceTypeName)) {
                ServiceProvider<?> exact = servicesByTypeName.get(serviceTypeName);
                if (Objects.nonNull(exact) && !ServiceProviderBindable.isIntercepted(exact)) {
                    return (List) explodeAndSort(Collections.singletonList(exact), criteria, expected);
                }
            }
            if (hasOneContractInCriteria) {
                Set<ServiceProvider<?>> subsetOfMatches = servicesByContract.get(theOnlyContractRequested);
                if (Objects.nonNull(subsetOfMatches)) {
                    result = subsetOfMatches.stream().parallel()
                            .filter(sp -> sp.getServiceInfo().matches(criteria))
                            .collect(Collectors.toList());
                    if (!result.isEmpty()) {
                        return (List) explodeAndSort(result, criteria, expected);
                    }
                }
            }
        }

        if (isCacheEnabled()) {
            result = cache.get(criteria);
            cacheLookupCount.incrementAndGet();
            if (Objects.nonNull(result)) {
                cacheHitCount.incrementAndGet();
                return (List) result;
            }
        }

        // table scan :-(
        result = servicesByTypeName.values()
                .stream().parallel()
                .filter(sp -> sp.getServiceInfo().matches(criteria))
                .collect(Collectors.toList());
        if (expected && result.isEmpty()) {
            throw resolutionBasedInjectionError(criteria);
        }

        if (!result.isEmpty()) {
            result = explodeAndSort(result, criteria, expected);
        }

        if (isCacheEnabled
                && ((DEFAULT_TRACK_EMPTIES_IN_CACHE || !result.isEmpty()))) {
            cache.put(DefaultServiceInfo.cloneCopy(criteria), List.copyOf(result));
        }

        return (List) result;
    }

    @SuppressWarnings("unchecked")
    protected <T> List<T> explodeAndSort(Collection<T> coll, ServiceInfo criteria, boolean expected) {
        List result;

        if ((coll.size() > 1)
                || coll.stream().anyMatch(sp -> sp instanceof ServiceProviderProvider)) {
            result = new ArrayList<>();

            coll.forEach(s -> {
                if (s instanceof ServiceProviderProvider) {
                    List<? extends ServiceProvider<?>> subList = ((ServiceProviderProvider) s)
                            .getServiceProviders(criteria, true, true);
                    if (Objects.nonNull(subList) && !subList.isEmpty()) {
                        subList.stream().filter(Objects::nonNull).forEach(result::add);
                    }
                } else {
                    result.add(s);
                }
            });

            if (result.size() > 1) {
                result.sort(DefaultServices.getServiceProviderComparator());
            }

            return result;
        } else {
            result = (coll instanceof List) ? (List) coll : new ArrayList<>(coll);
        }

        if (expected && result.isEmpty()) {
            throw resolutionBasedInjectionError(criteria);
        }

        return result;
    }

    /**
     * Provides a means to validate whether a service provider is being carried in the registry, and if so may optionally
     * return the delegate service provider to use. Most often, however, the delegate returned is actually the service provider
     * that was passed in.
     *
     * @param serviceProvider the service provider to search for by its {@link io.helidon.pico.spi.ServiceInfo#getServiceTypeName()}
     * @return the service provider in the registry, or null if not found
     */
    public ServiceProvider<?> getServiceProvider(ServiceProvider<?> serviceProvider) {
        return servicesByTypeName.get(serviceProvider.getServiceInfo().getServiceTypeName());
    }

    protected static String toModuleName(Module module) {
        return module.getName().orElse(null);
    }

    @SuppressWarnings("rawtypes")
    protected ServiceBinder createServiceBinder(PicoServices picoServices,
                                                DefaultServices serviceRegistry,
                                                String moduleName) {
        return new io.helidon.pico.spi.impl.DefaultServiceBinder(picoServices, serviceRegistry, moduleName);
    }

    protected void bind(PicoServices picoServices, Module module) {
        String moduleName = toModuleName(module);
        ServiceBinder moduleServiceBinder = createServiceBinder(picoServices, this, moduleName);
        module.configure(moduleServiceBinder);
        bind(picoServices, DefaultServices.toServiceProvider(module, moduleName));
    }

    protected void bind(PicoServices picoServices, ServiceProvider<?> serviceProvider) {
        ServiceInfo serviceInfo = DefaultServices.getValidatedServiceInfo(serviceProvider);
        String serviceTypeName = serviceInfo.getServiceTypeName();

        ServiceProvider<?> previous = servicesByTypeName.putIfAbsent(serviceTypeName, serviceProvider);
        if (Objects.nonNull(previous) && previous != serviceProvider) {
            if (isDynamic()) {
                DefaultPicoServices.LOGGER.log(System.Logger.Level.WARNING,
                                               "overwriting " + previous + " with " + serviceProvider);
                servicesByTypeName.put(serviceTypeName, serviceProvider);
            } else {
                throw serviceProviderAlreadyBoundInjectionError(previous, serviceProvider);
            }
        }

        // special handling in case we are an interceptor...
        Set<QualifierAndValue> qualifiers = serviceInfo.getQualifiers();
        Optional<QualifierAndValue> interceptedQualifier = qualifiers.stream()
                .filter((q) -> q.getQualifierTypeName().equals(Intercepted.class.getName()))
                .findFirst();
        if (interceptedQualifier.isPresent()) {
            // assumption: expected that the root service provider is registered prior to any interceptors ...
            String interceptedServiceTypeName = Objects.requireNonNull(interceptedQualifier.get().getValue());
            ServiceProvider<?> interceptedSp = lookupFirst(DefaultServiceInfo.builder()
                                                                   .serviceTypeName(interceptedServiceTypeName)
                                                                   .build());
            if (interceptedSp instanceof ServiceProviderBindable) {
                ((ServiceProviderBindable<?>) interceptedSp).setInterceptor(serviceProvider);
            }
        }

        servicesByContract.compute(serviceTypeName, (contract, servicesSharingThisContract) -> {
            if (Objects.isNull(servicesSharingThisContract)) {
                servicesSharingThisContract = new TreeSet<>(getServiceProviderComparator());
            }
            boolean added = servicesSharingThisContract.add(serviceProvider);
            assert (added) : "expected to have added: " + serviceProvider;
            return servicesSharingThisContract;
        });
        for (String cn : serviceInfo.getContractsImplemented()) {
            servicesByContract.compute(cn, (contract, servicesSharingThisContract) -> {
                if (Objects.isNull(servicesSharingThisContract)) {
                    servicesSharingThisContract = new TreeSet<>(getServiceProviderComparator());
                }
                boolean ignored = servicesSharingThisContract.add(serviceProvider);
                return servicesSharingThisContract;
            });
        }
    }

    /**
     * First use weight, then use FQN of the service type name as the secondary comparator if weights are the same.
     *
     * @return the pico comparator
     */
    // note: should always keep a singleton of the comparator around since it is used so frequently?
    public static Comparator<? super Provider<?>> getServiceProviderComparator() {
        return new ServiceProviderComparator();
    }

    protected static ServiceProvider<?> toServiceProvider(Module module, String moduleName) {
        return new io.helidon.pico.spi.impl.BasicModule(module, moduleName);
    }

    protected static ServiceInfo getValidatedServiceInfo(ServiceProvider<?> serviceProvider) {
        ServiceInfo info = serviceProvider.getServiceInfo();
        Objects.requireNonNull(info, () -> "service info is required for " + serviceProvider);
        Objects.requireNonNull(info.getServiceTypeName(), () -> "service type name is required for " + serviceProvider);
        return info;
    }

    protected static InjectionException serviceProviderAlreadyBoundInjectionError(ServiceProvider<?> previous,
                                                                                  ServiceProvider<?> serviceProvider) {
        return new InjectionException("service provider already bound by " + previous, null, serviceProvider);
    }

    /**
     * Throws an exception based upon trying to resolve a service info context.
     *
     * @param ctx the context trying to be resolved
     * @return the injection exception
     */
    public static InjectionException resolutionBasedInjectionError(ServiceInfo ctx) {
        return new InjectionException("expected to resolve a service instance matching " + ctx);
    }

    @Override
    public int getLookupCount() {
        return lookupCount.get();
    }

    @Override
    public int getCacheLookupCount() {
        return cacheLookupCount.get();
    }

    @Override
    public int getCacheHitCount() {
        return cacheHitCount.get();
    }

}
