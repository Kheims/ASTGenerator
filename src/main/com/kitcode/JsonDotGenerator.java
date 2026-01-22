package com.kitcode;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Arrays;

public class JsonDotGenerator {

    private static final String DEFAULT_INPUT = "MLCQCodeSmellSamples.json";
    private static final String NORMALIZED_INPUT = "MLCQCodeSmellSamples.normalized.json";
    private static final String DEFAULT_OUTPUT_DIR = "graphs/json";
    private static final String DEFAULT_JAVA_OUTPUT_DIR = "graphs/json/java";

    private static class SampleEntry {
        String repo_url;
        String commit_hash;
        String file_path;
        int start_line;
        int end_line;
        String code_snippet;
        String smell;
        String severity;
        Map<String, LabelInfo> labels;
        List<Boolean> y;
    }

    private static class LabelInfo {
        String severity;
        Boolean present;
        Integer vote_count;
    }

    private static class WrapCandidate {
        final String source;
        final String mode;

        WrapCandidate(String source, String mode) {
            this.source = source;
            this.mode = mode;
        }
    }

    public static void main(String[] args) throws IOException {
        String inputPath = args.length > 0 ? args[0] : resolveDefaultInput();
        String outputDir = args.length > 1 ? args[1] : DEFAULT_OUTPUT_DIR;
        String javaOutputDir = args.length > 2 ? args[2] : DEFAULT_JAVA_OUTPUT_DIR;

        List<SampleEntry> entries = readEntries(inputPath);
        Files.createDirectories(Paths.get(outputDir));
        Files.createDirectories(Paths.get(javaOutputDir));

        Map<String, Integer> nameCounts = new HashMap<String, Integer>();
        for (int i = 0; i < entries.size(); i++) {
            SampleEntry entry = entries.get(i);
            generatePerSnippet(entry, i, outputDir, javaOutputDir, nameCounts);
        }
    }

    private static List<SampleEntry> readEntries(String inputPath) throws IOException {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<SampleEntry>>() {}.getType();
        Path path = Paths.get(inputPath);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, listType);
        }
    }

    private static void generatePerSnippet(SampleEntry entry, int index, String outputDir, String javaOutputDir, Map<String, Integer> nameCounts) throws IOException {
        String baseName = buildBaseName(entry);
        int count = nameCounts.getOrDefault(baseName, 0) + 1;
        nameCounts.put(baseName, count);

        String javaFileName = applySuffix(baseName, count);
        Path javaPath = Paths.get(javaOutputDir, javaFileName);

        String header = buildHeader(entry, index);
        String source = buildJavaSource(entry.code_snippet, header);
        Files.writeString(javaPath, source, StandardCharsets.UTF_8);
    }

    private static String buildBaseName(SampleEntry entry) {
        String commit = safeToken(entry.commit_hash);
        String file = extractFileName(entry.file_path).replaceAll("\\s+", "_");
        String start = Integer.toString(entry.start_line);
        String end = Integer.toString(entry.end_line);
        String y = formatYCompact(entry);
        return commit + "_" + start + "_" + end + "_" + y + "_" + file;
    }

    private static String buildHeader(SampleEntry entry, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append("json_index=").append(index);
        appendHeaderField(sb, "commit_hash", entry.commit_hash);
        appendHeaderField(sb, "file_path", entry.file_path);
        appendHeaderField(sb, "start_line", Integer.toString(entry.start_line));
        appendHeaderField(sb, "end_line", Integer.toString(entry.end_line));
        appendHeaderField(sb, "y", formatY(entry));
        return escapeHeader(sb.toString());
    }

    private static void appendHeaderField(StringBuilder sb, String key, String value) {
        sb.append(" ");
        sb.append(key);
        sb.append("=");
        sb.append(value == null ? "" : value);
    }

    private static String escapeHeader(String header) {
        return header.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String addHeaderComment(String header, String source) {
        if (header == null || header.isEmpty()) {
            return source;
        }
        return "// " + header + "\n" + source;
    }

    private static String buildJavaSource(String snippet, String header) {
        WrapCandidate chosen = selectWrapCandidate(snippet);
        String source = chosen != null ? chosen.source : "public class Snippet {}";
        return addHeaderComment(header, source);
    }

    private static WrapCandidate selectWrapCandidate(String snippet) {
        String minimal = normalizeSnippet(snippet, false);
        String aggressive = normalizeSnippet(snippet, true);

        if (minimal == null) {
            return new WrapCandidate("public class Snippet {}", "min-empty");
        }

        String trimmedMin = minimal.trim();
        String trimmedAgg = aggressive != null ? aggressive.trim() : null;

        if (looksLikeTypeDeclaration(trimmedMin)) {
            return new WrapCandidate(trimmedMin, "min-type");
        }
        if (trimmedAgg != null && looksLikeTypeDeclaration(trimmedAgg)) {
            return new WrapCandidate(trimmedAgg, "agg-type");
        }
        if (looksLikeStatement(trimmedMin)) {
            return new WrapCandidate(wrapAsStatement(minimal), "min-statement");
        }
        if (looksLikeMemberDeclaration(trimmedMin)) {
            return new WrapCandidate(wrapAsMember(minimal), "min-member");
        }
        if (trimmedAgg != null && looksLikeStatement(trimmedAgg)) {
            return new WrapCandidate(wrapAsStatement(aggressive), "agg-statement");
        }
        if (trimmedAgg != null && looksLikeMemberDeclaration(trimmedAgg)) {
            return new WrapCandidate(wrapAsMember(aggressive), "agg-member");
        }
        return new WrapCandidate(wrapAsStatement(minimal), "min-default");
    }

    private static List<WrapCandidate> buildWrapCandidates(String snippet) {
        String normalized = normalizeSnippet(snippet, false);
        if (normalized == null) {
            return Arrays.asList(new WrapCandidate("public class Snippet {}", "empty"));
        }

        String trimmed = normalized.trim();
        if (looksLikeTypeDeclaration(trimmed)) {
            return Arrays.asList(new WrapCandidate(trimmed, "type"));
        }

        if (looksLikeStatement(trimmed)) {
            return Arrays.asList(
                    new WrapCandidate(wrapAsStatement(normalized), "statement"),
                    new WrapCandidate(wrapAsMember(normalized), "member")
            );
        }

        if (looksLikeMemberDeclaration(trimmed)) {
            return Arrays.asList(
                    new WrapCandidate(wrapAsMember(normalized), "member"),
                    new WrapCandidate(wrapAsStatement(normalized), "statement")
            );
        }

        return Arrays.asList(
                new WrapCandidate(wrapAsStatement(normalized), "statement"),
                new WrapCandidate(wrapAsMember(normalized), "member")
        );
    }

    private static String normalizeSnippet(String snippet, boolean aggressive) {
        if (snippet == null) {
            return null;
        }

        MaskedText masked = maskStringLiterals(snippet);
        String normalized = masked.text;
        if (aggressive) {
            String[] keywords = {
                    "public", "protected", "private", "static", "final", "abstract", "native", "strictfp",
                    "synchronized"
            };
            for (String keyword : keywords) {
                normalized = normalized.replaceAll("\\b" + keyword + "(?=[A-Za-z_])", keyword + " ");
            }
        }
        normalized = normalized.replaceAll("(?<=[a-zA-Z_])\\(", " (");
        normalized = normalized.replaceAll("\\)(?=[A-Za-z_])", ") ");
        normalized = normalized.replaceAll("(?<=[a-zA-Z_])\\{", " {");
        normalized = normalized.replaceAll("\\}(?=[A-Za-z_])", "} ");
        normalized = normalized.replaceAll("(?<=[^\\s])([;])", "$1 ");
        normalized = normalized.replaceAll("[\\t\\x0B\\f\\r ]+", " ");
        return unmaskStringLiterals(normalized, masked.literals);
    }

    private static String wrapAsMember(String snippet) {
        return "public class Snippet {\n" + snippet + "\n}\n";
    }

    private static String wrapAsStatement(String snippet) {
        return "public class Snippet {\n  void snippetMethod() {\n" + indentSnippet(snippet, "    ") + "\n  }\n}\n";
    }

    private static boolean looksLikeTypeDeclaration(String snippet) {
        String trimmed = stripLeadingComments(snippet).trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("package ") || lower.startsWith("import ")) {
            return true;
        }
        String typePattern = "^(?:@[_A-Za-z][\\w$.]*"
                + "(?:\\s*\\([^)]*\\))?\\s*)*"
                + "(?:(?:public|protected|private|abstract|static|final|strictfp)\\s+)*"
                + "(class|interface|enum)\\b.*";
        return trimmed.matches(typePattern);
    }

    private static String stripLeadingComments(String snippet) {
        if (snippet == null) {
            return "";
        }
        String remaining = snippet.trim();
        boolean changed;
        do {
            changed = false;
            if (remaining.startsWith("//")) {
                int newline = remaining.indexOf('\n');
                remaining = newline >= 0 ? remaining.substring(newline + 1).trim() : "";
                changed = true;
            } else if (remaining.startsWith("/*")) {
                int end = remaining.indexOf("*/");
                remaining = end >= 0 ? remaining.substring(end + 2).trim() : "";
                changed = true;
            }
        } while (changed);
        return remaining;
    }

    private static boolean looksLikeStatement(String snippet) {
        String lower = snippet.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("if ")
                || lower.startsWith("if(")
                || lower.startsWith("for ")
                || lower.startsWith("for(")
                || lower.startsWith("while ")
                || lower.startsWith("while(")
                || lower.startsWith("switch ")
                || lower.startsWith("switch(")
                || lower.startsWith("try ")
                || lower.startsWith("try{")
                || lower.startsWith("return ")
                || lower.startsWith("return;")
                || lower.startsWith("throw ")
                || lower.startsWith("do ")
                || lower.startsWith("break")
                || lower.startsWith("continue")
                || lower.startsWith("case ")
                || lower.startsWith("default");
    }

    private static boolean looksLikeMemberDeclaration(String snippet) {
        String lower = snippet.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("@")) {
            return true;
        }
        if (looksLikeStatement(lower)) {
            return false;
        }
        boolean hasParens = snippet.contains("(") && snippet.contains(")");
        boolean hasBraces = snippet.contains("{") && snippet.contains("}");
        if (hasParens && hasBraces) {
            return true;
        }
        return snippet.contains(";") && !snippet.contains("class ");
    }

    private static String indentSnippet(String snippet, String indent) {
        String[] lines = snippet.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(indent).append(lines[i]);
        }
        return sb.toString();
    }

    private static String safeToken(String value) {
        if (value == null || value.isEmpty()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    private static String extractFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private static String formatY(SampleEntry entry) {
        boolean[] y = resolveY(entry);
        return "[" + y[0] + ", " + y[1] + ", " + y[2] + ", " + y[3] + "]";
    }

    private static String formatYCompact(SampleEntry entry) {
        boolean[] y = resolveY(entry);
        return "[" + y[0] + "," + y[1] + "," + y[2] + "," + y[3] + "]";
    }

    private static boolean[] resolveY(SampleEntry entry) {
        if (entry.y != null && entry.y.size() >= 4) {
            return new boolean[] { entry.y.get(0), entry.y.get(1), entry.y.get(2), entry.y.get(3) };
        }
        boolean isFe = labelPresent(entry, "feature envy");
        boolean isLm = labelPresent(entry, "long method");
        boolean isBlob = labelPresent(entry, "blob");
        boolean isDc = labelPresent(entry, "data class");
        return new boolean[] { isFe, isLm, isBlob, isDc };
    }

    private static boolean labelPresent(SampleEntry entry, String labelKey) {
        if (entry.labels == null) {
            return false;
        }
        LabelInfo info = entry.labels.get(labelKey);
        return info != null && Boolean.TRUE.equals(info.present);
    }

    private static class MaskedText {
        final String text;
        final List<String> literals;

        MaskedText(String text, List<String> literals) {
            this.text = text;
            this.literals = literals;
        }
    }

    private static MaskedText maskStringLiterals(String input) {
        List<String> literals = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inString || inChar) {
                current.append(c);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                } else if (inString && c == '"') {
                    inString = false;
                } else if (inChar && c == '\'') {
                    inChar = false;
                }
                if (!inString && !inChar) {
                    String token = "__STR" + literals.size() + "__";
                    literals.add(current.toString());
                    sb.append(token);
                    current.setLength(0);
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                current.append(c);
                continue;
            }
            if (c == '\'') {
                inChar = true;
                current.append(c);
                continue;
            }
            sb.append(c);
        }

        if (current.length() > 0) {
            String token = "__STR" + literals.size() + "__";
            literals.add(current.toString());
            sb.append(token);
        }

        return new MaskedText(sb.toString(), literals);
    }

    private static String unmaskStringLiterals(String input, List<String> literals) {
        String result = input;
        for (int i = 0; i < literals.size(); i++) {
            result = result.replace("__STR" + i + "__", literals.get(i));
        }
        return result;
    }

    private static String applySuffix(String baseName, int count) {
        if (count <= 1) {
            return baseName;
        }
        String suffix = "_" + String.format("%03d", count);
        if (baseName.endsWith(".java")) {
            return baseName.substring(0, baseName.length() - 5) + suffix + ".java";
        }
        return baseName + suffix;
    }

    private static String resolveDefaultInput() {
        Path normalized = Paths.get(NORMALIZED_INPUT);
        if (Files.exists(normalized)) {
            return NORMALIZED_INPUT;
        }
        return DEFAULT_INPUT;
    }

    private static void writeFailureArtifact(String outputDir, String sourceName, String header, String originalSnippet, List<WrapCandidate> candidates) throws IOException {
        Path errorsDir = Paths.get(outputDir, "errors");
        Files.createDirectories(errorsDir);
        String baseName = sourceName.replaceAll("\\.java$", "");
        Path errorFile = errorsDir.resolve(baseName + ".txt");
        StringBuilder sb = new StringBuilder();
        sb.append("header:\n").append(header == null ? "" : header).append("\n\n");
        sb.append("original:\n").append(originalSnippet == null ? "" : originalSnippet).append("\n\n");
        for (WrapCandidate candidate : candidates) {
            sb.append("wrap=").append(candidate.mode).append("\n");
            sb.append(addHeaderComment(header, candidate.source)).append("\n\n");
        }
        Files.writeString(errorFile, sb.toString(), StandardCharsets.UTF_8);
    }
}
