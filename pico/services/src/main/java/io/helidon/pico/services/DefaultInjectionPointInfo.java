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

import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.pico.spi.DefaultServiceInfo;
import io.helidon.pico.spi.DefaultTypeName;
import io.helidon.pico.spi.InjectionPointInfo;
import io.helidon.pico.spi.ServiceInfo;
import io.helidon.pico.types.TypeName;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Models a specific receiver target of injection.
 */
//@SuperBuilder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("unchecked")
public class DefaultInjectionPointInfo extends DefaultElementInfo implements InjectionPointInfo {
    // note to self: study perf and mem usage on this toggle -jtrent
    private static final boolean BIND_ID_ON_FIRST_USAGE = false;

    String identity;
    private String baseIdentity;
    private final boolean listWrapped;
    private final boolean optionalWrapped;
    private final boolean providerWrapped;
    @JsonIgnore private ServiceInfo dependencyToServiceInfo;

    protected DefaultInjectionPointInfo(DefaultInjectionPointInfoBuilder builder) {
        super(builder);
        this.listWrapped = builder.listWrapped;
        this.optionalWrapped = builder.optionalWrapped;
        this.providerWrapped = builder.providerWrapped;
    }

    /**
     * Returns an instance pre-configured with an identity and a service type dependency.
     *
     * @param identity                  the identity
     * @param dependencyToServiceInfo   the service type dependency
     * @return the created instance
     */
    public static DefaultInjectionPointInfo create(String identity,
                                                   ServiceInfo dependencyToServiceInfo) {
        DefaultInjectionPointInfo instance = builder().build();
        instance.identity = identity;
        instance.setDependencyToServiceInfo(dependencyToServiceInfo);
        return instance;
    }

    /**
     * @return A builder for {@link io.helidon.pico.spi.impl.DefaultInjectionPointInfo}.
     */
    public static DefaultInjectionPointInfoBuilder<? extends DefaultElementInfo, ? extends DefaultElementInfoBuilder<?, ?>>
                    builder() {
        return new DefaultInjectionPointInfoBuilder() {};
    }

    /**
     * @return A builder initialized with the current attributes.
     */
    @Override
    public DefaultInjectionPointInfoBuilder<? extends DefaultElementInfo, ? extends DefaultElementInfoBuilder<?, ?>>
                    toBuilder() {
        return new DefaultInjectionPointInfoBuilder(this) {};
    }

    @Override
    public String toString() {
        String serviceTypeName = getServiceTypeName();
        if (Objects.isNull(serviceTypeName)) {
            return getIdentity();
        }
        return serviceTypeName + "." + getIdentity();
    }

    @JsonIgnore
    @Override
    public String getBaseIdentity() {
        if (Objects.nonNull(baseIdentity)) {
            return baseIdentity;
        }

        ElementKind kind = Objects.requireNonNull(getElementKind());
        String elemName = getElementName();
        Access access = getAccess();
        Supplier<String> packageName = toPackageName(getServiceTypeName());
        String id;
        if (ElementKind.FIELD == kind) {
            id = toFieldIdentity(elemName, access, packageName);
        } else {
            id = toMethodBaseIdentity(elemName, getElementArgs(), access, packageName);
        }
        if (BIND_ID_ON_FIRST_USAGE) {
            baseIdentity = id;
        }

        return id;
    }

    @Override
    public String getIdentity() {
        if (Objects.nonNull(identity)) {
            return identity;
        }

        ElementKind kind = Objects.requireNonNull(getElementKind());
        String elemName = getElementName();
        Access access =
                getAccess();
        Supplier<String> packageName = toPackageName(getServiceTypeName());
        String id;
        if (ElementKind.FIELD == kind) {
            id = toFieldIdentity(elemName, access, packageName);
        } else {
            id = toMethodIdentity(elemName, getElementArgs(), getElementOffset(), access, packageName);
        }
        if (BIND_ID_ON_FIRST_USAGE) {
            identity = id;
        }

        return id;
    }

    /**
     * Translates this instance to a {@link ServiceInfo} instance.
     *
     * @return a service info instance
     */
    public ServiceInfo toDependencyToServiceInfo() {
        if (Objects.isNull(dependencyToServiceInfo)) {
            setDependencyToServiceInfo(DefaultServiceInfo.builder()
                    .contractImplemented(getElementTypeName())
                    .qualifiers(getQualifiers())
                    .scopeTypeNames(getScopeTypeNames())
                    .build());
        }
        return dependencyToServiceInfo;
    }

    /**
     * Sets the service info associated with its dependency.
     *
     * @param serviceInfo the service info
     */
    public void setDependencyToServiceInfo(ServiceInfo serviceInfo) {
        assert (Objects.isNull(this.dependencyToServiceInfo) || this.dependencyToServiceInfo == serviceInfo
                || this.dependencyToServiceInfo.equals(serviceInfo));
        this.dependencyToServiceInfo = serviceInfo;
    }

    @Override
    public boolean listWrapped() {
        return listWrapped;
    }

    @Override
    public boolean optionalWrapped() {
        return optionalWrapped;
    }

    @Override
    public boolean providerWrapped() {
        return providerWrapped;
    }

    static Supplier<String> toPackageName(String serviceTypeName) {
        return () -> toPackageName(DefaultTypeName.createFromTypeName(serviceTypeName));
    }

    static String toPackageName(TypeName typeName) {
        return Objects.nonNull(typeName) ? typeName.getPackageName() : null;
    }

    /**
     * The field's identity and its base identity are one in the same since there is no arguments to handle.
     *
     * @param elemName      the non-null field name
     * @param ignoredAccess the access for the field
     * @param packageName   the package name of the owning service type containing the field
     * @return the field identity (relative to the owning service type)
     */
    public static String toFieldIdentity(String elemName, Access ignoredAccess, Supplier<String> packageName) {
        String id = Objects.requireNonNull(elemName);
        String pName = Objects.isNull(packageName) ? null : packageName.get();
        if (Objects.nonNull(pName)) {
            id = pName + "." + id;
        }
        return id;
    }

    /**
     * Computes the base identity given the method name and the number of arguments to the method.
     *
     * @param elemName      the method name
     * @param methodArgCount the number of arguments to the method
     * @param access        the method's access
     * @param packageName   the method's enclosing package name
     * @return the base identity (relative to the owning service type)
     */
    public static String toMethodBaseIdentity(String elemName,
                                              int methodArgCount,
                                              Access access,
                                              Supplier<String> packageName) {
        String id = Objects.requireNonNull(elemName) + "|" + methodArgCount;
        if (Access.PACKAGE_PRIVATE == access || elemName.equals(CTOR)) {
            String pName = Objects.isNull(packageName) ? null : packageName.get();
            if (Objects.nonNull(pName)) {
                id = pName + "." + id;
            }
        }
        return id;
    }

    /**
     * Computes the method's unique identity, taking into consideration the number of args it accepts
     * plus any optionally provided specific argument offset position.
     *
     * @param elemName      the method name
     * @param methodArgCount the number of arguments to the method
     * @param elemOffset    the optional parameter offset
     * @param access        the access for the method
     * @param packageName   the package name of the owning service type containing the method
     * @return the unique identity (relative to the owning service type)
     */
    public static String toMethodIdentity(String elemName,
                                          int methodArgCount,
                                          Integer elemOffset,
                                          Access access,
                                          Supplier<String> packageName) {
        String result = toMethodBaseIdentity(elemName, methodArgCount, access, packageName);

        if (Objects.isNull(elemOffset)) {
            return result;
        }

        assert (elemOffset <= methodArgCount) : result;
        return result + "(" + elemOffset + ")";
    }

    /**
     * Builder for {@link io.helidon.pico.spi.impl.DefaultInjectionPointInfo}.
     *
     * @param <B> the builder type
     * @param <C> the concrete type being build
     */
    @SuppressWarnings("unchecked")
    public abstract static class DefaultInjectionPointInfoBuilder
            <C extends DefaultInjectionPointInfo, B extends DefaultInjectionPointInfoBuilder<C, B>>
            extends DefaultElementInfoBuilder<C, B> {
        private boolean listWrapped;
        private boolean optionalWrapped;
        private boolean providerWrapped;

        protected DefaultInjectionPointInfoBuilder() {
        }

        protected DefaultInjectionPointInfoBuilder(C c) {
            super(c);
            this.listWrapped = c.listWrapped();
            this.optionalWrapped = c.optionalWrapped();
            this.providerWrapped = c.providerWrapped();
        }

        /**
         * Builds the {@link io.helidon.pico.spi.impl.DefaultElementInfo}.
         *
         * @return the fluent builder instance
         */
        public C build() {
            return (C) new DefaultInjectionPointInfo(this);
        }

        /**
         * Indicates whether this injection point [aka IP] is a {@link java.util.List} type.
         *
         * @param listWrapped true if the IP is list type.
         * @return this fluent builder
         */
        public B listWrapped(boolean listWrapped) {
            this.listWrapped = listWrapped;
            return (B) this;
        }

        /**
         * Indicates whether this injection point [aka IP] is a {@link java.util.Optional} type.
         *
         * @param optionalWrapped true if the IP is optional type.
         * @return this fluent builder
         */
        public B optionalWrapped(boolean optionalWrapped) {
            this.optionalWrapped = optionalWrapped;
            return (B) this;
        }

        /**
         * Indicates whether this injection point [aka IP] is a {@link jakarta.inject.Provider} type.
         *
         * @param providerWrapped true if the IP is provider type.
         * @return this fluent builder
         */
        public B providerWrapped(boolean providerWrapped) {
            this.providerWrapped = providerWrapped;
            return (B) this;
        }
    }

}
