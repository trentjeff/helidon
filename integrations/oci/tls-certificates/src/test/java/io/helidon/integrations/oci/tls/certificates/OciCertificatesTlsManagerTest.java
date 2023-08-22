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

package io.helidon.integrations.oci.tls.certificates;

import java.util.Map;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.tls.TlsManager;
import io.helidon.common.tls.spi.TlsManagerProvider;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class OciCertificatesTlsManagerTest {

    // TODO:
    @Disabled
    @Test
    void tlsManager() {
        Config config = Config.create(ConfigSources.create(
                Map.of("oci-certificates-tls-manager.schedule", "123",
                       "oci-certificates-tls-manager.vault-crypto-endpoint", "http://localhost",
                       "oci-certificates-tls-manager.ca-ocid", "caOcid",
                       "oci-certificates-tls-manager.cert-ocid", "certOcid",
                       "oci-certificates-tls-manager.key-ocid", "keyOcid",
                       "oci-certificates-tls-manager.key-password", "keyPassword"
                )));
        TlsManagerProvider provider = HelidonServiceLoader.builder(ServiceLoader.load(TlsManagerProvider.class))
                .build()
                .asList()
                .iterator()
                .next();
        TlsManager tlsManager = provider.create(config.get(provider.configKey()), "@default");
        assertThat(tlsManager, notNullValue());
    }

}
