package com.moca.mocabe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CodeRabbitConfigTest {

    private static final Path CONFIG_PATH = Paths.get(".coderabbit.yaml");

    private String content;
    private List<String> lines;

    @BeforeEach
    void setUp() throws IOException {
        content = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
        lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
    }

    @Test
    void declaresYamlSchemaReferenceOnFirstLine() {
        assertEquals("# yaml-language-server: $schema=https://coderabbit.ai/integrations/schema.v2.json",
                lines.get(0));
    }

    @Test
    void configuresKoreanLanguageAndDisablesEarlyAccess() {
        assertTrue(content.contains("language: \"ko-KR\""));
        assertTrue(content.contains("early_access: false"));
    }

    @Test
    void configuresAssertiveReviewProfileWithoutBlockingMerges() {
        assertTrue(content.contains("profile: \"assertive\""));
        assertTrue(content.contains("request_changes_workflow: false"));
        assertTrue(content.contains("high_level_summary: true"));
    }

    @Test
    void configuresReviewDisplayOptions() {
        assertTrue(content.contains("poem: false"));
        assertTrue(content.contains("review_status: true"));
        assertTrue(content.contains("review_details: false"));
        assertTrue(content.contains("collapse_walkthrough: false"));
        assertFalse(content.contains("poem: true"), "Poem generation should stay disabled");
    }

    @Test
    void enablesAutoReviewForNonDraftPullRequestsTargetingDev() {
        assertTrue(content.contains("auto_review:"));
        assertTrue(content.contains("enabled: true"));
        assertTrue(content.contains("drafts: false"));
        assertTrue(content.contains("base_branches:"));
        assertTrue(content.contains("- \"dev\""));
    }

    @Test
    void ignoresPullRequestsMarkedAsWorkInProgressOrDoNotMerge() {
        assertTrue(content.contains("ignore_title_keywords:"));
        assertTrue(content.contains("- \"WIP\""));
        assertTrue(content.contains("- \"DO NOT MERGE\""));
        assertTrue(content.contains("- \"[skip review]\""));
    }

    @Test
    void excludesPullRequestsLabeledDoNotReview() {
        assertTrue(content.contains("labels:"));
        assertTrue(content.contains("- \"!do-not-review\""));
    }

    @Test
    void excludesDocumentationLockAndGeneratedPathsFromReview() {
        assertTrue(content.contains("- \"!**/*.md\""));
        assertTrue(content.contains("- \"!**/*.lock\""));
        assertTrue(content.contains("- \"!**/generated/**\""));
        assertTrue(content.contains("- \"!**/build/**\""));
        assertTrue(content.contains("- \"!**/target/**\""));
    }

    @Test
    void definesPathInstructionsForEveryReviewedLayer() {
        List<String> expectedPaths = List.of(
                "path: \"src/main/java/**/*.java\"",
                "path: \"src/main/java/**/controller/**/*.java\"",
                "path: \"src/main/resources/**/*.xml\"",
                "path: \"src/test/**/*.java\"",
                "path: \"src/main/resources/openapi/**\""
        );

        for (String expectedPath : expectedPaths) {
            assertTrue(content.contains(expectedPath), "Missing path instruction: " + expectedPath);
        }
    }

    @Test
    void pathInstructionsAppearInLayeredReviewOrder() {
        int javaLayerIndex = content.indexOf("path: \"src/main/java/**/*.java\"");
        int controllerIndex = content.indexOf("path: \"src/main/java/**/controller/**/*.java\"");
        int mapperXmlIndex = content.indexOf("path: \"src/main/resources/**/*.xml\"");
        int testIndex = content.indexOf("path: \"src/test/**/*.java\"");
        int openApiIndex = content.indexOf("path: \"src/main/resources/openapi/**\"");

        assertTrue(javaLayerIndex >= 0 && javaLayerIndex < controllerIndex);
        assertTrue(controllerIndex < mapperXmlIndex);
        assertTrue(mapperXmlIndex < testIndex);
        assertTrue(testIndex < openApiIndex);
    }

    @Test
    void flagsJwtAdoptionInJavaLayerReviewInstructions() {
        int javaLayerIndex = content.indexOf("path: \"src/main/java/**/*.java\"");
        int controllerIndex = content.indexOf("path: \"src/main/java/**/controller/**/*.java\"");
        String javaLayerInstructions = content.substring(javaLayerIndex, controllerIndex);

        assertTrue(javaLayerInstructions.contains("JWT"));
        assertTrue(javaLayerInstructions.contains("opaque token"));
    }

    @Test
    void enablesAutomaticChatReplies() {
        assertTrue(content.contains("chat:"));
        assertTrue(content.contains("auto_reply: true"));
    }

    @Test
    void doesNotUseTabsForIndentation() {
        for (int i = 0; i < lines.size(); i++) {
            assertFalse(lines.get(i).contains("\t"), "Line " + (i + 1) + " contains a tab character");
        }
    }
}