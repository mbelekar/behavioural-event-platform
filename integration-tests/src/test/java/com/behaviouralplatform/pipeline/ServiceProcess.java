package com.behaviouralplatform.pipeline;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs a service's boot jar as a separate JVM, the way it runs in production. Output goes to build/service-logs. */
final class ServiceProcess implements AutoCloseable {

    private final Process process;

    private ServiceProcess(Process process) {
        this.process = process;
    }

    static ServiceProcess start(String service, Map<String, String> environment) throws IOException {
        Path jar = Arrays.stream(System.getProperty("service.jars").split(File.pathSeparator))
                .map(Path::of)
                .filter(p -> p.getFileName().toString().startsWith(service))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No boot jar for " + service));
        Path log = Path.of("build/service-logs/" + service + ".log");
        Files.createDirectories(log.getParent());
        String java = ProcessHandle.current().info().command().orElseThrow();
        ProcessBuilder builder = new ProcessBuilder(java, "-jar", jar.toString())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        builder.environment().putAll(environment);
        return new ServiceProcess(builder.start());
    }

    @Override
    public void close() {
        process.destroy();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
