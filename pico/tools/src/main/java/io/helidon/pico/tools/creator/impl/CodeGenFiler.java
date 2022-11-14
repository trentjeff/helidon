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

package io.helidon.pico.tools.creator.impl;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.creator.CodeGenPaths;
import io.helidon.pico.tools.creator.ModuleDetail;
import io.helidon.pico.tools.types.SimpleModuleDescriptor;
import io.helidon.pico.tools.utils.ModuleUtils;
import io.helidon.pico.types.TypeName;

/**
 * This class is used to generate source and resources originating from either annotation processing or maven-plugin
 * invocation. It also provides a circuit breaker in case the filer should be disabled from actually writing out source
 * and resources, and instead will use the filer's messager to report what it would have performed.
 */
public class CodeGenFiler {
    private static boolean filerWriteIsEnabled = true;
    private static final boolean FILER_WRITE_ONCE_PER_TYPE = true;
    private static final Set<TypeName> FILER_TYPES_FILED = new LinkedHashSet<>();

    private final AbstractFilerMsgr filer;
    private final Boolean enabled;

    /**
     * Ctor.
     *
     * @param filer the filer to use for creating resources.
     */
    public CodeGenFiler(AbstractFilerMsgr filer) {
        this(filer, null);
    }

    /**
     * Ctor.
     *
     * @param filer the filer to use for creating resources.
     * @param enabled true if forcing enablement, false if forcing disablement, null for using defaults
     */
    public CodeGenFiler(AbstractFilerMsgr filer, Boolean enabled) {
        this.filer = Objects.requireNonNull(filer);
        this.enabled = enabled;
    }

    /**
     * Provides the ability to disable actual file writing (convenient for unit testing). The default is true for
     * enabled.
     *
     * @param enabled if disabled, pass false.
     * @return the previous value of this setting
     */
    public static boolean setFilerEnabled(boolean enabled) {
        boolean prev = filerWriteIsEnabled;
        filerWriteIsEnabled = enabled;
        return prev;
    }

    protected boolean isFilerWriteEnabled() {
        return Objects.nonNull(enabled) ? enabled : filerWriteIsEnabled;
    }

    protected AbstractFilerMsgr getFiler() {
        return filer;
    }

    protected Msgr getMessager() {
        return filer;
    }

    /**
     * Generate the meta-inf services given the provided map.
     *
     * @param paths           paths to where code should be written.
     * @param metaInfServices the meta-inf services mapping
     */
    public void codegenMetaInfServices(CodeGenPaths paths, Map<String, List<String>> metaInfServices) {
        if (Objects.isNull(metaInfServices) || metaInfServices.isEmpty()) {
            return;
        }

        Filer filer = getFiler();
        Msgr messager = getMessager();
        Map<String, Set<String>> mergedMap = new LinkedHashMap<>();
        // load up any existing values, since this compilation may be partial and be run again...
        for (Map.Entry<String, List<String>> e : metaInfServices.entrySet()) {
            String contract = e.getKey();
            Set<String> mergedSet = new LinkedHashSet<>(e.getValue());
            mergedMap.put(contract, mergedSet);
            String outPath = new File(paths.getMetaInfServicesPath(), contract).getPath();
            try {
                messager.debug("Reading " + outPath);

                FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", outPath);
                try (InputStream is = f.openInputStream();
                        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        mergedSet.add(line);
                    }
                }
            } catch (FilerException | NoSuchFileException x) {
                // don't show the exception in this case
                messager.debug(getClass().getSimpleName() + ":" + x.getMessage(), null);
            } catch (Exception x) {
                ToolsException te =
                        new ToolsException("Failed to find/load existing META-INF/services file: " + x.getMessage(), x);
                messager.warn(te.getMessage(), te);
            }
        }

        for (Map.Entry<String, Set<String>> e : mergedMap.entrySet()) {
            String contract = e.getKey();
            String outPath = new File(paths.getMetaInfServicesPath(), contract).getPath();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
                for (String value : e.getValue()) {
                    pw.println(value);
                }
            }

            codegenResourceFilerOut(outPath, baos.toString(), null);
        }
    }

    /**
     * Code generates a resource, providing the ability to update if the resource already exists.
     *
     * @param outPath   the path to output the resource to
     * @param body      the resource body
     * @param fnUpdater the optional updater of the body
     * @return file coordinates corresponding to the resource in question
     */
    public File codegenResourceFilerOut(String outPath, String body, Function<InputStream, String> fnUpdater) {
        Msgr messager = getMessager();
        if (!isFilerWriteEnabled()) {
            messager.log("(disabled) Writing " + outPath + " with:\n" + body);
            return null;
        }
        messager.debug("Writing " + outPath);

        Filer filer = getFiler();
        boolean contentsAlreadyVerified = false;
        AtomicReference<File> fileRef = new AtomicReference<>();
        try {
            if (Objects.nonNull(fnUpdater)) {
                // attempt to update it...
                try {
                    FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", outPath);
                    try (InputStream is = f.openInputStream()) {
                        String newBody = fnUpdater.apply(is);
                        if (Objects.nonNull(newBody)) {
                            body = newBody;
                        }
                    }
                } catch (NoSuchFileException e) {
                    // no-op
                } catch (Exception e) {
                    //                    messager.debug(getClass().getSimpleName() + ":" + e.getMessage());
                    contentsAlreadyVerified = tryToEnsureSameContents(e, body, messager, fileRef);
                }
            }

            // write it...
            FileObject f = filer.createResource(StandardLocation.CLASS_OUTPUT, "", outPath);
            try (Writer os = f.openWriter()) {
                os.write(body);
            }

            return ModuleUtils.toFile(f.toUri());
        } catch (FilerException x) {
            //            messager.debug(getClass().getSimpleName() + ":" + x.getMessage(), null);
            if (!contentsAlreadyVerified) {
                tryToEnsureSameContents(x, body, messager, fileRef);
            }
        } catch (Exception x) {
            ToolsException te = new ToolsException("Failed to write resource file: " + x.getMessage(), x);
            messager.error(te.getMessage(), te);
        }

        return fileRef.get();
    }

    /**
     * Throws an error if the contents being written cannot be written, and the desired content is different from what
     * is on disk.
     *
     * @param e        the exception thrown by the filer
     * @param expected the expected body of the resource
     * @param messager the messager to handle errors and logging
     * @param fileRef  the reference that will be set to the coordinates of the resource
     * @return true if the implementation was able to verify the contents match
     */
    protected boolean tryToEnsureSameContents(Exception e,
                                              String expected,
                                              Msgr messager,
                                              AtomicReference<File> fileRef) {
        if (!(e instanceof FilerException)) {
            return false;
        }

        String message = e.getMessage();
        if (Objects.isNull(message)) {
            return false;
        }

        int pos = message.lastIndexOf(' ');
        if (pos <= 0) {
            return false;
        }

        String maybePath = message.substring(pos + 1);
        File file = new File(maybePath);
        if (!file.exists()) {
            return false;
        }
        if (Objects.nonNull(fileRef)) {
            fileRef.set(file);
        }

        try {
            String actual = Files.readString(file.toPath(), Charset.defaultCharset());
            if (!actual.equals(expected)) {
                String error = "expected contents to match for file: " + file
                        + "\nexpected:\n" + expected
                        + "\nactual:\n" + actual;
                ToolsException te = new ToolsException(error);
                messager.error(error, te);
            }
        } catch (Exception x) {
            messager.debug(getClass().getSimpleName() + ": unable to verify contents match: " + file + "; " + x.getMessage(),
                           null);
            return false;
        }

        return true;
    }

    /**
     * Code generates the {@link java.lang.Module} source.
     *
     * @param moduleDetail the module details
     */
    public void codegenModuleFilerOut(ModuleDetail moduleDetail) {
        if (Objects.isNull(moduleDetail) || Objects.isNull(moduleDetail.getModuleBody())) {
            return;
        }

        TypeName typeName = moduleDetail.getModuleTypeName();
        String body = Objects.requireNonNull(moduleDetail.getModuleBody());
        codegenJavaFilerOut(typeName, body);
    }

    /**
     * Code generates the {@link io.helidon.pico.Application} source.
     *
     * @param applicationTypeName the application type
     * @param body                the application body of source
     */
    public void codegenApplicationFilerOut(TypeName applicationTypeName, String body) {
        codegenJavaFilerOut(applicationTypeName, body);
    }

    /**
     * Code generates the {@link io.helidon.pico.Activator} source.
     *
     * @param activatorDetail the activator details
     */
    public void codegenActivatorFilerOut(DefaultActivatorCodeGenDetail activatorDetail) {
        if (Objects.isNull(activatorDetail) || Objects.isNull(activatorDetail.getBody())) {
            return;
        }

        TypeName typeName = activatorDetail.getServiceTypeName();
        String body = Objects.requireNonNull(activatorDetail.getBody());
        codegenJavaFilerOut(typeName, body);
    }

    /**
     * Code generate a java source file.
     *
     * @param typeName the source type name
     * @param body     the source body
     * @return the new file coordinates or null if nothing was written
     */
    public File codegenJavaFilerOut(TypeName typeName, String body) {
        Msgr messager = getMessager();
        if (!isFilerWriteEnabled()) {
            messager.log("(disabled) Writing " + typeName + " with:\n" + body);
            return null;
        }

        if (FILER_WRITE_ONCE_PER_TYPE && !FILER_TYPES_FILED.add(typeName)) {
            messager.log(typeName + ": already processed");
            return null;
        }

        messager.debug("Writing " + typeName);

        Filer filer = getFiler();
        try {
            JavaFileObject javaSrc = filer.createSourceFile(typeName.name());
            try (Writer os = javaSrc.openWriter()) {
                os.write(body);
            }

            return ModuleUtils.toFile(javaSrc.toUri());
        } catch (FilerException x) {
            messager.log("Failed to write java file: " + x);
        } catch (Exception x) {
            messager.warn("Failed to write java file: " + x, x);
        }

        return null;
    }

    /**
     * Code generate the module-info descriptor.
     *
     * @param newDeltaDescriptor      the descriptor
     * @param overwriteTargetIfExists should the file be overwritten if it already exists
     * @return the module-info coordinates, or null if nothing was written
     */
    public File codegenModuleInfoFilerOut(SimpleModuleDescriptor newDeltaDescriptor,
                                          boolean overwriteTargetIfExists) {
        if (Objects.isNull(newDeltaDescriptor)) {
            return null;
        }

        Msgr messager = getMessager();
        String typeName = ModuleUtils.PICO_MODULE_INFO_JAVA_NAME;
        if (!isFilerWriteEnabled()) {
            messager.log("(disabled) Writing " + typeName + " with:\n" + newDeltaDescriptor);
            return null;
        }
        messager.debug("Writing " + typeName);

        Function<InputStream, String> moduleInfoUpdater = inputStream -> {
            SimpleModuleDescriptor existingDescriptor = SimpleModuleDescriptor.uncheckedLoad(inputStream);
            existingDescriptor.merge(newDeltaDescriptor);
            return existingDescriptor.getContents();
        };
        File file = codegenResourceFilerOut(typeName,
                                            newDeltaDescriptor.getContents(), moduleInfoUpdater);
        if (Objects.nonNull(file)) {
            messager.debug("Wrote module-info: " + file);
        } else if (overwriteTargetIfExists) {
            messager.warn("Expected to have written module-info, but failed to write it", null);
        }
        return file;
    }

    /**
     * Reads in the module-info if it exists, or returns null if it doesn't exist.
     *
     * @param name the name to the module-info file.
     * @return the module-info descriptor, or null if it doesn't exist
     */
    public SimpleModuleDescriptor readModuleInfo(String name) {
        if (Objects.isNull(name)) {
            return null;
        }

        CharSequence body = readResourceAsString(name);
        return Objects.isNull(body) ? null : SimpleModuleDescriptor.uncheckedLoad(body.toString());
    }

    /**
     * Reads in a resource from the {@link javax.tools.StandardLocation#CLASS_OUTPUT} location.
     *
     * @param name the name of the resource
     * @return the body of the resource as a string, or null if it doesn't exist
     */
    public CharSequence readResourceAsString(String name) {
        try {
            FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", name);
            return f.getCharContent(true);
        } catch (IOException e) {
            getMessager().debug("unable to load resource: " + name);
            return null;
        }
    }

    /**
     * Attempts to translate the resource name to a file coordinate, or null if translation is not possible.
     *
     * @param name the name of the resource
     * @return the file coordinates if it can be ascertained, or null if not possible to ascertain this information
     */
    public File toResourceLocation(String name) {
        try {
            FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", name);
            return ModuleUtils.toFile(f.toUri());
        } catch (IOException e) {
            getMessager().debug("unable to load resource: " + name);
        }
        return null;
    }

    /**
     * Attempts to translate the type name to a file coordinate, or null if translation is not possible.
     *
     * @param name the name of the type
     * @return the file coordinates if it can be ascertained, or null if not possible to ascertain this information
     *
     * @see io.helidon.pico.tools.utils.ModuleUtils#toSourcePath(java.io.File, javax.lang.model.element.TypeElement) for annotation processing use cases
     */
    public File toSourceLocation(String name) {
        if (filer instanceof AbstractFilerMsgr.DirectFilerMsgr) {
            TypeName typeName = DefaultTypeName.createFromTypeName(name);
            return ((AbstractFilerMsgr.DirectFilerMsgr) filer).toSourcePath(StandardLocation.SOURCE_PATH, typeName);
        }

        getMessager().debug("unable to determine source location for : " + name);
        return null;
    }

}
