package org.aether.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.aether.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestAetherBuilderProcessor {
    private static final String FIXTURES = "src/test/resources/fixtures";
    private Path outputDir;
    private String classpath;

    @BeforeEach
    public void setUp() throws Exception {
        outputDir = Files.createTempDirectory("aether-processor-test");
        classpath = buildClasspath();
    }

    @Test
    public void generatesBuilderForMarkedRecord() throws Exception {
        compileFixtures("MyDto.java");

        final File builderFile = outputDir.resolve("fixtures/MyDtoBuilder.java").toFile();
        assertTrue(builderFile.exists(), "Builder source should be generated");

        final String source = readFile(builderFile);
        assertTrue(source.contains("public final class MyDtoBuilder implements AetherBuilder<MyDto>"));
        assertTrue(source.contains("@Override"));
        assertTrue(source.contains("Field 'data' must not be null"));
        assertTrue(source.contains("length must be between 3 and 50"));
        assertTrue(source.contains("Pattern.matches"));
    }

    @Test
    public void doesNotGenerateBuilderForUnmarkedRecord() throws Exception {
        compileFixtures("PlainRecord.java");
        assertFalse(outputDir.resolve("fixtures/PlainRecordBuilder.java").toFile().exists());
    }

    @Test
    public void rejectsUnsupportedFieldTypes() {
        final DiagnosticCollector<JavaFileObject> diagnostics = compileFixturesWithDiagnostics("InvalidDto.java");
        assertTrue(diagnostics.getDiagnostics().stream()
                        .anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
                                && d.getMessage(null).contains("Unsupported field type")),
                "Expected compile error for unsupported type");
        assertFalse(outputDir.resolve("fixtures/InvalidDtoBuilder.java").toFile().exists());
    }

    @Test
    public void validatesNullRejectionAndSuccess() throws Exception {
        compileAndLoad("MyDto.java");

        final Class<?> builderClass = Class.forName("fixtures.MyDtoBuilder", true, testClassLoader());
        final Object builder = builderClass.getConstructor().newInstance();
        final AtomicReference<Exception> captured = new AtomicReference<>();

        final Object failure = invokeBuild(builder, captured);
        assertTrue(wasError(failure), "Null data should fail");
        assertTrue(captured.get() instanceof ValidationException);
        assertTrue(((ValidationException) captured.get()).getErrors().contains("Field 'data' must not be null"));

        builderClass.getMethod("data", String.class).invoke(builder, "ab");
        captured.set(null);
        final Object tooShort = invokeBuild(builder, captured);
        assertTrue(wasError(tooShort));

        builderClass.getMethod("data", String.class).invoke(builder, "valid_name");
        captured.set(null);
        final Object success = invokeBuild(builder, captured);
        assertFalse(wasError(success));
        final Object dto = success.getClass().getMethod("response").invoke(success);
        assertEquals("valid_name", dto.getClass().getMethod("data").invoke(dto));
    }

    @Test
    public void generatesBuilderForRecordImplementingInterface() throws Exception {
        compileFixtures("Named.java", "NamedDto.java");

        final File builderFile = outputDir.resolve("fixtures/NamedDtoBuilder.java").toFile();
        assertTrue(builderFile.exists(), "Builder source should be generated");

        final String source = readFile(builderFile);
        assertTrue(source.contains("implements AetherBuilder<NamedDto>"));
        assertTrue(source.contains("buildAsNamed(ExceptionalListener onError)"));
        assertTrue(source.contains("ExceptionalResponse<Named>"));
    }

    @Test
    public void buildAsInterfaceViewReturnsTypedResponse() throws Exception {
        compileAndLoad("Named.java", "NamedDto.java");

        final ClassLoader loader = testClassLoader();
        final Class<?> builderClass = Class.forName("fixtures.NamedDtoBuilder", true, loader);
        final Object builder = builderClass.getConstructor().newInstance();
        builderClass.getMethod("name", String.class).invoke(builder, "alice");

        final AtomicReference<Exception> captured = new AtomicReference<>();
        final Object listener = (org.dempsay.utils.exceptional.api.ExceptionalListener) captured::set;
        final Object response = builderClass
                .getMethod("buildAsNamed", org.dempsay.utils.exceptional.api.ExceptionalListener.class)
                .invoke(builder, listener);

        assertFalse(wasError(response));
        final Object named = response.getClass().getMethod("response").invoke(response);
        assertTrue(Class.forName("fixtures.Named", true, loader).isInstance(named));
        assertEquals("alice", named.getClass().getMethod("name").invoke(named));
    }

    @Test
    public void allowsNullableNull() throws Exception {
        compileAndLoad("SafeDto.java");

        final Class<?> builderClass = Class.forName("fixtures.SafeDtoBuilder", true, testClassLoader());
        final Object builder = builderClass.getConstructor().newInstance();
        final AtomicReference<Exception> captured = new AtomicReference<>();

        final Object success = invokeBuild(builder, captured);
        assertFalse(wasError(success));
        assertFalse(captured.get() != null);
    }

    private void compileFixtures(final String... fixtureNames) {
        final DiagnosticCollector<JavaFileObject> diagnostics = compileFixturesWithDiagnostics(fixtureNames);
        final String errors = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null))
                .collect(Collectors.joining(System.lineSeparator()));
        assertTrue(errors.isEmpty(), "Compilation failed: " + errors);
    }

    private DiagnosticCollector<JavaFileObject> compileFixturesWithDiagnostics(final String... fixtureNames) {
        final List<File> sources = new ArrayList<>();
        for (String fixtureName : fixtureNames) {
            sources.add(new File(FIXTURES, fixtureName));
        }

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        final StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        final List<String> options = List.of(
                "-cp", classpath,
                "-d", outputDir.toString(),
                "-processor", "org.aether.processor.AetherBuilderProcessor",
                "--release", "21");

        final CompilationTask task = compiler.getTask(
                new StringWriter(),
                fileManager,
                diagnostics,
                options,
                List.of(),
                fileManager.getJavaFileObjectsFromFiles(sources));

        task.call();
        return diagnostics;
    }

    private void compileAndLoad(final String... fixtureNames) {
        compileFixtures(fixtureNames);
    }

    private Object invokeBuild(final Object builder, final AtomicReference<Exception> captured) throws Exception {
        final Object listener = (org.dempsay.utils.exceptional.api.ExceptionalListener) captured::set;
        return builder.getClass().getMethod("build", org.dempsay.utils.exceptional.api.ExceptionalListener.class)
                .invoke(builder, listener);
    }

    private boolean wasError(final Object response) throws Exception {
        return (boolean) response.getClass().getMethod("wasError").invoke(response);
    }

    private ClassLoader testClassLoader() throws Exception {
        final List<URL> urls = new ArrayList<>();
        urls.add(outputDir.toUri().toURL());
        for (String entry : classpath.split(File.pathSeparator)) {
            urls.add(new File(entry).toURI().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader());
    }

    private String buildClasspath() throws Exception {
        final List<String> entries = new ArrayList<>();
        entries.add(new File("target/classes").getAbsolutePath());
        entries.add(new File("../aether-api/target/classes").getAbsolutePath());
        entries.add(resolveDependency("org.dempsay.utils", "exceptional", "1.0.9"));
        entries.add(resolveDependency("org.dempsay.support.jsr269", "jsr269-utilities", "1.0.1"));
        return String.join(File.pathSeparator, entries);
    }

    private String resolveDependency(final String groupId, final String artifactId, final String version) {
        final String repoPath = System.getProperty("user.home")
                + "/.m2/repository/"
                + groupId.replace('.', '/')
                + "/"
                + artifactId
                + "/"
                + version
                + "/"
                + artifactId
                + "-"
                + version
                + ".jar";
        return new File(repoPath).getAbsolutePath();
    }

    private static String readFile(final File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }
}