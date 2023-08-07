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

package io.helidon.inject.cdi;

import java.util.Map;

import io.helidon.inject.api.ClassNamed;
import io.helidon.inject.api.Qualifier;
import io.helidon.inject.tools.TypeTools;

import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class AnnotationTest {

    @Test
    void classNamed() {
        Qualifier qualifier = Qualifier.create(ClassNamed.class, AnnotationTest.class.getName());
        java.lang.annotation.Annotation anno = AnnotationSupport.toAnnotation(qualifier);
        assertThat(anno.annotationType(), equalTo(Named.class));
        assertThat(TypeTools.extractValues(anno), equalTo(Map.of("value", AnnotationTest.class.getName())));

        Qualifier qualifier2 = Qualifier.create(ClassNamed.class, AnnotationTest.class.getName());
        java.lang.annotation.Annotation anno2 = AnnotationSupport.toAnnotation(qualifier2);
        assertThat(anno, equalTo(anno2));
    }

}
