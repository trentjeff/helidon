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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.builder.Builder;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.InjectionException;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.Interceptor;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.ServiceProviderProvider;
import io.helidon.pico.Services;
import io.helidon.pico.spi.InjectionResolver;

/**
 * The injection plan.
 *
 * @param <T> the type receiver for the injection
 */
public class InjectionPlan<T> {
    static final System.Logger LOGGER = System.getLogger(InjectionPlan.class.getName());

    private String identity;
    private DefaultInjectionPointInfo ipInfo;
    private List<ServiceProvider<T>> ipQualifiedServiceProviders;
    @JsonIgnore private List<Object> ipUnqualifiedServiceProviderResolutions;
    @JsonIgnore private ServiceProvider<?> selfServiceProvider;
    private boolean wasResolved;
    private T resolved;

    @Override
    public String toString() {
        return Objects.toString(ipInfo.getIdentity());
    }

    /**
     * Converts the inputs to an injection plan.
     *
     * @param picoServices pico services instance
     * @param dependencies the dependencies
     * @param resolveIps   flag indicating whether injection points should be resolved
     * @param self         the reference to the service provider associated with this plan
     * @return the injection plan per element name
     */
    public static Map<String, InjectionPlan<Object>> toInjectionPlans(PicoServices picoServices,
                                                                      io.helidon.pico.spi.ext.Dependencies dependencies,
                                                                      boolean resolveIps,
                                                                      ServiceProvider<?> self) {
        Map<String, InjectionPlan<Object>> result = new LinkedHashMap<>();
        if (Objects.isNull(dependencies) || dependencies.getDependencies().isEmpty()) {
            return result;
        }

        final Services services = picoServices.getServices();
        final PicoServicesConfig config = picoServices.getConfig().get();
        final boolean isPrivateSupported = config
                .getValue(PicoServicesConfig.KEY_SUPPORTS_JSR330_PRIVATE,
                        PicoServicesConfig.defaultValue(PicoServicesConfig.DEFAULT_SUPPORTS_PRIVATE));
        final boolean isStaticSupported = config
                .getValue(PicoServicesConfig.KEY_SUPPORTS_JSR330_STATIC,
                        PicoServicesConfig.defaultValue(PicoServicesConfig.DEFAULT_SUPPORTS_STATIC));

        dependencies.getDependencies()
                .forEach((dep) -> {
                    ServiceInfo depTo = dep.getDependencyTo();
                    if (Objects.nonNull(self)) {
                        final ServiceInfo selfInfo = self.getServiceInfo();
                        final Double weight = selfInfo.getWeight();
                        if (Objects.nonNull(weight)
                                && selfInfo.getContractsImplemented()
                                        .containsAll(dep.getDependencyTo().getContractsImplemented())) {
                            // if we have a weight on ourselves, and we inject an interface that we actually offer, then
                            // be sure to use it to get lower weighted injection points ...
                            depTo = ((DefaultServiceInfo) depTo).toBuilder().weight(weight).build();
                        }
                    }

                    if (self instanceof InjectionResolver) {
                        dep.getIpDependencies()
                                .stream()
                                .filter((ipInfo) ->
                                        (isPrivateSupported || ipInfo.getAccess() != InjectionPointInfo.Access.PRIVATE)
                                                && (isStaticSupported || !ipInfo.staticDecl()))
                                .forEach((ipInfo) -> {
                                    String id = ipInfo.getIdentity();
                                    if (!result.containsKey(id)) {
                                        Object resolved = ((InjectionResolver) self)
                                                .resolve(ipInfo, picoServices, self, resolveIps);
                                        if (Objects.nonNull(resolved)) {
                                            Object target = (resolved instanceof Optional<?>
                                                                 && ((Optional) resolved).isEmpty()) ? null : resolved;
                                            InjectionPlan<Object> plan = InjectionPlan.builder()
                                                    .identity(id)
                                                    .ipInfo(ipInfo)
                                                    .ipQualifiedServiceProviders(toIpQualified(target))
                                                    .ipUnqualifiedServiceProviderResolutions(toIpUnqualified(target))
                                                    .wasResolved(true)
                                                    .resolved(target)
                                                    .build();
                                            Object prev = result.put(id, plan);
                                            assert (Objects.isNull(prev)) : ipInfo;
                                        }
                                    }
                                });
                    }

                    List<ServiceProvider<Object>> tmpServiceProviders = services.lookup(depTo, false);
                    if (Objects.isNull(tmpServiceProviders) || tmpServiceProviders.isEmpty()) {
                        if (io.helidon.pico.spi.ext.VoidServiceProvider.INSTANCE.getServiceInfo().matches(depTo)) {
                            tmpServiceProviders = io.helidon.pico.spi.ext.VoidServiceProvider.LIST_INSTANCE;
                        }
                    }

                    // filter down the selections to not include self...
                    final List<ServiceProvider<Object>> serviceProviders =
                            (Objects.nonNull(self) && Objects.nonNull(tmpServiceProviders) && !tmpServiceProviders.isEmpty())
                                    ? tmpServiceProviders.stream()
                                        .filter((sp) -> !isSelf(self, sp))
                                        .collect(Collectors.toList())
                                    : tmpServiceProviders;

                    dep.getIpDependencies()
                        .stream()
                        .filter((ipInfo) ->
                                (isPrivateSupported || ipInfo.getAccess() != InjectionPointInfo.Access.PRIVATE)
                                        && (isStaticSupported || !ipInfo.staticDecl()))
                        .forEach((ipInfo) -> {
                            String id = ipInfo.getIdentity();
                            if (!result.containsKey(id)) {
                                Object resolved = (resolveIps) ? resolve(picoServices, self, ipInfo, serviceProviders) : null;
                                if (!resolveIps && !ipInfo.isOptionalWrapped()
                                        && (Objects.isNull(serviceProviders) || serviceProviders.isEmpty())
                                        && !allowNullableInjectionPoint(picoServices, ipInfo)) {
                                    throw new InjectionException("expected to resolve a service instance appropriate for "
                                        + getServiceTypeName(ipInfo) + "." + ipInfo.getElementName(),
                                                                 DefaultServices.resolutionBasedInjectionError(
                                                     ipInfo.getDependencyToServiceInfo()), self);
                                }
                                InjectionPlan<Object> plan = InjectionPlan.builder()
                                        .identity(id)
                                        .ipInfo(ipInfo)
                                        .ipQualifiedServiceProviders(serviceProviders)
                                        .wasResolved(resolveIps)
                                        .resolved((resolved instanceof Optional<?>
                                                && ((Optional) resolved).isEmpty()) ? null : resolved)
                                        .build();
                                Object prev = result.put(id, plan);
                                assert (Objects.isNull(prev)) : ipInfo;
                            }
                    });
                }
        );

        return result;
    }

    @SuppressWarnings("unchecked")
    protected static List<ServiceProvider<Object>> toIpQualified(Object target) {
        if (target instanceof Collection) {
            List<ServiceProvider<Object>> result = new LinkedList<>();
            ((Collection<?>) target).stream()
                    .map(InjectionPlan::toIpQualified)
                    .forEach(result::addAll);
            return result;
        }

        return (target instanceof io.helidon.pico.spi.ext.AbstractServiceProvider)
                ? Collections.singletonList((ServiceProvider<Object>) target)
                : Collections.emptyList();
    }

    protected static List<Object> toIpUnqualified(Object target) {
        if (target instanceof Collection) {
            List<Object> result = new LinkedList<>();
            ((Collection<?>) target).stream()
                    .map(InjectionPlan::toIpUnqualified)
                    .forEach(result::addAll);
            return result;
        }

        return (target instanceof io.helidon.pico.spi.ext.AbstractServiceProvider)
                ? Collections.emptyList()
                : Collections.singletonList(target);
    }

    protected static boolean isSelf(ServiceProvider<?> self, Object other) {
        assert (Objects.nonNull(self));

        if (self == other) {
            return true;
        }

        if (self instanceof ServiceProviderBindable && other == ((ServiceProviderBindable) self).getInterceptor()) {
            return true;
        }

        return false;
    }

    protected static boolean allowNullableInjectionPoint(PicoServices picoServices, DefaultInjectionPointInfo ipInfo) {
        ServiceInfo missingServiceInfo = ipInfo.getDependencyToServiceInfo();
        Set<String> contractsNeeded = missingServiceInfo.getContractsImplemented();
        return (1 == contractsNeeded.size() && contractsNeeded.contains(Interceptor.class.getName()));
    }

    @SuppressWarnings("unchecked")
    protected static Object resolve(PicoServices picoServices,
                                    ServiceProvider<?> self,
                                    DefaultInjectionPointInfo ipInfo,
                                    List<ServiceProvider<Object>> serviceProviders) {
        if (ipInfo.staticDecl()) {
            throw new InjectionException(ipInfo + ": static is not supported", null, self);
        }
        if (ipInfo.getAccess() == InjectionPointInfo.Access.PRIVATE) {
            throw new InjectionException(ipInfo + ": private is not supported", null, self);
        }

        try {
            if (Void.class.getName().equals(ipInfo.getServiceTypeName())) {
                return null;
            }

            if (ipInfo.isListWrapped()) {
                if (ipInfo.isOptionalWrapped()) {
                    throw new InjectionException("Optional + List injection is not supported for "
                            + getServiceTypeName(ipInfo) + "." + ipInfo.getElementName());
                }

                if (serviceProviders.isEmpty()) {
                    if (!allowNullableInjectionPoint(picoServices, ipInfo)) {
                        throw new InjectionException("expected to resolve a service instance appropriate for "
                                                             + getServiceTypeName(ipInfo) + "." + ipInfo.getElementName(),
                                                     DefaultServices
                                                             .resolutionBasedInjectionError(ipInfo.getDependencyToServiceInfo()),
                                                     self);
                    } else {
                        return serviceProviders;
                    }
                }

                if (ipInfo.isProviderWrapped() && !ipInfo.isOptionalWrapped()) {
                    return serviceProviders;
                }

                if (ipInfo.isListWrapped() && !ipInfo.isOptionalWrapped()) {
                    return toEligibleInjectionRefs(ipInfo, serviceProviders, true);
                }
            } else if (serviceProviders.isEmpty()) {
                if (ipInfo.isOptionalWrapped()) {
                    return Optional.empty();
                } else {
                    throw new InjectionException("expected to resolve a service instance appropriate for "
                            + getServiceTypeName(ipInfo) + "." + ipInfo.getElementName(),
                                 DefaultServices.resolutionBasedInjectionError(ipInfo.getDependencyToServiceInfo()), self);
                }
            } else {
                ServiceProvider<Object> serviceProvider = serviceProviders.get(0);
                ServiceProviderBindable<Object> serviceProviderBindable =
                        ServiceProviderBindable.toBindableProvider(ServiceProviderBindable.toRootProvider(serviceProvider));
                if (serviceProviderBindable != serviceProvider
                        && serviceProviderBindable instanceof ServiceProviderProvider) {
                    serviceProvider = serviceProviderBindable;
                    serviceProviders = (List<ServiceProvider<Object>>) ((ServiceProviderProvider) serviceProvider)
                            .getServiceProviders(ipInfo.getDependencyToServiceInfo(), true, false);
                    if (!serviceProviders.isEmpty()) {
                        serviceProvider = serviceProviders.get(0);
                    }
                }

                if (ipInfo.isProviderWrapped()) {
                    return ipInfo.isOptionalWrapped() ? Optional.of(serviceProvider) : serviceProvider;
                }

                if (ipInfo.isOptionalWrapped()) {
                    Optional<?> optVal;
                    try {
                        Object val = serviceProvider.get(ipInfo, ipInfo.getDependencyToServiceInfo(), false);
                        optVal = Optional.ofNullable(val);
                    } catch (InjectionException e) {
                        LOGGER.log(System.Logger.Level.WARNING, e.getMessage(), e);
                        optVal = Optional.empty();
                    }

                    return optVal;
                }

                return serviceProvider.get(ipInfo, ipInfo.getDependencyToServiceInfo(), true);
            }
        } catch (InjectionException ie) {
            throw ie;
        } catch (Throwable t) {
            throw new InjectionException("failed while resolving " + ipInfo, t, null);
        }

        throw new InjectionException("Unsupported injection point types for "
                + getServiceTypeName(ipInfo) + "." + ipInfo.getElementName());
    }

    protected static String getServiceTypeName(DefaultInjectionPointInfo ipInfo) {
        return ipInfo.getServiceTypeName();
    }

    protected static <V> List<V> toEligibleInjectionRefs(DefaultInjectionPointInfo ipInfo,
                                                         List<ServiceProvider<V>> list,
                                                         boolean expected) {
        List<V> result = new ArrayList<>();

        String tag = ipInfo.getElementName();

        for (ServiceProvider<V> sp : list) {
            List<V> instances = sp.getList(ipInfo, ipInfo.getDependencyToServiceInfo(), expected);
            if (Objects.nonNull(instances)) {
                result.addAll(instances);
            }
        }

        if (expected && result.isEmpty()) {
            throw new InjectionException("expected to resolve a service instance appropriate for '"
                    + getServiceTypeName(ipInfo) + "." + tag + "' with criteria = '" + ipInfo.getDependencyToServiceInfo()
                                                 + "' but instead received null from these possible providers: " + list);
        }

        return result;
    }

}
