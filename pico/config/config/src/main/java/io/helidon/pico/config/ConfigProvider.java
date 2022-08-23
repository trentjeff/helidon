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

package io.helidon.pico.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;

import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Note: The provider with the highest weight will be selected to provide config.
 */
@Singleton
@Named(ConfigProvider.SYSTEM)
@Weight(0) // note to self: let us be the last ones to accept config builder
public class ConfigProvider implements Provider<Config> {

    static final String SYSTEM = "system";

    private final LazyValue<SystemConfig> lazyRoot = LazyValue.create(SystemConfig::new);

    @Override
    public Config get() {
        return lazyRoot.get();
    }

    static class SystemConfig implements Config {
        private final Config.Key key = ConfigKeyImpl.of();
        private final Map<String, Config> childConfigs = new ConcurrentHashMap<>();

        @Override
        public Config.Key key() {
            return key;
        }

        @Override
        public Config get(String key) {
            // note to self: implement
            return null;
        }

        @Override
        public Config.Type type() {
            return Type.OBJECT;
        }

        @Override
        public <T> ConfigValue<T> as(Class<T> type) {
            // note to self: implement
            return null;
        }
    }

    // note to self: implement
    static Config of(Config.Key key, Config.Type type) {
        return null;
    }

}
