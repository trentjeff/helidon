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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigException;
import io.helidon.common.config.ConfigValue;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.spi.Resetable;

/**
 * The default reference implementation for provider configuration.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
class DefaultPicoServicesConfig implements PicoServicesConfig, Config.Key, Resetable {
    /**
     * The provider name for this module.
     */
    public static final String PROVIDER = "oracle";

    private static final System.Logger LOGGER = System.getLogger(DefaultPicoServicesConfig.class.getName());
    private static final String TAG_CONFIG_KEY = PicoServicesConfig.NAME;

    private final Optional<Config> cfg;
    private Map<String, String> props;

    /**
     * The default constructor.
     */
    DefaultPicoServicesConfig() {
        this(ConfigHolder.config());
    }

    DefaultPicoServicesConfig(Optional<Config> cfg) {
        this.cfg = cfg;
        softReset();
    }

    /**
     * Assign a value to a configuration option for this JVM execution.
     *
     * @param key the key that represents the configuration option
     * @param val the value to assign
     */
    public void set(String key, String val) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "setting " + key + " to '" + val + "'");
        }
        props.put(key, val);
    }

    @Override
    public Key parent() {
        throw new IllegalStateException();
    }

    @Override
    public boolean isRoot() {
        return true;
    }

    @Override
    public String toString() {
        return props.toString();
    }

    @Override
    public Key child(Key key) {
        return new KeyVal(key.name());
    }

    @Override
    public Config get(String key) {
        return new SimpleConfig(new KeyVal(key));
    }

    @Override
    public Key key() {
        return this;
    }

    @Override
    public String name() {
        return cfg.map(Config::name).orElse(TAG_CONFIG_KEY);
    }

    @Override
    public Config detach() {
        throw new IllegalStateException();
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public boolean isList() {
        return false;
    }

    @Override
    public boolean hasValue() {
        return false;
    }

    @Override
    public <T> ConfigValue<T> as(Class<T> type) {
        throw new IllegalStateException();
    }

    @Override
    public <T> io.helidon.common.config.ConfigValue<T> map(Function<io.helidon.common.config.Config, T> mapper) {
        throw new IllegalStateException();
    }

    @Override
    public <T> ConfigValue<List<T>> asList(Class<T> type) {
        throw new IllegalStateException();
    }

    @Override
    public ConfigValue<Map<String, String>> asMap() {
        return new SimpleVal<>(DefaultPicoServicesConfig.this.key(), Collections.unmodifiableMap(props));
    }

    @Override
    public boolean softReset() {
        this.props = defaultProps();
        this.cfg.ifPresent(config -> props.putAll(config.asMap().get()));
        return true;
    }

    @Override
    public int compareTo(Key o) {
        throw new IllegalStateException();
    }


    private static Map<String, String> defaultProps() {
        Map<String, String> props = new TreeMap<>();
        props.put(KEY_PROVIDER, PROVIDER);
        props.put(KEY_VERSION, Versions.CURRENT_VERSION);
        props.put(KEY_DEADLOCK_TIMEOUT_IN_MILLIS, String.valueOf(DEFAULT_DEADLOCK_TIMEOUT_IN_MILLIS));
        props.put(KEY_ACTIVATION_LOGS_ENABLED, String.valueOf(DEFAULT_ACTIVATION_LOGS_ENABLED));
        props.put(KEY_SERVICE_LOOKUP_CACHING_ENABLED, String.valueOf(DEFAULT_SERVICE_LOOKUP_CACHING_ENABLED));
        props.put(KEY_SUPPORTS_DYNAMIC, String.valueOf(DEFAULT_SUPPORTS_DYNAMIC));
        props.put(KEY_SUPPORTS_REFLECTION, String.valueOf(DEFAULT_SUPPORTS_REFLECTION));
        props.put(KEY_SUPPORTS_COMPILE_TIME, String.valueOf(DEFAULT_SUPPORTS_COMPILE_TIME));
        props.put(KEY_SUPPORTS_JSR330, String.valueOf(DEFAULT_SUPPORTS_JSR330));
        props.put(KEY_SUPPORTS_JSR330_STATIC, String.valueOf(DEFAULT_SUPPORTS_STATIC));
        props.put(KEY_SUPPORTS_JSR330_PRIVATE, String.valueOf(DEFAULT_SUPPORTS_PRIVATE));
        props.put(KEY_SUPPORTS_THREAD_SAFE_ACTIVATION, String.valueOf(DEFAULT_SUPPORTS_THREAD_SAFE_ACTIVATION));
        props.put(KEY_BIND_APPLICATION, String.valueOf(DEFAULT_BIND_APPLICATION));
        props.put(KEY_BIND_MODULES, String.valueOf(DEFAULT_BIND_MODULES));
        return props;
    }


    // TODO: 1: SimpleConfig should ideally not have this many methods that need implementing.
    // TODO: 2: SimpleConfig and KeyVal should ideally be able to share the same impl, but map() is defined differently.
    // TODO: 3: ConfigException should not require a message (i.e., we need an empty ctor).
    private class SimpleConfig implements Config {
        private final KeyVal keyVal;

        public SimpleConfig(KeyVal keyVal) {
            this.keyVal = keyVal;
        }

        @Override
        public Key key() {
            return keyVal;
        }

        @Override
        public Config get(String key) {
            // TODO: change to ConfigException?
            throw new IllegalStateException();
        }

        @Override
        public Config detach() {
            // TODO: change to ConfigException?
            throw new IllegalStateException();
        }

        @Override
        public boolean exists() {
            return hasValue();
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public boolean isObject() {
            return false;
        }

        @Override
        public boolean isList() {
            return false;
        }

        @Override
        public boolean hasValue() {
            return props.containsKey(keyVal.name());
        }

        @Override
        @SuppressWarnings({"unchecked", "WrapperTypeMayBePrimitive"})
        public <T> ConfigValue<T> as(Class<T> type) {
            if (type == String.class) {
                return new SimpleVal<>(key(), (T) props.get(name()));
            } else if (type == Boolean.class) {
                Boolean val = Boolean.valueOf(props.get(name()));
                return new SimpleVal<>(key(), (T) val);
            } else if (type == Long.class) {
                Long val = Long.valueOf(props.get(name()));
                return new SimpleVal<>(key(), (T) val);
            }
            // TODO: change to ConfigException?
            throw new IllegalStateException();
        }

        @Override
        public <T> ConfigValue<T> map(Function<Config, T> mapper) {
            // Note: this map() function clashes with KeyVal.map() --- can we name these differently?
            // TODO: should this default behavior be pushed to common/config?
            // TODO: change to ConfigException?
            throw new IllegalStateException();
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigException {
            // TODO: should this default behavior be pushed to common/config?
            // TODO: change to ConfigException?
            throw new IllegalStateException();
        }

        @Override
        public ConfigValue<Map<String, String>> asMap() throws ConfigException {
            // TODO: should this default behavior be pushed to common/config?
            // TODO: change to ConfigException?
            throw new IllegalStateException();
        }
    }


    private class KeyVal implements Config.Key, ConfigValue<String> {
        private final String name;

        private KeyVal(String keyName) {
            this.name = keyName;
        }

        @Override
        public Key parent() {
            return cfg.orElseThrow(IllegalStateException::new).key();
        }

        @Override
        public boolean isRoot() {
            return false;
        }

        @Override
        public Key key() {
            return this;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Optional<String> asOptional() {
            // TODO: should this default behavior be pushed to common/config?  Will need to use hasValue() as part of the impl
            return Optional.ofNullable(props.get(name()));
        }

        @Override
        public String get() {
            return props.get(name());
        }

        @Override
        public <N> ConfigValue<N> as(Function<String, N> mapper) {
            // TODO: should this default behavior be pushed to common/config?
            // TODO: change to ConfigException?
            throw new IllegalStateException();
        }

        @Override
        public Key child(Key key) {
            // TODO: should this default behavior be pushed to common/config?
            // TODO: change to ConfigException?
            throw new IllegalStateException();
        }

        @Override
        public int compareTo(Key o) {
            // TODO: should this default behavior be pushed to common/config?
            // TODO: change to ConfigException?
            throw new IllegalStateException();
        }
    }


    private static class SimpleVal<T> implements ConfigValue<T> {
        private final Key key;
        private final T val;

        private SimpleVal(Key key, T val) {
            this.key = key;
            this.val = val;
        }

        @Override
        public Key key() {
            return key;
        }

        @Override
        public Optional<T> asOptional() {
            return Optional.of(get());
        }

        @Override
        public T get() {
            return val;
        }

        @Override
        public <N> ConfigValue<N> as(Function<T, N> mapper) {
            // TODO: should this default behavior be pushed to common/config?
            // TODO: change to ConfigException?
            throw new IllegalStateException();
        }
    }

}
