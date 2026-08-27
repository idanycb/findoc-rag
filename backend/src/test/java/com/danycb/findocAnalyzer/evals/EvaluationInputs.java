package com.danycb.findocAnalyzer.evals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

final class EvaluationInputs {
    private static final Path ROOT = Path.of(System.getProperty("findoc.eval.root", "../evals"))
            .toAbsolutePath().normalize();

    private EvaluationInputs() {
    }

    static Path requireFile(String relativePath) {
        Path file = ROOT.resolve(relativePath).normalize();
        if (!file.startsWith(ROOT)) {
            throw new AssertionError("Evaluation input escapes the configured root: " + relativePath);
        }
        if (!Files.isRegularFile(file)) {
            throw new AssertionError("Evaluation input is missing: " + file.toAbsolutePath()
                    + ". Record and commit the deterministic corpus before running -Peval.");
        }
        return file;
    }

    static JsonNode readJson(ObjectMapper objectMapper, String relativePath) throws IOException {
        return objectMapper.readTree(requireFile(relativePath).toFile());
    }

    static void verifyCorpusManifest(JsonNode manifest, String corpusDirectory) throws IOException {
        MessageDigest setDigest = sha256();
        for (JsonNode entry : manifest.path("files")) {
            String relativePath = entry.path("path").asText();
            String expected = entry.path("sha256").asText();
            Path file = requireFile(corpusDirectory + "/" + relativePath);
            String actual = HexFormat.of().formatHex(sha256().digest(Files.readAllBytes(file)));
            if (!actual.equals(expected)) {
                throw new AssertionError("Corpus checksum mismatch: " + file.toAbsolutePath());
            }
            setDigest.update(relativePath.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            setDigest.update(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        String actualSet = HexFormat.of().formatHex(setDigest.digest());
        if (!actualSet.equals(manifest.path("manifestSha256").asText())) {
            throw new AssertionError("Corpus manifest set checksum does not match its files");
        }
    }

    static List<JsonNode> readJsonLines(ObjectMapper objectMapper, String relativePath) throws IOException {
        Path dataset = requireFile(relativePath);
        List<String> lines = Files.readAllLines(dataset).stream().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty()) {
            throw new AssertionError("Evaluation dataset is empty: " + dataset.toAbsolutePath());
        }
        return lines.stream().map(line -> readLine(objectMapper, dataset, line)).toList();
    }

    private static JsonNode readLine(ObjectMapper objectMapper, Path dataset, String line) {
        try {
            return objectMapper.readTree(line);
        } catch (IOException failure) {
            throw new AssertionError("Invalid JSONL record in " + dataset.toAbsolutePath(), failure);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
