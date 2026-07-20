package org.dempsay.aether.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import org.dempsay.aether.validation.ValidationException;
import org.dempsay.utils.exceptional.api.ExceptionalResource;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
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

        final String source = readFile(builderFile).response();
        assertTrue(source.contains("public final class MyDtoBuilder implements AetherBuilder<MyDto>"));
        assertTrue(source.contains("public String data()"));
        assertTrue(source.contains("@Override"));
        assertTrue(source.contains("Field 'data' must not be null"));
        assertTrue(source.contains("length must be between 3 and 50"));
        assertTrue(source.contains("private static final Pattern DATA_PATTERN = Pattern.compile"));
        assertTrue(source.contains("DATA_PATTERN.matcher(data).matches()"));
    }

    @Test
    public void generatesResourceStoreInterfaceForMarkedRecord() throws Exception {
        compileFixtures("MyDto.java");

        final File storeFile = outputDir.resolve("fixtures/MyDtoStore.java").toFile();
        assertTrue(storeFile.exists(), "Store interface should be generated");

        final String source = readFile(storeFile).response();
        assertTrue(source.contains("import org.dempsay.aether.store.api.AetherResourceStore;"));
        assertTrue(source.contains("public interface MyDtoStore extends AetherResourceStore<MyDto>"));
        assertFalse(source.contains("AetherSingletonStore"));
    }

    @Test
    public void generatesSingletonStoreInterfaceWhenAnnotated() throws Exception {
        compileFixtures("ConfigDto.java");

        final File storeFile = outputDir.resolve("fixtures/ConfigDtoStore.java").toFile();
        assertTrue(storeFile.exists(), "Singleton store interface should be generated");

        final String source = readFile(storeFile).response();
        assertTrue(source.contains("import org.dempsay.aether.store.api.AetherSingletonStore;"));
        assertTrue(source.contains("public interface ConfigDtoStore extends AetherSingletonStore<ConfigDto>"));
        assertFalse(source.contains("AetherResourceStore"));
        assertTrue(outputDir.resolve("fixtures/ConfigDtoBuilder.java").toFile().exists());
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

        final String source = readFile(builderFile).response();
        assertTrue(source.contains("implements AetherBuilder<NamedDto>, Named"));
        assertTrue(source.contains("public String name()"));
        assertTrue(source.contains("collectValidationErrors()"));
        assertFalse(source.contains("buildAsNamed"));
    }

    @Test
    public void builderImplementsRecordInterfaces() throws Exception {
        compileAndLoad("Named.java", "NamedDto.java");

        final ClassLoader loader = testClassLoader();
        final Class<?> namedInterface = Class.forName("fixtures.Named", true, loader);
        final Class<?> builderClass = Class.forName("fixtures.NamedDtoBuilder", true, loader);

        assertTrue(namedInterface.isAssignableFrom(builderClass));

        final Object builder = builderClass.getConstructor().newInstance();
        assertNull(builderClass.getMethod("name").invoke(builder));

        builderClass.getMethod("name", String.class).invoke(builder, "alice");
        assertEquals("alice", builderClass.getMethod("name").invoke(builder));
        assertEquals("alice", namedInterface.getMethod("name").invoke(builder));
    }

    @Test
    public void escapesRegexPatternForGeneratedJava() throws Exception {
        compileFixtures("RegexDto.java");

        final String source = readFile(outputDir.resolve("fixtures/RegexDtoBuilder.java").toFile()).response();
        assertTrue(source.contains("import java.util.regex.Pattern;"), "Regex builders must import Pattern");
        assertTrue(
                source.contains("private static final Pattern CODE_PATTERN = Pattern.compile(\"\\\\d{3}\")"),
                "Regex pattern should be escaped for a valid Java string literal");
        assertTrue(source.contains("CODE_PATTERN.matcher(code).matches()"));
    }

    @Test
    public void validatesRegexAtRuntime() throws Exception {
        compileAndLoad("RegexDto.java");

        final Class<?> builderClass = Class.forName("fixtures.RegexDtoBuilder", true, testClassLoader());
        final Object builder = builderClass.getConstructor().newInstance();
        final AtomicReference<Exception> captured = new AtomicReference<>();

        builderClass.getMethod("code", String.class).invoke(builder, "12");
        assertTrue(wasError(invokeBuild(builder, captured)), "Too few digits should fail regex");

        builderClass.getMethod("code", String.class).invoke(builder, "123");
        captured.set(null);
        final Object success = invokeBuild(builder, captured);
        assertFalse(wasError(success));
        final Object dto = success.getClass().getMethod("response").invoke(success);
        assertEquals("123", dto.getClass().getMethod("code").invoke(dto));
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

    /**
     * Generated sources must always satisfy dempsay checkstyle conventions — even when
     * a consumer excludes {@code target/generated-sources} from their scan.
     */
    @Test
    public void generatedSourcesMeetCheckstyleConventions() throws Exception {
        compileFixtures(
                "MyDto.java",
                "SafeDto.java",
                "RegexDto.java",
                "ConfigDto.java",
                "Named.java",
                "NamedDto.java");

        final List<Path> generated = Files.walk(outputDir)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> {
                    final String name = p.getFileName().toString();
                    return name.endsWith("Builder.java") || name.endsWith("Store.java");
                })
                .toList();
        assertFalse(generated.isEmpty(), "Expected generated Builder/Store sources");

        for (final Path path : generated) {
            final String source = Files.readString(path);
            final String file = path.getFileName().toString();
            assertFalse(
                    source.contains("() {}"),
                    file + ": empty constructor/method must use '{ }' not '{}' (WhitespaceAround)");
            // Method parameters must be final (dempsay FinalParameters).
            // Match: public/private returnType name(Type param) without final after '('.
            assertFalse(
                    source.matches(
                            "(?s).*(?:public|private)\\s+\\S+\\s+\\w+\\s*\\(\\s*"
                                    + "(?!final\\b)[A-Za-z0-9_.<>,\\[\\]\\s]+\\s+\\w+\\s*\\).*"),
                    file + ": method parameters must be final (FinalParameters)");
            if (source.contains("import java.") && source.contains("import org.")) {
                assertTrue(
                        source.contains("\n\nimport org."),
                        file + ": blank line required between java.* and org.* imports");
            }
            if (source.contains(".length()")) {
                assertTrue(
                        source.contains("final int "),
                        file + ": length locals should be final");
            }
        }
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
                "-processor", "org.dempsay.aether.processor.AetherBuilderProcessor",
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

    private static ExceptionalResponse<String> readFile(final File file) {
        return ExceptionalResource.of(
                () -> new BufferedReader(new FileReader(file)),
                reader -> reader.lines().collect(Collectors.joining(System.lineSeparator())))
                .execute();
    }
}
