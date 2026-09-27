package com.behaviouralplatform.schemas;

import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Loads schema files and resolves their references. */
public final class SchemaFiles {

    public static final String ENVELOPE_SUBJECT = "behavioural_envelope";

    private static final Pattern VERSION_FILE = Pattern.compile("v(\\d+)\\.json");
    /** {@code "$ref": "<subject>/v<N>.json"}: the name carries the referenced subject and version. */
    private static final Pattern REF = Pattern.compile("\"\\$ref\"\\s*:\\s*\"(([a-z_]+)/v(\\d+)\\.json)\"");

    private static final Comparator<Path> ENVELOPE_FIRST = Comparator.comparing(
                    (Path p) -> !p.getFileName().toString().equals(ENVELOPE_SUBJECT))
            .thenComparing(p -> p.getFileName().toString());

    private SchemaFiles() {}

    /** All schema files: envelope first, then subjects alphabetically, versions ascending. */
    public static List<SchemaFile> load(Path dir) throws IOException {
        List<SchemaFile> files = new ArrayList<>();
        try (Stream<Path> subjects = Files.list(dir)) {
            for (Path subjectDir :
                    subjects.filter(Files::isDirectory).sorted(ENVELOPE_FIRST).toList()) {
                files.addAll(loadSubject(subjectDir));
            }
        }
        return files;
    }

    private static List<SchemaFile> loadSubject(Path subjectDir) throws IOException {
        String subject = subjectDir.getFileName().toString();
        List<SchemaFile> versions = new ArrayList<>();
        try (Stream<Path> paths = Files.list(subjectDir)) {
            for (Path path : paths.toList()) {
                Matcher name = VERSION_FILE.matcher(path.getFileName().toString());
                if (name.matches()) {
                    String content = Files.readString(path);
                    versions.add(
                            new SchemaFile(subject, Integer.parseInt(name.group(1)), content, references(content)));
                }
            }
        }
        versions.sort(Comparator.comparingInt(SchemaFile::version));
        return versions;
    }

    private static List<SchemaReference> references(String content) {
        List<SchemaReference> references = new ArrayList<>();
        Matcher ref = REF.matcher(content);
        while (ref.find()) {
            references.add(new SchemaReference(ref.group(1), ref.group(2), Integer.parseInt(ref.group(3))));
        }
        return references;
    }

    /** Builds a JsonSchema with its references resolved from the other loaded files. */
    public static JsonSchema toJsonSchema(SchemaFile file, List<SchemaFile> all) {
        Map<String, String> resolved = new HashMap<>();
        for (SchemaReference reference : file.references()) {
            SchemaFile target = all.stream()
                    .filter(f -> f.subject().equals(reference.getSubject()) && f.version() == reference.getVersion())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(file.subject() + "/v" + file.version()
                            + ".json references missing " + reference.getName()));
            resolved.put(reference.getName(), target.content());
        }
        return new JsonSchema(file.content(), file.references(), resolved, null);
    }
}
