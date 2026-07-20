package org.dempsay.aether.store.gen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.dempsay.utils.exceptional.api.ExceptionalResource;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration-style tests for {@link AetherStoreProvidersProcessor}.
 */
class TestAetherStoreProvidersProcessor {
    private static final String FIXTURES = "src/test/resources/fixtures";

    private Path outputDir;
    private String classpath;

    @BeforeEach
    void setUp() throws Exception {
        outputDir = Files.createTempDirectory("aether-store-gen-test");
        classpath = buildClasspath();
    }

    @Test
    void generatesFsMemoryAndSingletonAdapters() throws Exception {
        compileFixtures(
                "MyDto.java",
                "MyDtoStore.java",
                "ConfigDto.java",
                "ConfigDtoStore.java",
                "server/package-info.java");

        final Path fsMy = outputDir.resolve("fixtures/server/FsMyDtoStore.java");
        final Path memMy = outputDir.resolve("fixtures/server/MemoryMyDtoStore.java");
        final Path fsConfig = outputDir.resolve("fixtures/server/FsConfigDtoStore.java");

        assertTrue(Files.exists(fsMy), "FsMyDtoStore should be generated");
        assertTrue(Files.exists(memMy), "MemoryMyDtoStore should be generated");
        assertTrue(Files.exists(fsConfig), "FsConfigDtoStore should be generated");

        final String fsSource = readFile(fsMy.toFile()).response();
        assertTrue(fsSource.contains("package fixtures.server;"));
        assertTrue(fsSource.contains("extends FileSystemAetherResourceStore<MyDto>"));
        assertTrue(fsSource.contains("implements MyDtoStore"));
        assertTrue(fsSource.contains("super(root, MyDto.class)"));
        assertTrue(fsSource.contains("public FsMyDtoStore(final Path root)"));

        final String memSource = readFile(memMy.toFile()).response();
        assertTrue(memSource.contains("extends InMemoryAetherResourceStore<MyDto>"));
        assertTrue(memSource.contains("implements MyDtoStore"));
        assertTrue(memSource.contains("super(MyDto.class)"));
        assertTrue(memSource.contains("public MemoryMyDtoStore()"));

        final String configSource = readFile(fsConfig.toFile()).response();
        assertTrue(configSource.contains("extends FileSystemAetherSingletonStore<ConfigDto>"));
        assertTrue(configSource.contains("implements ConfigDtoStore"));
        assertTrue(configSource.contains("super(root, ConfigDto.class)"));
        assertTrue(
                !fsSource.contains("@Component"),
                "scr defaults to false — no DS annotations");
    }

    @Test
    void generatesScrAnnotationsWhenEnabled() throws Exception {
        compileFixtures(
                "MyDto.java",
                "MyDtoStore.java",
                "ConfigDto.java",
                "ConfigDtoStore.java",
                "server-scr/package-info.java");

        final String fsSource = readFile(
                outputDir.resolve("fixtures/server/scr/FsMyDtoStore.java").toFile()).response();
        assertTrue(fsSource.contains("import org.osgi.service.component.annotations.Activate;"));
        assertTrue(fsSource.contains("import org.osgi.service.component.annotations.Component;"));
        assertTrue(fsSource.contains("import org.osgi.service.component.annotations.Reference;"));
        assertTrue(fsSource.contains("import org.dempsay.aether.store.config.FileStoreConfig;"));
        assertTrue(fsSource.contains("@Component(service = MyDtoStore.class)"));
        assertTrue(fsSource.contains("@Activate"));
        assertTrue(fsSource.contains("@Reference final FileStoreConfig config"));
        assertTrue(fsSource.contains("Path.of(config.location())"));
        assertTrue(fsSource.contains("public FsMyDtoStore(final Path root)"));

        final String memSource = readFile(
                outputDir.resolve("fixtures/server/scr/MemoryMyDtoStore.java").toFile()).response();
        assertTrue(memSource.contains("@Component(service = MyDtoStore.class)"));
        assertTrue(memSource.contains("@Activate"));
        assertTrue(memSource.contains("public MemoryMyDtoStore()"));
    }

    @Test
    void rejectsEmptyProvidersAnnotation() {
        final DiagnosticCollector<JavaFileObject> diagnostics = compileSources(
                listOf(
                        new File(FIXTURES, "MyDto.java"),
                        new File(FIXTURES, "MyDtoStore.java"),
                        new File("src/test/resources/fixtures-invalid/EmptyProviders.java")));
        assertTrue(
                diagnostics.getDiagnostics().stream()
                        .anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
                                && d.getMessage(null).contains("at least one non-empty list")),
                "Expected error for empty @AetherStoreProviders");
    }

    @Test
    void rejectsSingletonInFilesystemList() {
        final DiagnosticCollector<JavaFileObject> diagnostics = compileSources(
                listOf(
                        new File(FIXTURES, "ConfigDto.java"),
                        new File(FIXTURES, "ConfigDtoStore.java"),
                        new File("src/test/resources/fixtures-invalid/SingletonInFilesystem.java")));
        assertTrue(
                diagnostics.getDiagnostics().stream()
                        .anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
                                && d.getMessage(null).contains("singletonFilesystem")),
                "Expected error when @Singleton is listed under filesystem");
    }

    private void compileFixtures(final String... relativePaths) {
        final List<File> sources = new ArrayList<>();
        for (final String relative : relativePaths) {
            sources.add(new File(FIXTURES, relative));
        }
        final DiagnosticCollector<JavaFileObject> diagnostics = compileSources(sources);
        final String errors = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null))
                .collect(Collectors.joining(System.lineSeparator()));
        assertTrue(errors.isEmpty(), "Compilation failed: " + errors);
    }

    private DiagnosticCollector<JavaFileObject> compileSources(final List<File> sources) {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        final StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        final List<String> options = List.of(
                "-cp", classpath,
                "-d", outputDir.toString(),
                "-processor", "org.dempsay.aether.store.gen.AetherStoreProvidersProcessor",
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

    private static List<File> listOf(final File... files) {
        return List.of(files);
    }

    private String buildClasspath() {
        final List<String> entries = new ArrayList<>();
        entries.add(new File("target/classes").getAbsolutePath());
        entries.add(new File("../aether-api/target/classes").getAbsolutePath());
        entries.add(new File("../aether-store-fs/target/classes").getAbsolutePath());
        entries.add(resolveDependency("org.dempsay.utils", "exceptional", "1.0.9"));
        entries.add(resolveDependency("org.dempsay.support.jsr269", "jsr269-utilities", "1.0.1"));
        entries.add(resolveDependency("org.freemarker", "freemarker", "2.3.34"));
        entries.add(resolveDependency("com.google.code.gson", "gson", "2.11.0"));
        entries.add(resolveDependency(
                "org.osgi", "org.osgi.service.component.annotations", "1.5.1"));
        return String.join(File.pathSeparator, entries);
    }

    private String resolveDependency(final String groupId, final String artifactId, final String version) {
        return System.getProperty("user.home")
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
    }

    private static ExceptionalResponse<String> readFile(final File file) {
        return ExceptionalResource.of(
                () -> new BufferedReader(new FileReader(file)),
                reader -> reader.lines().collect(Collectors.joining(System.lineSeparator())))
                .execute();
    }
}
