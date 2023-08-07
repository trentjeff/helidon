CDI integration with Helidon Injection
---

#Introduction

Goals of this integration:

- Allow invocation of Helidon Injection based services/beans on CDI beans

Non-goals:

- Injection of CDI beans into Helidon Services/beans
- Support for request or session scope beans, etc.
- Support for CDI based interceptors in conjunction with Helidon Injection based interceptors

#Design

What I need to do
 - find all services handled by Helidon Injection
 - add producers for Helidon Injection based services/beans

#Usage

The following must be done to use this integration:

## Annotation processor configuration

The following snippet shows compile configuration with annotation processors for maven
 that enables use of Helidon Injection.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <forceJavacCompilerUse>true</forceJavacCompilerUse>
        <compilerArgs>
            <arg>-Ainject.autoAddNonContractInterfaces=true</arg>
            <arg>-Ainject.ignoreUnsupportedAnnotations=true</arg>
        </compilerArgs>
        <annotationProcessorPaths>
            <path>
                <groupId>io.helidon.inject.configdriven</groupId>
                <artifactId>helidon-inject-configdriven-processor</artifactId>
                <version>${helidon.version}</version>
            </path>
            <path>
                <groupId>io.helidon.builder</groupId>
                <artifactId>helidon-builder-processor</artifactId>
                <version>${helidon.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```
