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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.pico.ActivationLog;
import io.helidon.pico.ActivationLogQuery;
import io.helidon.pico.ActivationResult;
import io.helidon.pico.ActivationStatus;
import io.helidon.pico.Application;
import io.helidon.pico.DefaultActivationResult;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.EventReceiver;
import io.helidon.pico.InjectionException;
import io.helidon.pico.Injector;
import io.helidon.pico.PicoException;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.RunLevel;
import io.helidon.pico.ServiceBinder;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceInjectionPlanBinder;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.spi.Resetable;

import jakarta.inject.Inject;

/**
 * The default implementation for {@link PicoServices}.
 * Gaining access to this service instance is most commonly achieved by calling
 * {@link io.helidon.pico.PicoServices#picoServices()}.
 * <p>
 * For testing purposes one should look at the {@code pico-testing} module for other usages.
 * @deprecated see {@link io.helidon.pico.PicoServices#picoServices()}
 */
@Deprecated
public class DefaultPicoServices implements PicoServices, Resetable {
    private static final System.Logger LOGGER = System.getLogger(DefaultPicoServices.class.getName());
    private final Context ctx;

    /**
     * The default constructor. This is intended for the global services registry instance, which is resolved via
     * the service loader.
     *
     * @see io.helidon.pico.services.DefaultPicoServicesProvider
     */
    DefaultPicoServices() {
        this(new DefaultPicoServicesConfig(), null, null, true);
    }

    /**
     * Constructor taking a configuration.
     *
     * @param config the config
     */
    protected DefaultPicoServices(PicoServicesConfig config) {
        this(Objects.nonNull(config) ? config : new DefaultPicoServicesConfig(), null, null, false);
    }

    /**
     * Constructor taking a configuration as well as the activation log to record activation events into.
     *
     * @param config       the config
     * @param initServices the initial services registry
     * @param log          the activation log
     * @param global       indicator whether this instance represents the global/root service registry
     */
    public DefaultPicoServices(PicoServicesConfig config,
                               DefaultServices initServices,
                               DefaultActivationLog log,
                               boolean global) {
        this.isGlobal = global;
        if (global) {
            synchronized (INSTANCE) {
                assert (Objects.isNull(INSTANCE.get()))
                        : "expected to only have a singleton instance of " + DefaultPicoServices.class;
                assert (Objects.isNull(INSTANCE.getAndSet(this)))
                        : "expected to only have a singleton instance of " + DefaultPicoServices.class;
            }

            if (Objects.isNull(log)
                    && config.getValue(PicoServicesConfig.KEY_ACTIVATION_LOGS_ENABLED,
                                       PicoServicesConfig.defaultValue(PicoServicesConfig.DEFAULT_ACTIVATION_LOGS_ENABLED))) {
                log = createActivationLog();
            }
        }
        this.config = Objects.nonNull(config) ? config : new io.helidon.pico.spi.impl.DefaultPicoServicesConfig();
        this.services.set(initServices);
        this.activationLog = log;
        this.isDynamic = config.getValue(PicoServicesConfig.KEY_SUPPORTS_DYNAMIC,
                                         PicoServicesConfig.defaultValue(PicoServicesConfig.DEFAULT_SUPPORTS_DYNAMIC));
    }

    /**
     * Returns the global/root instance.
     *
     * @return the global/root instance if set
     */
    public static PicoServices getInstance() {
        DefaultPicoServices picoServices = INSTANCE.get();
        if (Objects.isNull(picoServices)) {
            synchronized (INSTANCE) {
                picoServices = INSTANCE.get();
                if (Objects.isNull(picoServices)) {
                    picoServices = new DefaultPicoServices();
                    INSTANCE.set(picoServices);
                }
            }
        }
        return picoServices;
    }

    /**
     * Returns true iff the target qualifies for injection.
     *
     * @param serviceProvider the service provider of the target.
     * @return true if the target qualifies for injection
     */
    public static boolean isQualifiedInjectionTarget(ServiceProvider<?> serviceProvider) {
        ServiceInfo serviceInfo = serviceProvider.getServiceInfo();
        Set<String> contractsImplemented = serviceInfo.getContractsImplemented();
        if (Objects.nonNull(contractsImplemented) && !contractsImplemented.isEmpty()) {
            return !contractsImplemented.contains(Module.class.getName())
                    && !contractsImplemented.contains(Application.class.getName());
        }
        return true;
    }

    @Override
    public PicoServices services() {
        return null;
    }

    protected static io.helidon.pico.spi.impl.DefaultActivationLog createActivationLog() {
        return io.helidon.pico.spi.impl.DefaultActivationLog.sharedCapturedAndRetainedLog(io.helidon.pico.spi.impl.DefaultActivationLog.NAME_FULL, LOGGER);
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public boolean isDynamic() {
        return isDynamic;
    }

    public boolean isBindModulesEnabled() {
        return config.getValue(io.helidon.pico.spi.impl.DefaultPicoServicesConfig.KEY_BIND_MODULES,
                               PicoServicesConfig.defaultValue(io.helidon.pico.spi.impl.DefaultPicoServicesConfig.DEFAULT_BIND_MODULES));
    }

    public boolean isBindApplicationEnabled() {
        return config.getValue(io.helidon.pico.spi.impl.DefaultPicoServicesConfig.KEY_BIND_APPLICATION,
                               PicoServicesConfig.defaultValue(io.helidon.pico.spi.impl.DefaultPicoServicesConfig.DEFAULT_BIND_APPLICATION));
    }

    @Override
    public Optional<? extends PicoServicesConfig> getConfig() {
        return Optional.ofNullable(config);
    }

    @Override
    public Optional<ActivationLog> getActivationLog() {
        return Optional.ofNullable(activationLog);
    }

    protected void setServices(io.helidon.pico.spi.impl.DefaultServices services) {
        boolean set = this.services.compareAndSet(null, services);
        if (!set) {
            throw new PicoException("services already initialized");
        }
    }

    @Override
    public io.helidon.pico.spi.impl.DefaultServices getServices() {
        if (!isInitializing.getAndSet(true) || Objects.isNull(services.get())) {
            Thread currentThread = Thread.currentThread();
            try {
                if (Objects.nonNull(initializingThread) && !currentThread.equals(initializingThread)) {
                    initializedServices.await();
                }
                if (Objects.nonNull(initializingThread)) {
                    throw new PicoException("already initializing on thread: " + initializingThread
                                                    + "; current thread: " + currentThread);
                }
                initializingThread = currentThread;
                innerGetServices();
                initializingThread = null;
                initializedServices.countDown();
            } catch (Throwable t) {
                if (t instanceof PicoException) {
                    throw (PicoException) t;
                } else {
                    throw new PicoException("Failed to get services: " + t.getMessage(), t);
                }
            } finally {
                initializingThread = null;
            }
        }

        return services.get();
    }

    private synchronized void innerGetServices() {
        io.helidon.pico.spi.impl.DefaultServices serviceRegistry = services.get();
        if (Objects.isNull(serviceRegistry)) {
            // create the global services registry now
            serviceRegistry = createServices();
        }

        if (isGlobal() || isBindModulesEnabled()) {
            // iterate over all modules, binding to each one's set of services, but with NO activations
            List<Module> modules = getModules(true);
            try {
                isBinding.set(true);
                bindModuleServices(serviceRegistry, modules);
            } finally {
                isBinding.set(false);
            }
        }

        if (isGlobal() || isBindApplicationEnabled()) {
            // look for the literal injection plan
            // typically only be one Application in non-testing runtimes,
            // unless the developer "messed up" by combining application modules, or some strange edge case
            List<Application> apps = getApplications(true);
            bindApplication(serviceRegistry, apps);
        }

        // set the services to be used (until reset at least)
        this.services.set(serviceRegistry);
        this.initializingThread = null;

        if (isGlobal() || isBindModulesEnabled()) {
            serviceRegistry.getAllServiceProviders(false).forEach(sp -> {
                if (sp instanceof EventReceiver) {
                    ((EventReceiver) sp).onEvent(EventReceiver.Event.POST_BIND_ALL_MODULES);
                }
            });
        }

        if (isGlobal() || isBindApplicationEnabled() || isBindApplicationEnabled()) {
            serviceRegistry.getAllServiceProviders(false).forEach(sp -> {
                if (sp instanceof EventReceiver) {
                    ((EventReceiver) sp).onEvent(EventReceiver.Event.FINAL_RESOLVE);
                }
            });
        }

        // notify interested service providers of "readiness"...
        serviceRegistry.getAllServiceProviders(false).stream()
                .filter(sp -> sp instanceof EventReceiver)
                .forEach(sp -> ((EventReceiver) sp)
                        .onEvent(EventReceiver.Event.SERVICES_READY));
    }

    @Override
    public Optional<? extends Injector> getInjector() {
        if (Objects.nonNull(injector.get())) {
            return Optional.of(injector.get());
        }

        synchronized (injector) {
            if (Objects.nonNull(injector.get())) {
                return Optional.of(injector.get());
            }

            io.helidon.pico.spi.impl.DefaultInjector injector = createInjector();
            this.injector.set(injector);

            return Optional.of(injector);
        }
    }

    @Override
    public Optional<ServiceBinder> createServiceBinder(Module module) {
        if (!isDynamic()) {
            return Optional.empty();
        }
        return Optional.of(createServices()
                                   .createServiceBinder(this, getServices(), io.helidon.pico.spi.impl.DefaultServices.toModuleName(module)));
    }

    /**
     * A hard reset to boot state (i.e., sets to null all settings, maps, and caches).
     */
    @Override
    public void hardReset() {
        // maybe consider restricting this based upon config
        synchronized (services) {
            io.helidon.pico.spi.impl.DefaultServices svcs = services.get();
            if (Objects.nonNull(svcs)) {
                svcs.hardReset();
            }
            synchronized (moduleList) {
                isBinding.set(false);
                services.set(null);
                initializedServices = new CountDownLatch(1);
                isInitializing.set(false);
                moduleList.set(null);
                applicationList.set(null);
                activationLog = Objects.nonNull(activationLog) ? createActivationLog() : null;
            }
        }
    }

    @Override
    public boolean softReset() {
        return false;
    }

    /**
     * A soft reset to boot state (i.e., clears all settings, maps, and caches).
     */
    @Override
    public void clear() {
        synchronized (services) {
            io.helidon.pico.spi.impl.DefaultServices svcs = services.get();
            if (Objects.nonNull(svcs)) {
                svcs.clear();
            }

            Optional<ActivationLogQuery> query = Objects.nonNull(activationLog)
                    ? activationLog.toQuery()
                    : Optional.empty();
            query.ifPresent(ActivationLogQuery::clear);
        }
    }

    @Override
    public Future<Map<String, ActivationResult<?>>> shutdown() {
        LOGGER.log(System.Logger.Level.INFO, "in shutdown");
        io.helidon.pico.spi.impl.DefaultServices services = getServices();
        io.helidon.pico.spi.impl.DefaultInjector injector = (io.helidon.pico.spi.impl.DefaultInjector) getInjector().get();
        CompletableFuture<Map<String, ActivationResult<?>>> completableFuture = new CompletableFuture<>();
        ConcurrentHashMap<String, ActivationResult<?>> map = new ConcurrentHashMap<>();

        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(false);
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.setName(PicoServicesConfig.NAME + "-shutdown-" + System.currentTimeMillis());
            return thread;
        };

        ExecutorService es = Executors.newSingleThreadExecutor(threadFactory);
        es.submit(() -> {
            try {
                List<ActivationLog.ActivationEntry> log = Objects.isNull(activationLog) ? null : activationLog.getLog();
                if (Objects.nonNull(log)) {
                    log = new ArrayList<>(log);
                    Collections.reverse(log);

                    List<ServiceProvider<Object>> serviceProviders =
                            log.stream()
                                    .map(ActivationLog.ActivationEntry::getServiceProvider)
                                    .filter((sp) -> sp.getCurrentActivationPhase().isEligibleForDeactivation())
                                    .collect(Collectors.toList());

                    // prepare for the shutdown log event sequence
                    activationLog.clear();

                    // shutdown using the reverse chrono ordering in the log for starters...
                    shutdown(map, services, injector, activationLog, serviceProviders);
                }

                // next get all services that are beyond INIT state, and sort by runlevel order, and shut those down
                // too...
                List<ServiceProvider<Object>> serviceProviders = services.lookup(DefaultServiceInfo.builder().build(),
                                                                                 false);
                serviceProviders = serviceProviders.stream()
                        .filter((sp) -> sp.getCurrentActivationPhase().isEligibleForDeactivation())
                        .collect(Collectors.toList());
                serviceProviders.sort((o1, o2) -> {
                    Integer runLevel1 = o1.getServiceInfo().getRunLevel();
                    if (Objects.isNull(runLevel1)) {
                        runLevel1 = RunLevel.NORMAL;
                    }
                    Integer runLevel2 = o2.getServiceInfo().getRunLevel();
                    if (Objects.isNull(runLevel2)) {
                        runLevel2 = RunLevel.NORMAL;
                    }
                    return Integer.compare(runLevel1, runLevel2);
                });
                shutdown(map, services, injector, activationLog, serviceProviders);

                // finally, clear everything...
                clear();

                completableFuture.complete(map);
            } catch (Throwable t) {
                LOGGER.log(System.Logger.Level.ERROR, "failed in shutdown", t);
                completableFuture.completeExceptionally(t);
            } finally {
                es.shutdown();
            }
        });

        return completableFuture;
    }

    protected void shutdown(ConcurrentHashMap<String, ActivationResult<?>> map,
                            io.helidon.pico.spi.impl.DefaultServices services,
                            io.helidon.pico.spi.impl.DefaultInjector injector,
                            io.helidon.pico.spi.impl.DefaultActivationLog activationLog,
                            List<ServiceProvider<Object>> serviceProviders) {
        for (ServiceProvider<Object> sp : serviceProviders) {
            if (!sp.getCurrentActivationPhase().isEligibleForDeactivation()) {
                continue;
            }

            ActivationResult<?> result;
            try {
                result = injector.deactivate(sp, services, activationLog, Injector.Strategy.ANY);
            } catch (Throwable t) {
                result = DefaultActivationResult.builder()
                        .serviceProvider(sp)
                        .finishingStatus(ActivationStatus.FAILURE)
                        .error(t)
                        .build();
                map.put(sp.getServiceInfo().getServiceTypeName(), result);
            }
            map.put(sp.getServiceInfo().getServiceTypeName(), result);
        }
    }

    protected List<Module> getModules(boolean load) {
        if (Objects.nonNull(moduleList.get())) {
            return moduleList.get();
        }

        synchronized (moduleList) {
            if (Objects.nonNull(moduleList.get())) {
                return moduleList.get();
            }

            List<Module> result = new LinkedList<>();
            if (load) {
                ServiceLoader<Module> serviceLoader = ServiceLoader.load(Module.class);
                for (Module module : serviceLoader) {
                    result.add(module);
                }
            }

            if (!isDynamic()) {
                result = Collections.unmodifiableList(result);
                moduleList.set(result);
            }

            return result;
        }
    }

    protected List<Application> getApplications(boolean load) {
        if (Objects.nonNull(applicationList.get())) {
            return applicationList.get();
        }

        synchronized (applicationList) {
            if (Objects.nonNull(applicationList.get())) {
                return applicationList.get();
            }

            List<Application> result = new LinkedList<>();
            if (load) {
                ServiceLoader<Application> serviceLoader = ServiceLoader.load(Application.class);
                for (Application app : serviceLoader) {
                    result.add(app);
                }
            }

            if (!isDynamic) {
                result = Collections.unmodifiableList(result);
                applicationList.set(result);
            }

            return result;
        }
    }

    protected io.helidon.pico.spi.impl.DefaultServices createServices() {
        return new io.helidon.pico.spi.impl.DefaultServices(getConfig().get());
    }

    protected io.helidon.pico.spi.impl.DefaultInjector createInjector() {
        return new io.helidon.pico.spi.impl.DefaultInjector();
    }

    protected void bindModuleServices(io.helidon.pico.spi.impl.DefaultServices serviceRegistry, List<Module> modules) {
        if (!isBindModulesEnabled()) {
            LOGGER.log(System.Logger.Level.DEBUG, "module binding is disabled");
            return;
        }

        if (modules.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "no " + Module.class.getName() + " was found.");
        } else {
            modules.forEach((module) -> serviceRegistry.bind(this, module));
        }
    }

    protected void bindApplication(io.helidon.pico.spi.impl.DefaultServices services, List<Application> apps) {
        if (!isBindApplicationEnabled()) {
            LOGGER.log(System.Logger.Level.DEBUG, "application binding is disabled");
            return;
        }

        if (apps.size() > 1) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "there is typically only 1 application instance; app instances = " + apps);
        } else if (apps.isEmpty()) {
            LOGGER.log(System.Logger.Level.INFO, "no " + Application.class.getName() + " was found.");
            return;
        }

        apps.forEach((app) -> {
            ServiceInjectionPlanBinder injectionPlanBinder = new InjectionPlanBinder(services);
            app.configure(injectionPlanBinder);
        });
    }

    /**
     * Services as the multiplexer from {@link io.helidon.pico.spi.Application} to each constituent
     * {@link io.helidon.pico.spi.ServiceProviderBindable} from our service registry. If the service is not found in
     * our
     * registry we will generate a runtime exception. If the constituent is found, but does not provide binding
     * capabilities
     * then this instance is also used to gracefully and quietly consume the plan via a no-op.
     */
    protected static class InjectionPlanBinder
            implements ServiceInjectionPlanBinder, ServiceInjectionPlanBinder.Binder {

        private final io.helidon.pico.spi.impl.DefaultServices services;

        protected InjectionPlanBinder(io.helidon.pico.spi.impl.DefaultServices services) {
            this.services = services;
        }

        @Override
        public Binder bindTo(ServiceProvider<?> serviceProvider) {
            ServiceProvider<?> sp = services.getServiceProvider(serviceProvider);
            if (Objects.isNull(sp)) {
                throw new InjectionException("expected to find a service in the service registry: "
                                                     + serviceProvider, null, serviceProvider, null);
            }
            ServiceProviderBindable<?> bindable = ServiceProviderBindable.toBindableProvider(sp);
            Binder binder = Objects.nonNull(bindable) ? bindable.toInjectionPlanBinder() : null;
            if (Objects.isNull(binder)) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "this service provider is not capable of being bound to injection points: " + sp);
                return this;
            }
            return binder;
        }

        @Override
        public <T> Binder bind(String ipIdentity, ServiceProvider<T> serviceProvider) {
            // NOP
            return this;
        }

        @Override
        public Binder bindMany(String ipIdentity, ServiceProvider<?>... serviceProviders) {
            // NOP
            return this;
        }

        @Override
        public Binder bindVoid(String ipIdentity) {
            // NOP
            return this;
        }

        @Override
        public Binder resolvedBind(String ipIdentity, Class<?> serviceType) {
            // NOP
            return this;
        }

        @Override
        public void commit() {
            // NOP
        }
    }


    private static class Context {
        private final AtomicBoolean isInitializing = new AtomicBoolean();
        private final AtomicBoolean isBinding = new AtomicBoolean();
        private final AtomicReference<DefaultServices> services = new AtomicReference<>();
        private final AtomicReference<DefaultInjector> injector = new AtomicReference<>();
        private final AtomicReference<List<Module>> moduleList = new AtomicReference<>();
        private final AtomicReference<List<Application>> applicationList = new AtomicReference<>();
        private final PicoServicesConfig config;
        private final boolean isGlobal;
        private final boolean isDynamic;
        private final Optional<ActivationLog> activationLog;
        private CountDownLatch initializedServices = new CountDownLatch(1);
        private final Thread initializingThread;

        private Context(PicoServicesConfig cfg,
                        boolean isGlobal,
                        ActivationLog activationLog) {
            this.config = cfg;
            this.isGlobal = isGlobal;

            if (null == activationLog
                    && cfg.asBoolean(PicoServicesConfig.KEY_ACTIVATION_LOGS_ENABLED)) {
                activationLog = DefaultActivationLog.create(DefaultActivationLog.NAME_FULL, LOGGER);
            }
            this.activationLog = Optional.ofNullable(activationLog);

            this.isDynamic = cfg.asBoolean(PicoServicesConfig.KEY_SUPPORTS_DYNAMIC);
            this.initializingThread = Thread.currentThread();
        }
    }

}
