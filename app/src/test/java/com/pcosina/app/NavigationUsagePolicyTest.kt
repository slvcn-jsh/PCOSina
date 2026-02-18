package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.asSequence
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationUsagePolicyTest {
    @Test
    fun routes_doNotUseStringLiteralsInNavigateOrComposable() {
        val sourceRoot = resolveMainSourceRoot()
        val violations = mutableListOf<String>()
        Files.walk(sourceRoot).use { paths ->
            paths.asSequence()
                .filter { file -> Files.isRegularFile(file) && file.toString().endsWith(".kt") }
                .forEach { file ->
                    Files.readAllLines(file).forEachIndexed { index, line ->
                        if (line.contains(Regex("""\bcomposable\s*\(\s*\"""")) ||
                            line.contains(Regex("""\bnavigate\s*\(\s*\""""))
                        ) {
                            violations.add("${sourceRoot.relativize(file)}:${index + 1} -> ${line.trim()}")
                        }
                    }
                }
        }
        assertTrue(
            "Use Routes constants/helpers instead of string literals:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun routes_doNotUseRawNavControllerNavigateCalls() {
        val sourceRoot = resolveMainSourceRoot()
        val violations = mutableListOf<String>()
        val rawNavigateRegex = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]*\s*\.\s*navigate\s*\(""")
        Files.walk(sourceRoot).use { paths ->
            paths.asSequence()
                .filter { file -> Files.isRegularFile(file) && file.toString().endsWith(".kt") }
                .forEach { file ->
                    Files.readAllLines(file).forEachIndexed { index, line ->
                        if (rawNavigateRegex.containsMatchIn(line)) {
                            violations.add("${sourceRoot.relativize(file)}:${index + 1} -> ${line.trim()}")
                        }
                    }
                }
        }
        assertTrue(
            "Use AppNavHost navigateInternal()/navigateKnown() or route-safe wrappers:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    private fun resolveMainSourceRoot(): Path {
        val candidates = listOf(
            Paths.get("app", "src", "main", "java"),
            Paths.get("src", "main", "java")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Could not locate main source root for policy test.")
    }
}
