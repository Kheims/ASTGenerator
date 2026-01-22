package com.kitcode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class JavaDirValidator {

    private static final String DEFAULT_INPUT_DIR = "graphs/json/java";
    private static final String DEFAULT_ERROR_DIR = "graphs/json/errors";

    public static void main(String[] args) throws IOException {
        String inputDir = args.length > 0 ? args[0] : DEFAULT_INPUT_DIR;
        String errorDir = args.length > 1 ? args[1] : DEFAULT_ERROR_DIR;

        Path baseInput = Paths.get(inputDir);
        if (!Files.exists(baseInput)) {
            System.err.println("Input directory does not exist: " + baseInput);
            return;
        }

        try (Stream<Path> paths = Files.walk(baseInput)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                 .forEach(path -> validateFile(path, errorDir));
        }
    }

    private static void validateFile(Path path, String errorDir) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            boolean ok = ASTGenerator.parseOnly(source, path.getFileName().toString());
            if (!ok) {
                writeErrorArtifact(path, errorDir, source);
            }
        } catch (IOException e) {
            System.err.println("Failed to read " + path + ": " + e.getMessage());
        }
    }

    private static void writeErrorArtifact(Path path, String errorDir, String source) {
        try {
            Files.createDirectories(Paths.get(errorDir));
            String baseName = path.getFileName().toString().replace(".java", "");
            Path errorFile = Paths.get(errorDir, baseName + ".txt");
            String header = extractHeader(source);
            StringBuilder sb = new StringBuilder();
            sb.append("header:\n").append(header == null ? "" : header).append("\n\n");
            sb.append("source:\n").append(source);
            Files.writeString(errorFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to write error artifact for " + path + ": " + e.getMessage());
        }
    }

    private static String extractHeader(String source) {
        if (source == null) {
            return null;
        }
        int newline = source.indexOf('\n');
        String firstLine = newline >= 0 ? source.substring(0, newline) : source;
        String trimmed = firstLine.trim();
        if (trimmed.startsWith("//")) {
            return trimmed.substring(2).trim();
        }
        return null;
    }
}
