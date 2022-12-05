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

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.common.Weighted;
import io.helidon.pico.ActivationLog;
import io.helidon.pico.ActivationPhase;
import io.helidon.pico.ActivationResult;
import io.helidon.pico.ActivationStatus;
import io.helidon.pico.Activator;
import io.helidon.pico.DeActivator;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.EventReceiver;
import io.helidon.pico.InjectionException;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.InjectionPointProvider;
import io.helidon.pico.PicoServiceProviderException;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.PostConstructMethod;
import io.helidon.pico.PreDestroyMethod;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceInjectionPlanBinder;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.Services;
import io.helidon.pico.spi.InjectionResolver;
import io.helidon.pico.spi.Resetable;

import jakarta.inject.Provider;

/**
 * Provides the basics for regular Singleton, ApplicationScope, Provider, and ServiceProvider based managed services.
 *
 * @param <T> the type of the service this provider manages
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class AbstractServiceProvider<T>
        implements ServiceProviderBindable<T>,
                   Activator<T>,
                   DeActivator<T>,
                   EventReceiver,
                   Resetable {
    private static final System.Logger LOGGER = System.getLogger(AbstractServiceProvider.class.getName());

//    @JsonIgnore
    private final AtomicReference<T> serviceRef = new AtomicReference<>();
//    @JsonIgnore
    private final Semaphore activationSemaphore = new Semaphore(1);
//    @JsonIgnore
    private long lastActivationThreadId;
//    @JsonIgnore
    private PicoServices picoServices;
//    @JsonIgnore
    private ActivationPhase phase;
//    @JsonIgnore
    private DefaultServiceInfo serviceInfo;
//    @JsonIgnore
    private io.helidon.pico.spi.ext.Dependencies dependencies;
//    @JsonIgnore
    private Map<String, io.helidon.pico.spi.ext.InjectionPlan<Object>> injectionPlan;
//    @JsonIgnore
    private ServiceProvider<?> interceptor;

    /**
     * The default constructor.
     */
    public AbstractServiceProvider() {
        this.phase = ActivationPhase.INIT;
    }

    /**
     * Constructor.
     *
     * @param instance     the managed service instance
     * @param phase        the current phase
     * @param serviceInfo  the service info
     * @param picoServices the pico services instance
     */
    public AbstractServiceProvider(T instance,
                                   ActivationPhase phase,
                                   ServiceInfo serviceInfo,
                                   PicoServices picoServices) {
        this();

        if (Objects.nonNull(instance)) {
            this.serviceRef.set(instance);
            this.phase = Objects.nonNull(phase) ? phase : ActivationPhase.ACTIVE;
        }
        this.serviceInfo = DefaultServiceInfo.toServiceInfo(instance, serviceInfo);
        //        this.lastActivationThreadId = Thread.currentThread().getId();
        this.picoServices = picoServices;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(getName());
    }

    @Override
    public boolean equals(Object another) {
        return (another instanceof ServiceProvider)
                && getServiceInfo().equals(((ServiceProvider) another).getServiceInfo());
    }

    @Override
    public String toString() {
        ServiceInfo serviceInfo = getServiceInfo();
        if (Objects.isNull(serviceInfo)) {
            return toIdentityString() + ":" + getCurrentActivationPhase();
        }
        return toIdentityString() + ":" + getCurrentActivationPhase() + ":" + serviceInfo.getContractsImplemented();
    }

    public T getServiceRef() {
        return serviceRef.get();
    }

    @Override
    public String toIdentityString() {
        return getIdentityPrefix() + getClassName() + getIdentitySuffix();
    }

    @Override
    public String getDescription() {
        return toIdentityString() + ":" +  getName() + ":" + getCurrentActivationPhase();
    }

    @JsonIgnore
    protected String getIdentityPrefix() {
        return "";
    }

    @JsonIgnore
    protected String getIdentitySuffix() {
        return "";
    }

    /**
     * @return The service type name for this provider.
     */
    @JsonIgnore
    public String getName() {
        ServiceInfo serviceInfo = getServiceInfo();
        return Objects.isNull(serviceInfo) ? null : serviceInfo.getServiceTypeName();
    }

    @JsonIgnore
    public String getClassName() {
        return getClass().getSimpleName();
    }

    protected T maybeActivate(InjectionPointInfo ipInfoCtx, boolean expected) {
        try {
            T serviceOrProvider = serviceRef.get();

            if (Objects.isNull(serviceOrProvider) || ActivationPhase.ACTIVE != getCurrentActivationPhase()) {
                AbstractActivationResult<T> activationResult =
                        activate(this, ipInfoCtx, ActivationPhase.ACTIVE, true);
                if (!activationResult.isSuccess()) {
                    if (expected) {
                        throw new InjectionException("get() was not successful: " + activationResult,
                                                     null, this, activationResult.getActivationLog());
                    }
                    return null;
                }

                serviceOrProvider = serviceRef.get();
            }

            if (expected && Objects.isNull(serviceOrProvider)) {
                throwManagedServiceInstanceShouldHaveBeenSetException();
            }

            return serviceOrProvider;
        } catch (Throwable e) {
            throw new PicoServiceProviderException("Unable to activate " + getDescription().trim(), e, this);
        }
    }

    protected void throwManagedServiceInstanceShouldHaveBeenSetException() {
        throw new InjectionException("managed service instance expected to have been set for " + this,
                                     null,
                                     this,
                                     null);
    }

    @Override
    public T get(InjectionPointInfo ipInfoCtx, ServiceInfo criteria, boolean expected) {
        T serviceOrProvider = maybeActivate(ipInfoCtx, expected);

        try {
            if (isProvider()) {
                Object instance;

                if (serviceOrProvider instanceof InjectionPointProvider) {
                    instance = ((InjectionPointProvider<?>) serviceOrProvider).get(ipInfoCtx, criteria, expected);
                } else if (serviceOrProvider instanceof Provider) {
                    instance = ((Provider<?>) serviceOrProvider).get();
                    if (expected && Objects.isNull(instance)) {
                        throw expectedQualifiedServiceError(ipInfoCtx, criteria);
                    }
                } else {
                    instance = NonSingletonServiceProvider.createAndActivate(this, ipInfoCtx);
                }

                return (T) instance;
            }
        } catch (InjectionException ie) {
            throw ie;
        } catch (Throwable t) {
            throw new InjectionException("get() failed for: " + ipInfoCtx, t, this, null);
        }

        return serviceOrProvider;
    }

    protected InjectionException expectedQualifiedServiceError(InjectionPointInfo ipInfoCtx, ServiceInfo criteria) {
        return new InjectionException("get() was expected to return a non-null instance for: " + ipInfoCtx
                                              + " with criteria matching: " + criteria, null, this, null);
    }

    @Override
    public List<T> getList(InjectionPointInfo ipInfoCtx, ServiceInfo criteria, boolean expected) {
        T serviceProvider = maybeActivate(ipInfoCtx, expected);

        try {
            if (isProvider()) {
                List instances = null;

                if (serviceProvider instanceof InjectionPointProvider) {
                    instances = ((InjectionPointProvider<?>) serviceProvider).getList(ipInfoCtx, criteria, expected);
                } else if (serviceProvider instanceof Provider) {
                    Object instance = ((Provider<?>) serviceProvider).get();
                    if (expected && Objects.isNull(instance)) {
                        throw new InjectionException("get() was expected to return a non-null instance for: " + ipInfoCtx,
                                                     null, this, null);
                    }
                    if (Objects.nonNull(instance)) {
                        if (instance instanceof List) {
                            instances = (List) instance;
                        } else {
                            instances = Collections.singletonList(instance);
                        }
                    }
                } else {
                    Object instance = NonSingletonServiceProvider.createAndActivate(this, ipInfoCtx);
                    instances = Collections.singletonList(instance);
                }

                return (List<T>) instances;
            }
        } catch (InjectionException ie) {
            throw ie;
        } catch (Throwable t) {
            throw new InjectionException("get() failed for: " + ipInfoCtx, t, this, null);
        }

        return Objects.nonNull(serviceProvider) ? Collections.singletonList(serviceProvider) : null;
    }

    protected <T> T get(Map<String, T> deps, String id) {
        T val = Objects.requireNonNull(deps.get(id), "'" + id + "' expected to have been found in: " + deps.keySet());
        //        return (T) InjectionPlan.getNotSelf(this, val);
        return val;
    }

    /**
     * Identifies whether the implementation was code generated, or user-supplied.
     *
     * @return true if user-supplied implementation
     */
    @JsonIgnore
    public boolean isCustom() {
        return false;
    }

    @JsonIgnore
    @Override
    public Activator<T> getActivator() {
        return this;
    }

    @JsonIgnore
    @Override
    public DeActivator<T> getDeActivator() {
        return this;
    }

    @JsonIgnore
    @Override
    public ServiceProviderBindable<T> getServiceProviderBindable() {
        return this;
    }

    @JsonIgnore
    @Override
    public boolean isSingletonScope() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isProvider() {
        return false;
    }

    @Override
    public DefaultServiceInfo getServiceInfo() {
        return serviceInfo;
    }

    protected void setServiceInfo(DefaultServiceInfo serviceInfo) {
        this.serviceInfo = serviceInfo;
    }

    protected void overrideServiceInfo(DefaultServiceInfo serviceInfo) {
        assert (Objects.isNull(picoServices));
        this.serviceInfo = Objects.requireNonNull(serviceInfo);
    }

    @JsonIgnore
    public io.helidon.pico.spi.ext.Dependencies getDependencies() {
        if (Objects.isNull(dependencies)
                || (Objects.isNull(dependencies.getForServiceTypeName()) && dependencies.getDependencies().isEmpty())) {
            return null;
        }
        return dependencies;
    }

    protected io.helidon.pico.spi.ext.Dependencies setDependencies(io.helidon.pico.spi.ext.Dependencies dependencies) {
        assert (Objects.isNull(this.dependencies) || this.dependencies == dependencies
                        || this.dependencies.equals(dependencies));
        this.dependencies = Objects.nonNull(dependencies) ? dependencies : io.helidon.pico.spi.ext.Dependencies.builder().build();
        return this.dependencies;
    }

    @JsonIgnore
    @Override
    public double weight() {
        Double weight = getServiceInfo().getWeight();
        if (Objects.isNull(weight)) {
            return Weighted.DEFAULT_WEIGHT;
        }
        return weight;
    }

    @JsonIgnore
    @Override
    public ActivationPhase getCurrentActivationPhase() {
        return phase;
    }

    protected ActivationPhase setPhase(ActivationResult settableResult, ActivationPhase phase) {
        ActivationPhase previous = getCurrentActivationPhase();
        this.phase = phase;
        if (Objects.nonNull(settableResult)) {
            ((DefaultActivationResult) settableResult).setFinishingActivationPhase(phase);
        }
        return previous;
    }

    public PicoServices getPicoServices() {
        return Objects.nonNull(picoServices) ? picoServices : DefaultPicoServices.getInstance();
    }

    /**
     * Set the pico service instance.
     *
     * @param picoServices the service instance
     */
    @Override
    public void setPicoServices(PicoServices picoServices) {
        if (Objects.nonNull(this.picoServices) || Objects.nonNull(serviceRef.get())) {
            PicoServices current = this.picoServices;
            if (picoServices == current) {
                return;
            }

            if (Objects.nonNull(current)) {
                if ((picoServices instanceof DefaultPicoServices) && ((DefaultPicoServices) picoServices).isDynamic()) {
                    hardReset();
                } else {
                    throw new InjectionException(toIdentityString()
                                                         + ": is already bound to a different services registry: " + current, null, this);
                }
            }
        }
        this.picoServices = picoServices;
    }

    @Override
    public void setModuleName(String moduleName) {
        final DefaultServiceInfo serviceInfo = getServiceInfo();
        final String moduleInfoName = serviceInfo.getModuleName();
        if (!DefaultServiceInfo.equals(moduleInfoName, moduleName)) {
            if (Objects.nonNull(moduleInfoName)) {
                throw new InjectionException("service provider already bound to " + moduleInfoName, null, this);
            }
            //            setServiceInfo(serviceInfo.toBuilder().moduleName(moduleName).build());
            serviceInfo.setModuleName(moduleName);
        }
    }

    @Override
    public ServiceProvider<?> getInterceptor() {
        return interceptor;
    }

    @Override
    public void setInterceptor(ServiceProvider<?> interceptor) {
        if (interceptor != this.interceptor) {
            assert (Objects.isNull(this.interceptor) && Objects.nonNull(interceptor) && (interceptor != this));
            this.interceptor = interceptor;
        }
    }

    @JsonIgnore
    public boolean isActive() {
        return isAlreadyAtTargetPhase(ActivationPhase.ACTIVE);
    }

    @JsonIgnore
    protected boolean isAlreadyAtTargetPhase(ActivationPhase ultimateTargetPhase) {
        return getCurrentActivationPhase().equals(Objects.requireNonNull(ultimateTargetPhase));
    }

    @Override
    public AbstractActivationResult<T> activate(ServiceProvider<T> targetServiceProvider,
                                                InjectionPointInfo ctx,
                                                ActivationPhase ultimateTargetPhase,
                                                boolean throwOnError) {
        final AbstractActivationResult<T> result = preambleActivate(targetServiceProvider,
                                                                    ctx, ultimateTargetPhase, throwOnError);
        if (result.isFinished()) {
            return result;
        }

        final DefaultActivationResult settableResult = (DefaultActivationResult) result;
        // if we get here then we own the semaphore for activation...
        try {
            if (ActivationPhase.INIT == result.getFinishingActivationPhase()
                    || ActivationPhase.PENDING == result.getFinishingActivationPhase()
                    || ActivationPhase.DESTROYED == result.getFinishingActivationPhase()) {
                doActivationStarting(settableResult, ActivationPhase.ACTIVATION_STARTING, false);
            }
            if (ActivationPhase.ACTIVATION_STARTING == result.getFinishingActivationPhase()) {
                doGatheringDependencies(settableResult, ActivationPhase.GATHERING_DEPENDENCIES);
            }
            if (ActivationPhase.GATHERING_DEPENDENCIES == result.getFinishingActivationPhase()) {
                doConstructing(settableResult, ActivationPhase.CONSTRUCTING);
            }
            if (ActivationPhase.CONSTRUCTING == result.getFinishingActivationPhase()) {
                doInjecting(settableResult, ActivationPhase.INJECTING);
            }
            if (ActivationPhase.INJECTING == result.getFinishingActivationPhase()) {
                doPostConstructing(settableResult, ActivationPhase.POST_CONSTRUCTING);
            }
            if (ActivationPhase.POST_CONSTRUCTING == result.getFinishingActivationPhase()) {
                doActivationFinishing(settableResult, ActivationPhase.ACTIVATION_FINISHING, false);
            }
            if (ActivationPhase.ACTIVATION_FINISHING == result.getFinishingActivationPhase()) {
                doActivationActive(settableResult, ActivationPhase.ACTIVE);
            }
            onFinished(settableResult, settableResult.getFinishingActivationPhase());
        } catch (Throwable t) {
            return failedFinish(settableResult, t, throwOnError);
        } finally {
            this.lastActivationThreadId = 0;
            settableResult.setFinished(true);
            activationSemaphore.release();
        }

        return result;
    }

    protected void onFinished(ActivationResult activationResult, ActivationPhase finishingActivationPhase) {
        DefaultActivationResult settableResult = (DefaultActivationResult) Objects.requireNonNull(activationResult);
        if (settableResult.getFinishingActivationPhase() != finishingActivationPhase) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Short circuiting activation to phase '"
                        + finishingActivationPhase + "' for " + this);
            }
            setPhase(activationResult, finishingActivationPhase);
        }
        settableResult.setFinishingStatus(ActivationStatus.SUCCESS);
    }

    protected AbstractActivationResult<T> preambleActivate(ServiceProvider<T> targetServiceProvider,
                                                           InjectionPointInfo ctx,
                                                           ActivationPhase ultimateTargetPhase,
                                                           boolean throwOnError) {
        assert (targetServiceProvider == this) : "not capable of handling service provider " + targetServiceProvider;
        if (isAlreadyAtTargetPhase(ultimateTargetPhase)) {
            return DefaultActivationResultBuilder.SIMPLE_SUCCESS;
        }

        final PicoServices picoServices = getPicoServices();
        final Optional<ActivationLog> log = picoServices.getActivationLog().isPresent()
                ? Optional.of(new CompositeActivationLog(picoServices.getActivationLog().get(),
                                                         DefaultActivationLog.privateCapturedAndRetainedLog(
                                                                 DefaultActivationLog.NAME_LOCAL,
                                                                 null)))
                : Optional.empty();

        // if we are here then we are not yet at the ultimate target phase, and we either have to activate or deactivate
        DefaultActivationLog.Entry entry = toLogEntry(ActivationLog.Event.STARTING, ultimateTargetPhase);
        entry.setFinishingActivationPhase(ActivationPhase.PENDING);
        if (log.isPresent()) {
            entry = (DefaultActivationLog.Entry) log.get().recordActivationEvent(entry);
        }

        final Services services = picoServices.getServices().getContextualServices(ctx);

        final DefaultActivationResult<T> result = createResultPlaceholder(services, log, ultimateTargetPhase);

        // fail fast if we are in a recursive situation on this thread...
        if (entry.getThreadId().equals(lastActivationThreadId)) {
            return failedFinish(result, recursiveActivationInjectionError(entry, log), throwOnError);
        }

        final Optional<? extends PicoServicesConfig> optConfig = picoServices.getConfig();
        assert (optConfig.isPresent());
        final PicoServicesConfig config = optConfig.get();
        boolean didAcquire = false;
        try {
            // let's wait a bit on the semaphore until we read timeout (probably detecting a deadlock situation)...
            if (!activationSemaphore.tryAcquire(getDeadlockTimeoutInMillis(config), TimeUnit.MILLISECONDS)) {
                // if we couldn't grab the semaphore than we (or someone else) is busy activating this services, or
                // we deadlocked.
                return failedFinish(result, timedOutActivationInjectionError(entry, log), throwOnError);
            }
            didAcquire = true;

            // if we made it to here then we "own" the semaphore and the subsequent activation steps...
            this.lastActivationThreadId = Thread.currentThread().getId();

            if (result.isFinished()) {
                didAcquire = false;
                activationSemaphore.release();
            }

            setPhase(result, ActivationPhase.PENDING);
        } catch (Throwable t) {
            this.lastActivationThreadId = 0;

            if (didAcquire) {
                activationSemaphore.release();
            }

            return failedFinish(result, interruptedPreActivationInjectionError(entry, log, t), throwOnError);
        }

        return result;
    }

    protected DefaultActivationResult<T> createResultPlaceholder(Services services,
                                                                 Optional<ActivationLog> log,
                                                                 ActivationPhase ultimateTargetPhase) {
        return new DefaultActivationResultBuilder()
                .serviceProvider(this)
                .services(services)
                .activationLog(log)
                .startingActivationPhase(getCurrentActivationPhase())
                .finishingActivationPhase(getCurrentActivationPhase())
                .ultimateTargetActivationPhase(ultimateTargetPhase)
                .build();
    }

    protected void recordActivationEvent(ActivationLog.Event event,
                                         ActivationPhase phase,
                                         ActivationResult settableResult) {
        ActivationPhase previous = setPhase(settableResult, phase);
        ActivationPhase ultimate = settableResult.getUltimateTargetActivationPhase();
        DefaultActivationLog.Entry entry = toLogEntry(event, ultimate);
        entry.setStartingActivationPhase(previous);
        entry.setFinishingActivationPhase(phase);
        Optional<ActivationLog> log = settableResult.getActivationLog();
        log.ifPresent(activationLog -> activationLog.recordActivationEvent(entry));
    }

    protected void doActivationStarting(DefaultActivationResult settableResult,
                                        ActivationPhase phase,
                                        boolean didSomething) {
        if (didSomething) {
            recordActivationEvent(ActivationLog.Event.FINISHED, phase, settableResult);
        } else {
            setPhase(settableResult, phase);
        }
    }

    /**
     * Called at startup to establish the injection plan as an alternative to gathering it dynamically.
     */
    @Override
    public ServiceInjectionPlanBinder.Binder toInjectionPlanBinder() {
        if (Objects.isNull(dependencies)) {
            setDependencies(getDependencies());

            if (Objects.isNull(dependencies)) {
                // couldn't accept our suggested dependencies...
                return null;
            }
        }

        if (Objects.nonNull(injectionPlan)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "this service provider already has an injection plan (which is unusual here): " + this);
        }

        final ConcurrentHashMap<String, DefaultInjectionPointInfo> idToIpInfo = new ConcurrentHashMap<>();
        dependencies.getDependencies().forEach(dep -> {
            dep.getIpDependencies().forEach(ipDep -> {
                String id = ipDep.getIdentity();
                DefaultInjectionPointInfo prev = idToIpInfo.put(id, ipDep);
                if (Objects.nonNull(prev)) {
                    if (!prev.equals(ipDep)
                            && !prev.getDependencyToServiceInfo().equals(ipDep.getDependencyToServiceInfo())) {
                        logMultiDefInjectionNote(this, id, prev, ipDep, idToIpInfo);
                    }
                }
            });
        });

        final ConcurrentHashMap<String, io.helidon.pico.spi.ext.InjectionPlan<Object>> injectionPlan = new ConcurrentHashMap<>();
        final AbstractServiceProvider<T> self = AbstractServiceProvider.this;
        return new ServiceInjectionPlanBinder.Binder() {
            private DefaultInjectionPointInfo ipInfo;

            private ServiceProvider<Object> bind(ServiceProvider rawSp) {
                assert (!(rawSp instanceof BoundedServiceProvider)) : rawSp;
                return BoundedServiceProvider.create(rawSp, ipInfo);
            }

            private List<ServiceProvider<Object>> bind(List<ServiceProvider> rawList) {
                return rawList.stream().map(this::bind).collect(Collectors.toList());
            }

            @Override
            public ServiceInjectionPlanBinder.Binder bind(String ipIdentity, ServiceProvider serviceProvider) {
                io.helidon.pico.spi.ext.InjectionPlan plan = createBuilder(ipIdentity)
                        .ipQualifiedServiceProviders(Collections.singletonList(bind(serviceProvider)))
                        .build();
                Object prev = injectionPlan.put(ipIdentity, plan);
                assert (Objects.isNull(prev));
                return this;
            }

            @Override
            public ServiceInjectionPlanBinder.Binder bindMany(String ipIdentity, ServiceProvider... serviceProviders) {
                io.helidon.pico.spi.ext.InjectionPlan plan = createBuilder(ipIdentity)
                        .ipQualifiedServiceProviders(bind(Arrays.asList(serviceProviders)))
                        .build();
                Object prev = injectionPlan.put(ipIdentity, plan);
                assert (Objects.isNull(prev));
                return this;
            }

            @Override
            public ServiceInjectionPlanBinder.Binder bindVoid(String ipIdentity) {
                return bind(ipIdentity, VoidServiceProvider.INSTANCE);
            }

            @Override
            public ServiceInjectionPlanBinder.Binder resolvedBind(String ipIdentity, Class<?> serviceType) {
                InjectionResolver resolver = (InjectionResolver) AbstractServiceProvider.this;
                ServiceInfo serviceInfo = DefaultServiceInfo
                        .toServiceInfoFromClass(serviceType, null);
                DefaultInjectionPointInfo ipInfo = DefaultInjectionPointInfo.create(ipIdentity, serviceInfo);
                Object resolved = Objects.requireNonNull(
                        resolver.resolve(ipInfo, getPicoServices(), AbstractServiceProvider.this, false));
                io.helidon.pico.spi.ext.InjectionPlan plan = createBuilder(ipIdentity)
                        .ipUnqualifiedServiceProviderResolutions(List.of(resolved))
                        .resolved(false)
                        .build();
                Object prev = injectionPlan.put(ipIdentity, plan);
                assert (Objects.isNull(prev));
                return this;
            }

            @Override
            public void commit() {
                if (!idToIpInfo.isEmpty()) {
                    throw new InjectionException("missing injection bindings for "
                                                         + idToIpInfo + " in " + getDescription(), null, self);
                }

                if (Objects.nonNull(self.injectionPlan) && !self.injectionPlan.equals(injectionPlan)) {
                    throw new InjectionException("injection plan has already been bound for "
                                                         + getDescription(), null, self);
                }
                self.injectionPlan = injectionPlan;
            }

            private DefaultInjectionPointInfo safeGetIpInfo(String ipIdentity) {
                DefaultInjectionPointInfo ipInfo = idToIpInfo.remove(ipIdentity);
                if (Objects.isNull(ipInfo)) {
                    throw new InjectionException("expected to find a dependency for '" + ipIdentity + "' from "
                                                         + getDescription() + " in " + idToIpInfo, null, self);
                }
                return ipInfo;
            }

            private io.helidon.pico.spi.ext.InjectionPlan.InjectionPlanBuilder<Object> createBuilder(String ipIdentity) {
                ipInfo = safeGetIpInfo(ipIdentity);
                return io.helidon.pico.spi.ext.InjectionPlan.builder()
                        .identity(ipIdentity)
                        .ipInfo(ipInfo)
                        .selfServiceProvider(self);
            }
        };
    }

    protected void doGatheringDependencies(DefaultActivationResult settableResult, ActivationPhase phase) {
        recordActivationEvent(ActivationLog.Event.STARTING, phase, settableResult);

        Map<String, io.helidon.pico.spi.ext.InjectionPlan<Object>> plans = getOrCreateInjectionPlan(false);
        if (Objects.nonNull(plans)) {
            settableResult.setInjectionPlans(plans);
        }

        Map<String, Object> deps = resolveDependencies(plans);
        if (Objects.nonNull(deps)) {
            settableResult.setDeps(deps);
        }

        recordActivationEvent(ActivationLog.Event.FINISHED, phase, settableResult);
    }

    /**
     * Create the injection plan.
     *
     * @param resolveIps true if the injection points should also be activated/resolved.
     * @return the injection plan
     */
    public Map<String, io.helidon.pico.spi.ext.InjectionPlan<Object>> getOrCreateInjectionPlan(boolean resolveIps) {
        if (Objects.nonNull(injectionPlan)) {
            return injectionPlan;
        }

        if (Objects.isNull(dependencies)) {
            setDependencies(getDependencies());
        }

        final Map<String, io.helidon.pico.spi.ext.InjectionPlan<Object>> plan =
                io.helidon.pico.spi.ext.InjectionPlan.toInjectionPlans(getPicoServices(), dependencies, resolveIps, this);
        assert (Objects.isNull(injectionPlan));
        injectionPlan = plan;

        return Objects.requireNonNull(injectionPlan);
    }

    public void hardReset() {
        Object service = serviceRef.get();
        if (Objects.nonNull(service)) {
            LOGGER.log(System.Logger.Level.INFO, "resetting " + this);
        }

        if (service instanceof Resetable) {
            try {
                ((Resetable) service).reset();
            } catch (Throwable t) {
                LOGGER.log(System.Logger.Level.WARNING, "unable to reset: " + this, t);
            }
        }
        injectionPlan = null;
        interceptor = null;
        picoServices = null;
        setServiceRef(null);
        setPhase(null, ActivationPhase.INIT);
    }

    protected Map<String, Object> resolveDependencies(Map<String, io.helidon.pico.spi.ext.InjectionPlan<Object>> plans) {
        Map<String, Object> result = new LinkedHashMap<>();

        plans.forEach((key, value) -> {
            if (value.isWasResolved()) {
                result.put(key, value.getResolved());
            } else {
                List<ServiceProvider<Object>> serviceProviders = value.getIpQualifiedServiceProviders();
                serviceProviders = (Objects.isNull(serviceProviders))
                        ? Collections.emptyList()
                        : Collections.unmodifiableList(serviceProviders);
                Object resolved;
                if (serviceProviders.isEmpty()
                        && Objects.nonNull(value.getIpUnqualifiedServiceProviderResolutions())
                        && !value.getIpUnqualifiedServiceProviderResolutions().isEmpty()) {
                    resolved = Collections.emptyList(); // deferred...
                } else {
                    resolved = io.helidon.pico.spi.ext.InjectionPlan.resolve(picoServices, this, value.getIpInfo(), serviceProviders);
                }
                result.put(key, resolved);
            }
        });

        return result;
    }

    protected void doConstructing(ActivationResult activationResult, ActivationPhase phase) {
        DefaultActivationResult settableResult = (DefaultActivationResult) activationResult;

        recordActivationEvent(ActivationLog.Event.STARTING, phase, settableResult);

        Map<String, Object> deps = settableResult.getDeps();
        setServiceRef(createServiceProvider(deps));

        recordActivationEvent(ActivationLog.Event.FINISHED, getCurrentActivationPhase(), settableResult);
    }

    protected void setServiceRef(T instance) {
        serviceRef.set(instance);
    }

    protected T createServiceProvider(Map<String, Object> deps) {
        throw new InjectionException("don't know how to create an instance of " + getServiceInfo(), null, this);
    }

    protected void doInjecting(DefaultActivationResult settableResult, ActivationPhase phase) {
        Map<String, Object> deps = settableResult.getDeps();
        if (Objects.isNull(deps) || deps.isEmpty()) {
            recordActivationEvent(ActivationLog.Event.FINISHED, phase, settableResult);
        } else {
            recordActivationEvent(ActivationLog.Event.STARTING, phase, settableResult);

            T target = Objects.requireNonNull(serviceRef.get());
            List<String> serviceTypeOrdering = Objects.requireNonNull(getServiceTypeInjectionOrder());
            LinkedHashSet<String> injections = new LinkedHashSet<>();
            serviceTypeOrdering.forEach((forServiceType) -> {
                try {
                    doInjectingFields(target, deps, injections, forServiceType);
                    doInjectingMethods(target, deps, injections, forServiceType);
                } catch (Throwable t) {
                    throw new InjectionException("failed to activate/inject: " + getName()
                                                         + "; dependency map was: " + deps, t, this);
                }
            });

            recordActivationEvent(ActivationLog.Event.FINISHED, phase, settableResult);
        }
    }

    protected List<String> getServiceTypeInjectionOrder() {
        return Collections.singletonList(getName());
    }

    protected void doInjectingFields(Object target,
                                     Map<String, Object> deps,
                                     Set<String> injections,
                                     String forServiceType) {
        // NOP; meant to be overridden
        boolean debugMe = true;
    }

    protected void doInjectingMethods(Object target,
                                      Map<String, Object> deps,
                                      Set<String> injections,
                                      String forServiceType) {
        // NOP; meant to be overridden
        boolean debugMe = true;
    }

    protected void doPostConstructing(DefaultActivationResult settableResult, ActivationPhase phase) {
        PostConstructMethod postConstruct = getPostConstructMethod();
        if (Objects.nonNull(postConstruct)) {
            recordActivationEvent(ActivationLog.Event.STARTING, phase, settableResult);
            postConstruct.postConstruct();
            recordActivationEvent(ActivationLog.Event.FINISHED, phase, settableResult);
        } else {
            setPhase(settableResult, phase);
        }
    }

    protected void doActivationFinishing(DefaultActivationResult settableResult,
                                         ActivationPhase phase,
                                         boolean didSomething) {
        if (didSomething) {
            recordActivationEvent(ActivationLog.Event.FINISHED, phase, settableResult);
        } else {
            setPhase(settableResult, phase);
        }
    }

    protected void doActivationActive(DefaultActivationResult settableResult, ActivationPhase phase) {
        recordActivationEvent(ActivationLog.Event.FINISHED, phase, settableResult);
    }

    @Override
    public ActivationResult<T> deactivate(ServiceProvider<T> targetServiceProvider, boolean throwOnError) {
        assert (targetServiceProvider == this) : "not capable of handling service provider " + targetServiceProvider;
        if (isAlreadyAtTargetPhase(ActivationPhase.INIT) || isAlreadyAtTargetPhase(ActivationPhase.DESTROYED)) {
            return DefaultActivationResultBuilder.SIMPLE_SUCCESS;
        }

        final PicoServices picoServices = getPicoServices();
        final Optional<ActivationLog> log = picoServices.getActivationLog().isPresent()
                ? Optional.of(new CompositeActivationLog(picoServices.getActivationLog().get(),
                                                         DefaultActivationLog.privateCapturedAndRetainedLog(
                                                                 DefaultActivationLog.NAME_LOCAL,
                                                                 null)))
                : Optional.empty();
        final Services services = picoServices.getServices();

        // if we are here then we are not yet at the ultimate target phase, and we either have to activate or
        // deactivate...
        DefaultActivationLog.Entry entry = toLogEntry(ActivationLog.Event.STARTING, ActivationPhase.DESTROYED);
        entry.setFinishingActivationPhase(ActivationPhase.PRE_DESTROYING);
        if (log.isPresent()) {
            entry = (DefaultActivationLog.Entry) log.get().recordActivationEvent(entry);
        }

        final PicoServicesConfig config = picoServices.getConfig().get();
        boolean didAcquire = false;
        final DefaultActivationResult<T> result = new DefaultActivationResultBuilder()
                .serviceProvider(this)
                .activationLog(log)
                .services(services)
                .finishingStatus(ActivationStatus.SUCCESS)
                .startingActivationPhase(getCurrentActivationPhase())
                .finishingActivationPhase(ActivationPhase.PRE_DESTROYING)
                .ultimateTargetActivationPhase(ActivationPhase.DESTROYED)
                .build();
        try {
            // let's wait a bit on the semaphore until we read timeout (probably detecting a deadlock situation)...
            if (!activationSemaphore.tryAcquire(getDeadlockTimeoutInMillis(config), TimeUnit.MILLISECONDS)) {
                // if we couldn't grab the semaphore than we (or someone else) is busy activating this services, or
                // we deadlocked.
                return failedFinish(result, timedOutDeActivationInjectionError(entry, log), throwOnError);
            }
            didAcquire = true;

            if (!isAlreadyAtTargetPhase(ActivationPhase.ACTIVE)) {
                return result;
            }

            // if we made it to here then we "own" the semaphore and the subsequent activation steps...
            this.lastActivationThreadId = Thread.currentThread().getId();

            if (!result.isFinished()) {
                setPhase(result, ActivationPhase.PRE_DESTROYING);
                doPreDestroying(result);

                setServiceRef(null);

                result.setDeps(null);
            }
        } catch (Throwable e) {
            return failedFinish(result, interruptedPreActivationInjectionError(entry, log, e), throwOnError);
        } finally {
            lastActivationThreadId = 0;
            result.setFinished(true);
            if (didAcquire) {
                activationSemaphore.release();
            }
        }

        return result;
    }

    protected void doPreDestroying(DefaultActivationResult<T> result) {
        PreDestroyMethod preDestroyMethod = getPreDestroyMethod();
        if (Objects.isNull(preDestroyMethod)) {
            recordActivationEvent(ActivationLog.Event.FINISHED, ActivationPhase.DESTROYED, result);
        } else {
            recordActivationEvent(ActivationLog.Event.STARTING, ActivationPhase.DESTROYED, result);
            preDestroyMethod.preDestroy();
            recordActivationEvent(ActivationLog.Event.FINISHED, ActivationPhase.DESTROYED, result);
        }
    }

    @JsonIgnore
    @Override
    public PostConstructMethod getPostConstructMethod() {
        return null;
    }

    @JsonIgnore
    @Override
    public PreDestroyMethod getPreDestroyMethod() {
        return null;
    }

    protected DefaultActivationResult failedFinish(DefaultActivationResult<T> result,
                                                   Throwable t,
                                                   boolean throwOnError) {
        if (Objects.isNull(result)) {
            result = new DefaultActivationResult<>();
            result.setServiceProvider(this);
        }

        this.lastActivationThreadId = 0;

        return finishFailedFinish(result, t, throwOnError);
    }

    /**
     * Failed to finish.
     *
     * @param t            thr throwable
     * @param throwOnError true if should throw on error, else returns the result containing the throwable error
     * @return the result (assuming that the throwable was not thrown)
     */
    public static DefaultActivationResult failedFinish(Throwable t, boolean throwOnError) {
        return finishFailedFinish(new DefaultActivationResult(), t, throwOnError);
    }

    private static DefaultActivationResult finishFailedFinish(DefaultActivationResult result,
                                                              Throwable t,
                                                              boolean throwOnError) {
        InjectionException e;

        Throwable prev = result.getError();
        if (Objects.isNull(prev) || !(t instanceof InjectionException)) {
            String msg = (Objects.nonNull(t) && Objects.nonNull(t.getMessage()))
                    ? t.getMessage() : "failed to complete operation";
            e = new InjectionException(msg, t, result.getServiceProvider(), result.getActivationLog());
        } else {
            e = (InjectionException) t;
        }

        result.setError(e);
        result.setFinishingStatus(ActivationStatus.FAILURE);
        result.setFinished(true);

        if (throwOnError) {
            throw e;
        }

        return result;
    }

    protected static InjectionException recursiveActivationInjectionError(ActivationLog.ActivationEntry entry,
                                                                          Optional<ActivationLog> log) {
        ServiceProvider<?> targetServiceProvider = entry.getServiceProvider();
        InjectionException e = new InjectionException("circular dependency found during activation of "
                                                              + targetServiceProvider,
                                                      null,
                                                      targetServiceProvider,
                                                      log);
        entry.setActivationException(e);
        return e;
    }

    protected static InjectionException timedOutActivationInjectionError(ActivationLog.ActivationEntry entry,
                                                                         Optional<ActivationLog> log) {
        ServiceProvider<?> targetServiceProvider = entry.getServiceProvider();
        InjectionException e = new InjectionException("timed out during activation of "
                                                              + targetServiceProvider,
                                                      null,
                                                      targetServiceProvider,
                                                      log);
        entry.setActivationException(e);
        return e;
    }

    protected static InjectionException timedOutDeActivationInjectionError(ActivationLog.ActivationEntry entry,
                                                                           Optional<ActivationLog> log) {
        ServiceProvider<?> targetServiceProvider = entry.getServiceProvider();
        InjectionException e = new InjectionException("timed out during deactivation of " + targetServiceProvider,
                                                      null, targetServiceProvider, log);
        entry.setActivationException(e);
        return e;
    }

    protected static InjectionException interruptedPreActivationInjectionError(ActivationLog.ActivationEntry entry,
                                                                               Optional<ActivationLog> log,
                                                                               Throwable cause) {
        ServiceProvider<?> targetServiceProvider = entry.getServiceProvider();
        InjectionException e =
                new InjectionException("circular dependency found during activation of " + targetServiceProvider,
                                       cause, targetServiceProvider, log);
        entry.setActivationException(e);
        return e;
    }

    protected void logMultiDefInjectionNote(ServiceProvider<?> sp,
                                                               String id,
                                                               Object prev,
                                                               DefaultInjectionPointInfo ipDep,
                                                               Map<String, DefaultInjectionPointInfo> idToIpInfoDep) {
        LOGGER.log(System.Logger.Level.DEBUG,
                   "there are two different services sharing the same injection point info id; first = "
                        + prev + " and the second = " + ipDep + "; both use the id '" + id
                        + "'; note that the second will override the first");
    }

    protected static long getDeadlockTimeoutInMillis(PicoServicesConfig config) {
        return config.getValue(PicoServicesConfig.KEY_DEADLOCK_TIMEOUT_IN_MILLIS, PicoServicesConfig.defaultValue(0L));
    }

    protected DefaultActivationLog.Entry toLogEntry(ActivationLog.Event event, ActivationPhase ultimateTargetPhase) {
        ActivationPhase phase = getCurrentActivationPhase();
        return DefaultActivationLog.Entry.builder()
                .serviceProvider((ServiceProvider<Object>) this)
                .managedServiceInstance(toReference(serviceRef.get()))
                .event(event)
                .targetActivationPhase(ultimateTargetPhase)
                .startingActivationPhase(phase)
                .finishingActivationPhase(phase)
                .build();
    }

    protected static Object toReference(Object instance) {
        return new WeakReference(instance) {
            @Override
            public String toString() {
                return String.valueOf(get());
            }
        };
    }

}
