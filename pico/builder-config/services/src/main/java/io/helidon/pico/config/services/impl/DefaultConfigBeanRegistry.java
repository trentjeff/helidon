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

package io.helidon.pico.config.services.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigValue;
import io.helidon.pico.builder.api.ConfiguredOption;
import io.helidon.pico.config.services.AbstractConfiguredServiceProvider;
import io.helidon.pico.config.spi.ConfigBeanAttributeVisitor;
import io.helidon.pico.config.spi.ConfigBeanInfo;
import io.helidon.pico.config.services.ConfigProvider;
import io.helidon.pico.config.services.ConfiguredServiceProvider;
import io.helidon.pico.config.services.InternalConfigBeanRegistry;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.PicoServiceProviderException;
import io.helidon.pico.PicoServices;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.spi.ext.ServiceProviderComparator;

import jakarta.inject.Singleton;

/**
 * The default implementation for {@link io.helidon.pico.config.services.ConfigBeanRegistry}.
 */
@Singleton
public class DefaultConfigBeanRegistry implements InternalConfigBeanRegistry {

    /**
     * The default config bean instance id.
     */
    public static final String DEFAULT_INSTANCE_ID = "@default";

    private static final System.Logger LOGGER = System.getLogger(DefaultConfigBeanRegistry.class.getName());

    private static final boolean FORCE_VALIDATE_USING_BEAN_ATTRIBUTES = false;
    private static final boolean FORCE_VALIDATE_USING_CONFIG_ATTRIBUTES = true;

    private final AtomicBoolean initializing = new AtomicBoolean();
    private CountDownLatch initialized = new CountDownLatch(1);
    private final Map<ConfiguredServiceProvider<?, ?>, ConfigBeanInfo> configuredServiceProviderMetaConfigBeanMap =
            new ConcurrentHashMap<>();
    private final Map<String, List<ConfiguredServiceProvider<?, ?>>> configuredServiceProvidersByConfigKey =
            new ConcurrentHashMap<>();

    protected boolean isInitialized() {
        return (0 == initialized.getCount());
    }

    @Override
    public void reset() {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            System.Logger.Level level = isInitialized() ? System.Logger.Level.INFO : System.Logger.Level.DEBUG;
            LOGGER.log(level, "Resetting...");
        }

        configuredServiceProviderMetaConfigBeanMap.clear();
        configuredServiceProvidersByConfigKey.clear();
        initializing.set(false);
        initialized = new CountDownLatch(1);
    }

    @Override
    public void bind(ConfiguredServiceProvider<?, ?> configuredServiceProvider,
                     QualifierAndValue configuredByQualifier,
                     ConfigBeanInfo metaConfigBeanInfo) {
        if (initializing.get()) {
            throw new ConfigException("unable to bind config post initialization: "
                                              + configuredServiceProvider.description());
        }

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Binding "
                    + configuredServiceProvider.getServiceType()
                    + " with " + configuredByQualifier.value());
        }

        Object prev = configuredServiceProviderMetaConfigBeanMap
                .put(Objects.requireNonNull(configuredServiceProvider),
                     Objects.requireNonNull(metaConfigBeanInfo));
        assert (Objects.isNull(prev)) : "duplicate service provider initialization occurred";

        String configKey = Objects.requireNonNull(metaConfigBeanInfo.validatedConfigKey());
        Class<?> cspType = Objects.requireNonNull(configuredServiceProvider.getServiceType());
        configuredServiceProvidersByConfigKey.compute(configKey, (k, cspList) -> {
            if (Objects.isNull(cspList)) {
                cspList = new ArrayList<>();
            }

            Optional<ConfiguredServiceProvider<?, ?>> prevCsp = cspList.stream()
                    .filter(it -> (cspType.equals(it.getConfigBeanType())))
                    .findAny();
            assert (prevCsp.isEmpty()) : "duplicate service provider initialization occurred";

            boolean added = cspList.add(configuredServiceProvider);
            assert (added);

            return cspList;
        });
    }

    @Override
    public void initialize(PicoServices ignoredPicoServices) {
        try {
            if (initializing.getAndSet(true)) {
                // all threads should wait for the leader (and the config bean registry) to have been fully initialized
                initialized.await();
                return;
            }

            Config config = ConfigProvider.getConfigInstance();
            if (Objects.isNull(config)) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "Unable to initialize w/ no config to read - be sure to provide or initialize "
                                   + ConfigProvider.class.getName() + " prior to service activation.");
                reset();
                return;
            }

            LOGGER.log(System.Logger.Level.DEBUG, "Initializing...");
            initialize(config);
            // we are now ready and initialized
            initialized.countDown();
        } catch (Throwable t) {
            LOGGER.log(System.Logger.Level.ERROR, "Error while initializing config bean registry", t);
            reset();
            throw new PicoServiceProviderException("Error while initializing config bean registry", t, null);
        }
    }

    private void initialize(Config cfg) {
        if (configuredServiceProvidersByConfigKey.isEmpty()) {
            LOGGER.log(System.Logger.Level.INFO, "No config driven services found...");
            return;
        }

        // first load all the top-level config beans... but defer resolve until later phase...
        configuredServiceProviderMetaConfigBeanMap.forEach((configuredServiceProvider, metaConfigBeanInfo) -> {
            String key = metaConfigBeanInfo.validatedConfigKey();
            Config config = cfg.get(key);
            Map<String, Map<String, Object>> metaAttributes = configuredServiceProvider.getConfigBeanAttributes();
            if (config.exists()) {
                loadConfigBeans(config, configuredServiceProvider, metaConfigBeanInfo, metaAttributes);
            } else if (metaConfigBeanInfo.defaultConfigBeanUsingDefaults()) {
                Object cfgBean = Objects.requireNonNull(configuredServiceProvider.toConfigBean(null, null),
                                                         "unable to create default config bean for "
                                                   + ServiceProvider.toDescription(configuredServiceProvider));
                registerConfigBean(cfgBean, DEFAULT_INSTANCE_ID, config, configuredServiceProvider, metaAttributes);
            }
        });

        if (!cfg.exists() || cfg.isLeaf()) {
            return;
        }

        // now find all the sub root level config beans also... still deferring resolution until a later phase...
        visitAndInitialize(cfg.asNodeList().get(), 0);
        LOGGER.log(System.Logger.Level.DEBUG, "Finishing walking config tree...");
    }

    private void visitAndInitialize(List<Config> configs, int depth) {
        configs.forEach((config) -> {
            if (depth > 0) {
                String key = config.name();

                List<ConfiguredServiceProvider<?, ?>> csps = configuredServiceProvidersByConfigKey.get(key);
                if (Objects.nonNull(csps) && !csps.isEmpty()) {
                    csps.forEach(configuredServiceProvider -> {
                        ConfigBeanInfo metaConfigBeanInfo =
                                Objects.requireNonNull(configuredServiceProviderMetaConfigBeanMap.get(configuredServiceProvider));
                        Map<String, Map<String, Object>> metaAttributes = configuredServiceProvider.getConfigBeanAttributes();
                        loadConfigBeans(config, configuredServiceProvider, metaConfigBeanInfo, metaAttributes);
                    });
                }
            }

            if (!config.isLeaf()) {
                visitAndInitialize(config.asNodeList().get(), depth + 1);
            }
        });
    }

    @Override
    public boolean isReady() {
        return isInitialized();
    }

    @Override
    public Map<ConfiguredServiceProvider<?, ?>, ConfigBeanInfo> getConfigurableServiceProviders() {
        return Collections.unmodifiableMap(configuredServiceProviderMetaConfigBeanMap);
    }

    @Override
    public List<ConfiguredServiceProvider<?, ?>> getConfiguredServiceProviders() {
        List<ConfiguredServiceProvider<?, ?>> result = new ArrayList<>();

        configuredServiceProvidersByConfigKey.values().forEach(cspList ->
            cspList.stream()
                    .filter(csp -> csp instanceof AbstractConfiguredServiceProvider)
                    .map(AbstractConfiguredServiceProvider.class::cast)
                    .forEach(rootCsp -> {
                        rootCsp.assertIsRootProvider(true, false);
                        Map<String, Optional<AbstractConfiguredServiceProvider<?, ?>>> cfgBeanMap =
                                rootCsp.getConfiguredServicesMap();
                        cfgBeanMap.values().forEach(slaveCsp -> slaveCsp.ifPresent(result::add));
                    }));

        if (result.size() > 1) {
            result.sort(new ServiceProviderComparator());
        }

        return result;
    }

    @Override
    public List<?> getConfigBeansByConfigKey(String key, String fullConfigKey) {
        List<ConfiguredServiceProvider<?, ?>> cspsUsingSameKey =
                configuredServiceProvidersByConfigKey.get(Objects.requireNonNull(key));
        if (Objects.isNull(cspsUsingSameKey)) {
            return Collections.emptyList();
        }

        List<Object> result = new LinkedList<>();
        cspsUsingSameKey.stream()
                .filter(csp -> csp instanceof AbstractConfiguredServiceProvider)
                .map(AbstractConfiguredServiceProvider.class::cast)
                .forEach(csp -> {
                    Map<String, ?> configBeans = csp.getConfigBeanMap();
                    if (Objects.isNull(fullConfigKey)) {
                        result.addAll(configBeans.values());
                    } else {
                        configBeans.forEach((k, v) -> {
                            if (fullConfigKey.equals(k)) {
                                result.add(v);
                            }
                        });
                    }
                });
        return result;
    }

    @Override
    public <CB> Map<String, CB> getConfigBeanMapByConfigKey(String key, String fullConfigKey) {
        List<ConfiguredServiceProvider<?, ?>> cspsUsingSameKey =
                configuredServiceProvidersByConfigKey.get(Objects.requireNonNull(key));
        if (Objects.isNull(cspsUsingSameKey)) {
            return Collections.emptyMap();
        }

        Map<String, CB> result = new TreeMap<>(AbstractConfiguredServiceProvider.getConfigBeanComparator());
        cspsUsingSameKey.stream()
                .filter(csp -> csp instanceof AbstractConfiguredServiceProvider)
                .map(AbstractConfiguredServiceProvider.class::cast)
                .forEach(csp -> {
                    Map<String, ?> configBeans = csp.getConfigBeanMap();
                    configBeans.forEach((k, v) -> {
                        if (Objects.isNull(fullConfigKey) || fullConfigKey.equals(k)) {
                            Object prev = result.put(k, (CB) v);
                            if (Objects.nonNull(prev) && prev != v) {
                                throw new IllegalStateException("had two entries with the same key: "
                                                                        + prev + " and " + v);
                            }
                        }
                    });
                });
        return result;
    }

    @Override
    public <CB> Map<String, CB> getAllConfigBeans() {
        Map<String, CB> result = new TreeMap<>(AbstractConfiguredServiceProvider.getConfigBeanComparator());
        configuredServiceProvidersByConfigKey.forEach((key, value) -> value.stream()
                .filter(csp -> csp instanceof AbstractConfiguredServiceProvider)
                .map(AbstractConfiguredServiceProvider.class::cast)
                .forEach(csp -> {
                    Map<String, ?> configBeans = csp.getConfigBeanMap();
                    configBeans.forEach((key1, value1) -> {
                        Object prev = result.put(key1, (CB) value1);
                        if (Objects.nonNull(prev) && prev != value1) {
                            throw new IllegalStateException("had two entries with the same key: "
                                                                    + prev + " and " + value1);
                        }
                    });
                }));
        return result;
    }

    protected <T, CB> void loadConfigBeans(Config config,
                                           ConfiguredServiceProvider<T, CB> configuredServiceProvider,
                                           ConfigBeanInfo metaConfigBeanInfo,
                                           Map<String, Map<String, Object>> metaAttributes) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Loading config bean(s) for "
                    + configuredServiceProvider.getServiceType() + " with config: "
                    + config.key().toString());
        }

        ConfigValue<List<Config>> nodeList = config.asNodeList();
        Object baseConfigBean = maybeLoadBaseConfigBean(config, nodeList, configuredServiceProvider);
        Map<String, CB> mapOfInstanceBasedConfig = maybeLoadConfigBeans(nodeList, configuredServiceProvider);

        // validate what we've loaded, to ensure it complies to the meta config info policy...
        if (!metaConfigBeanInfo.repeatable() && !mapOfInstanceBasedConfig.isEmpty()) {
            throw new ConfigException("Expected to only have a single base, non-repeatable configuration for "
                                              + configuredServiceProvider.getServiceType() + " with config: "
                                              + config.key().toString());
        }

        if (Objects.nonNull(baseConfigBean)) {
            registerConfigBean(baseConfigBean, null, config, configuredServiceProvider, metaAttributes);
        }
        mapOfInstanceBasedConfig
                .forEach((instanceId, configBean) ->
                    registerConfigBean(configBean,
                                       config.key().toString() + "." + instanceId,
                                       config.get(instanceId),
                                       configuredServiceProvider,
                                       metaAttributes));
    }

    /**
     * The base config bean must be a root config, and is only available if there is a non-numeric
     * key in our node list (e.g., "x.y" not "x.1.y").
     */
    protected <T, CB> CB maybeLoadBaseConfigBean(Config config,
                                             ConfigValue<List<Config>> nodeList,
                                             ConfiguredServiceProvider<T, CB> configuredServiceProvider) {
        boolean hasAnyNonNumericNodes = nodeList.get().stream()
               .anyMatch(cfg -> toNumeric(cfg.name()).isEmpty());
        if (!hasAnyNonNumericNodes) {
            return null;
        }

        return toConfigBean(config, configuredServiceProvider);
    }

    /**
     * These are any {config}.N instances, not the base w/o the N.
     */
    protected <T, CB> Map<String, CB> maybeLoadConfigBeans(ConfigValue<List<Config>> nodeList,
                                                           ConfiguredServiceProvider<T, CB> configuredServiceProvider) {
        Map<String, CB> result = new LinkedHashMap<>();

        nodeList.get().stream()
                .filter(cfg -> toNumeric(cfg.name()).isPresent())
                .forEach(cfg -> {
            String key = cfg.name();
            CB configBean = toConfigBean(cfg, configuredServiceProvider);
            Object prev = result.put(key, configBean);
            assert (Objects.isNull(prev)) : prev + " and " + configBean;
        });

        return result;
    }

    protected <T, CB> CB toConfigBean(Config config,
                                      ConfiguredServiceProvider<T, CB> configuredServiceProvider) {
        CB configBean = Objects.requireNonNull(configuredServiceProvider.toConfigBean(config, null),
                                               "unable to create default config bean for "
                                                       + ServiceProvider.toDescription(configuredServiceProvider));
        if (configuredServiceProvider instanceof AbstractConfiguredServiceProvider) {
            AbstractConfiguredServiceProvider csp = (AbstractConfiguredServiceProvider) configuredServiceProvider;
            csp.setConfigBeanInstanceId(configBean, config.key().toString());
        }

        return configBean;
    }

    static Optional<Integer> toNumeric(String val) {
        if (Objects.isNull(val)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Integer.parseInt(val));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Validates the config bean against the declared policy, coming by way of annotations on the
     * {@link io.helidon.pico.builder.api.ConfiguredOption}'s.
     *
     * @param csp                       the configured service provider
     * @param key                       the config key being validated (aka instance id)
     * @param configBean                the config bean itself
     * @param metaAttributes            the meta-attributes that captures the policy in a map like structure by
     *                                  attribute name
     * @throws PicoServiceProviderException if the provided config bean is not valide according to policy
     */
    protected <T> void validate(Object configBean,
                                String key,
                                Config config,
                                AbstractConfiguredServiceProvider<T, Object> csp,
                                Map<String, Map<String, Object>> metaAttributes) {
        final Set<String> problems = new LinkedHashSet<>();
        final String instanceId = csp.getConfigBeanInstanceId(configBean);
        assert (AnnotationAndValue.hasNonBlankValue(key));
        assert (Objects.isNull(config) || DEFAULT_INSTANCE_ID.equals(key) || (config.key().toString().equals(key)))
                : key + " and " + config.key().toString();

        ConfigBeanAttributeVisitor visitor = new ConfigBeanAttributeVisitor<Object>() {
            @Override
            public void visit(String attrName,
                              Supplier<Object> valueSupplier,
                              Map<String, Object> meta,
                              Object userDefinedCtx,
                              Class<?> type,
                              Class<?>... typeArgument) {
                Map<String, Object> metaAttrPolicy = metaAttributes.get(attrName);
                if (Objects.isNull(metaAttrPolicy)) {
                    problems.add("Unable to query policy for config key '" + key + "'");
                    return;
                }

                Object required = metaAttrPolicy.get("required");
                String attrConfigKey = (String) Objects.requireNonNull(metaAttrPolicy.get("key"));

                if (Objects.isNull(required)) {
                    required = ConfiguredOption.DEFAULT_REQUIRED;
                } else if (!(required instanceof Boolean)) {
                    required = Boolean.parseBoolean((String) required);
                }

                if ((boolean) required) {
                    boolean validated = false;

                    if (FORCE_VALIDATE_USING_CONFIG_ATTRIBUTES) {
                        validated = validateUsingConfigAttributes(
                                instanceId, attrName, attrConfigKey, config, valueSupplier, problems);
                    }

                    if (!validated || FORCE_VALIDATE_USING_BEAN_ATTRIBUTES) {
                        validateUsingBeanAttributes(valueSupplier, attrName, problems);
                    }
                }

                // TODO: allowed values check ...

            }
        };

        csp.visitAttributes(configBean, visitor, configBean);

        if (!problems.isEmpty()) {
            throw new PicoServiceProviderException("validation rules violated for "
                                                           + csp.getConfigBeanType()
                                                           + " with config key '" + key
                                                           + "':\n"
                                                           + String.join(", ", problems).trim(), null, csp);
        }
    }

    protected static boolean validateUsingConfigAttributes(String instanceId,
                                                           String attrName,
                                                           String attrConfigKey,
                                                           Config config,
                                                           Supplier<Object> beanBasedValueSupplier,
                                                           Set<String> problems) {
        if (Objects.isNull(config)) {
            if (!DEFAULT_INSTANCE_ID.equals(instanceId)) {
                problems.add("Unable to obtain backing config for service provider for " + attrConfigKey);
            }

            return false;
        } else {
            Config attrConfig = config.get(attrConfigKey);
            if (attrConfig.exists()) {
                return true;
            }

            // if we have a default value from our bean, then that is the fallback verification
            Object val = beanBasedValueSupplier.get();
            if (Objects.isNull(val)) {
                problems.add("'" + attrConfigKey + "' is a required configuration for attribute '" + attrName + "'");
                return true;
            }

            // full through to bean validation next, just for any added checks we might do there
            return false;
        }
    }

    protected static void validateUsingBeanAttributes(Supplier<Object> valueSupplier,
                                                      String attrName,
                                                      Set<String> problems) {
        Object val = valueSupplier.get();
        if (Objects.isNull(val)) {
            problems.add("'" + attrName + "' is a required attribute and cannot be null");
        } else {
            if (!(val instanceof String)) {
                val = val.toString();
            }
            if (!AnnotationAndValue.hasNonBlankValue((String) val)) {
                problems.add("'" + attrName + "' is a required attribute and cannot be blank");
            }
        }
    }

    protected <CB> void registerConfigBean(Object configBean,
                                           String instanceId,
                                           Config config,
                                           ConfiguredServiceProvider<?, CB> configuredServiceProvider,
                                           Map<String, Map<String, Object>> metaAttributes) {
        assert (configuredServiceProvider instanceof AbstractConfiguredServiceProvider);
        AbstractConfiguredServiceProvider<?, Object> csp =
                (AbstractConfiguredServiceProvider<?, Object>) configuredServiceProvider;

        if (Objects.nonNull(instanceId)) {
            csp.setConfigBeanInstanceId(configBean, instanceId);
        } else {
            instanceId = configuredServiceProvider.getConfigBeanInstanceId((CB) configBean);
        }

        if (DEFAULT_INSTANCE_ID.equals(instanceId)) {
            // default config beans should not be validated against any config, even if we have it available
            config = null;
        } else {
            Optional<Config> beanConfig = csp.getRawConfig();
            if (beanConfig.isPresent()) {
                // prefer to use the bean's config over ours if it has it
                config = beanConfig.get();
            }
        }

        // will throw if not valid
        validate(configBean, instanceId, config, csp, metaAttributes);

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                       "Registering config bean '" + instanceId + "' with " + configuredServiceProvider.getServiceType());
        }

        csp.registerConfigBean(instanceId, configBean);
    }

}
