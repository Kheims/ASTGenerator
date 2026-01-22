package com.kitcode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class JavaDirDotGenerator {

    private static final String DEFAULT_INPUT_DIR = "graphs/json/java";
    private static final String DEFAULT_OUTPUT_DIR = "graphs/data";

    public static void main(String[] args) throws IOException {
        String inputDir = args.length > 0 ? args[0] : DEFAULT_INPUT_DIR;
        String outputDir = args.length > 1 ? args[1] : DEFAULT_OUTPUT_DIR;

        Path baseInput = Paths.get(inputDir);
        if (!Files.exists(baseInput)) {
            System.err.println("Input directory does not exist: " + baseInput);
            return;
        }

        try (Stream<Path> paths = Files.walk(baseInput)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                 .forEach(path -> {
                     try {
                         String source = Files.readString(path, StandardCharsets.UTF_8);
                         String header = extractHeader(source);
                         Path outputPath = resolveOutputPath(baseInput, path, outputDir);
                         Files.createDirectories(outputPath.getParent());
                         boolean ok = ASTGenerator.generateDotFromString(source, outputPath.toString(), header);
                         if (!ok) {
                             System.err.println("Skipping " + path + " due to syntax errors");
                         }
                     } catch (IOException e) {
                         System.err.println("Failed to process " + path + ": " + e.getMessage());
                     }
                 });
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

    private static Path resolveOutputPath(Path baseInput, Path javaFile, String outputDir) {
        String fileName = javaFile.getFileName().toString();
        String dotFileName = fileName.endsWith(".java")
                ? fileName.substring(0, fileName.length() - 5) + ".dot"
                : fileName + ".dot";

        Path baseOutput = Paths.get(outputDir);
        Path relative = baseInput.relativize(javaFile.getParent());
        return baseOutput.resolve(relative).resolve(dotFileName);
    }
}
