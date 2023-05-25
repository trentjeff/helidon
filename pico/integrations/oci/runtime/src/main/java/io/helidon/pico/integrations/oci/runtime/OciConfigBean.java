/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.integrations.oci.runtime;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.config.ConfigBean;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Configuration used by {@link OciAuthenticationDetailsProvider}.
 */
@ConfigBean(OciConfigBean.NAME)
public interface OciConfigBean {

    String NAME = "oci";

    List<String> authStrategies();

    @ConfiguredOption(key = "config.path")
    Optional<String> configPath();

    @ConfiguredOption(key = "config.profile")
    Optional<String> configProfile();

    @ConfiguredOption(key = "auth.fingerprint")
    Optional<String> authFingerprint();

    @ConfiguredOption(key = "auth.region")
    Optional<String> authRegion();

    @ConfiguredOption(key = "auth.tenant-id")
    Optional<String> authTenantId();

    @ConfiguredOption(key = "auth.user-id")
    Optional<String> authUserId();

}
