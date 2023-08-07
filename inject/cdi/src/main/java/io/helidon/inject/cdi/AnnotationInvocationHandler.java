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

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;

class AnnotationInvocationHandler implements InvocationHandler, Serializable {
    private final io.helidon.common.types.Annotation hannotation;
    private final Class<? extends Annotation> type;
    private final Map<String, String> memberValues;

    AnnotationInvocationHandler(io.helidon.common.types.Annotation hannotation,
                                Class<? extends Annotation> type,
                                Map<String, String> memberValues) {
        this.hannotation = hannotation;
        this.type = type;
        this.memberValues = memberValues;
    }

    @Override
    public Object invoke(Object proxy,
                         Method method,
                         Object[] args) {
        String member = method.getName();
        int parameterCount = method.getParameterCount();

        // Handle Object and Annotation methods
        if (parameterCount == 1
                && member.equals("equals")
                && method.getParameterTypes()[0] == Object.class) {
            return equalsImpl(proxy, args[0]);
        }
        if (parameterCount != 0) {
            throw new AssertionError("Too many parameters for an annotation method");
        }

        if (member.equals("toString")) {
            return hannotation.toString();
        } else if (member.equals("hashCode")) {
            return hashCodeImpl();
        } else if (member.equals("annotationType")) {
            return type;
        }

        // Handle annotation member accessors
        Object result = memberValues.get(member);
        if (result == null) {
            throw new IncompleteAnnotationException(type, member);
        }

        if (result.getClass().isArray()
                && Array.getLength(result) != 0) {
            result = cloneArray(result);
        }

        return result;
    }

    /**
     * This method, which clones its array argument, would not be necessary
     * if Cloneable had a public clone method.
     */
    private Object cloneArray(Object array) {
        Class<?> type = array.getClass();

        if (type == byte[].class) {
            byte[] byteArray = (byte[]) array;
            return byteArray.clone();
        }
        if (type == char[].class) {
            char[] charArray = (char[]) array;
            return charArray.clone();
        }
        if (type == double[].class) {
            double[] doubleArray = (double[]) array;
            return doubleArray.clone();
        }
        if (type == float[].class) {
            float[] floatArray = (float[]) array;
            return floatArray.clone();
        }
        if (type == int[].class) {
            int[] intArray = (int[]) array;
            return intArray.clone();
        }
        if (type == long[].class) {
            long[] longArray = (long[]) array;
            return longArray.clone();
        }
        if (type == short[].class) {
            short[] shortArray = (short[]) array;
            return shortArray.clone();
        }
        if (type == boolean[].class) {
            boolean[] booleanArray = (boolean[]) array;
            return booleanArray.clone();
        }

        Object[] objectArray = (Object[]) array;
        return objectArray.clone();
    }

    /**
     * Implementation of dynamicProxy.equals(Object o)
     */
    private Boolean equalsImpl(Object proxy,
                               Object o) {
        if (o == proxy) {
            return true;
        }

        if (!type.isInstance(o)) {
            return false;
        }

        for (Method memberMethod : getMemberMethods()) {
            if (memberMethod.isSynthetic())
                continue;
            String member = memberMethod.getName();
            Object ourValue = memberValues.get(member);
            Object hisValue;
            AnnotationInvocationHandler hisHandler = asOneOfUs(o);
            if (hisHandler != null) {
                hisValue = hisHandler.memberValues.get(member);
            } else {
                try {
                    hisValue = memberMethod.invoke(o);
                } catch (InvocationTargetException e) {
                    return false;
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }

            if (!memberValueEquals(ourValue, hisValue)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns an object's invocation handler if that object is a dynamic
     * proxy with a handler of type AnnotationInvocationHandler.
     * Returns null otherwise.
     */
    private AnnotationInvocationHandler asOneOfUs(Object o) {
        if (Proxy.isProxyClass(o.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(o);
            if (handler instanceof AnnotationInvocationHandler annotationHandler) {
                return annotationHandler;
            }
        }
        return null;
    }

    /**
     * Returns true iff the two member values in "dynamic proxy return form"
     * are equal using the appropriate equality function depending on the
     * member type.  The two values will be of the same type unless one of
     * the containing annotations is ill-formed.  If one of the containing
     * annotations is ill-formed, this method will return false unless the
     * two members are identical object references.
     */
    private static boolean memberValueEquals(Object v1,
                                             Object v2) {
        Class<?> type = v1.getClass();

        // Check for primitive, string, class, enum const, annotation, or ExceptionProxy
        if (!type.isArray()) {
            return v1.equals(v2);
        }

        // Check for array of string, class, enum const, annotation, or ExceptionProxy
        if (v1 instanceof Object[] && v2 instanceof Object[]) {
            return Arrays.equals((Object[]) v1, (Object[]) v2);
        }

        // Check for ill formed annotation(s)
        if (v2.getClass() != type) {
            return false;
        }

        // Deal with array of primitives
        if (type == byte[].class) {
            return Arrays.equals((byte[]) v1, (byte[]) v2);
        }
        if (type == char[].class) {
            return Arrays.equals((char[]) v1, (char[]) v2);
        }
        if (type == double[].class) {
            return Arrays.equals((double[]) v1, (double[]) v2);
        }
        if (type == float[].class) {
            return Arrays.equals((float[]) v1, (float[]) v2);
        }
        if (type == int[].class) {
            return Arrays.equals((int[]) v1, (int[]) v2);
        }
        if (type == long[].class) {
            return Arrays.equals((long[]) v1, (long[]) v2);
        }
        if (type == short[].class) {
            return Arrays.equals((short[]) v1, (short[]) v2);
        }
        assert (type == boolean[].class);
        return Arrays.equals((boolean[]) v1, (boolean[]) v2);
    }

    private Method[] getMemberMethods() {
        return type.getDeclaredMethods();
    }

    /**
     * Implementation of dynamicProxy.hashCode()
     */
    private int hashCodeImpl() {
        int result = 0;
        for (Map.Entry<String, String> e : memberValues.entrySet()) {
            result += (127 * e.getKey().hashCode()) ^ memberValueHashCode(e.getValue());
        }
        return result;
    }

    /**
     * Computes hashCode of a member value (in "dynamic proxy return form")
     */
    private static int memberValueHashCode(Object value) {
        Class<?> type = value.getClass();
        if (!type.isArray())    // primitive, string, class, enum const, or annotation
            return value.hashCode();

        if (type == byte[].class) {
            return Arrays.hashCode((byte[]) value);
        }
        if (type == char[].class) {
            return Arrays.hashCode((char[]) value);
        }
        if (type == double[].class) {
            return Arrays.hashCode((double[]) value);
        }
        if (type == float[].class) {
            return Arrays.hashCode((float[]) value);
        }
        if (type == int[].class) {
            return Arrays.hashCode((int[]) value);
        }
        if (type == long[].class) {
            return Arrays.hashCode((long[]) value);
        }
        if (type == short[].class) {
            return Arrays.hashCode((short[]) value);
        }
        if (type == boolean[].class) {
            return Arrays.hashCode((boolean[]) value);
        }
        return Arrays.hashCode((Object[]) value);
    }

}
