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

package io.helidon.pico.tools.processor;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

import javax.lang.model.element.TypeElement;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.pico.types.TypeName;

/**
 * Conditionally based upon classpath, will handle javax.inject and javax.annotation.  If class support not on the classpath
 * then there will be no service implementations available with this.
 */
public interface JavaxTypeTools {

    /**
     * Will be non-null if the classpath supports the ability to resolve javax annotations
     */
    LazyValue<JavaxTypeTools> INSTANCE = LazyValue.create(() -> {
       try {
           return HelidonServiceLoader.create(ServiceLoader.load(JavaxTypeTools.class,
                                                                 JavaxTypeTools.class.getClassLoader())).iterator().next();
       } catch (Throwable t) {
           return null;
       }
    });

    /**
     * Attempts to fetch javax.annotation.Priority.
     *
     * @param serviceTypeName the enclosing service type name
     * @param type the element to be inspected
     * @return the priority, or null of it doesn't exist
     */
    default Integer getPriority(TypeName serviceTypeName, TypeElement type) {
        return null;
    }

    /**
     * Attempts to fetch javax annotation types, and then converts them to jakarta annotation equivalent names.
     *
     * @param type the element to be inspected
     * @param annotationTypeName javax annotation name to attempt to resolve.
     * @return empty collection if not resolvable, else the jakarta converted set of annotation type names
     */
    default List<String> getAnnotationsWithAnnotation(TypeElement type, String annotationTypeName) {
        return Collections.emptyList();
    }

    /**
     * Attempts to load the javax annotation type.
     *
     * @param annotationTypeName javax annotation name to attempt to resolve.
     * @return the class type if resolvable, else null
     */
    default Class<? extends Annotation> loadAnnotationClass(String annotationTypeName) {
        return null;
    }

}
