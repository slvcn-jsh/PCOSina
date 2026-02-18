package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.asSequence
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeRouteContextPolicyTest {

    @Test
    fun uiEntryPoints_passMealLabelWhenOpeningRecipeDetails() {
        val sourceRoot = resolveMainSourceRoot()
        val singleArgPattern = Regex("""Routes\.recipeDetailsRoute\(\s*[^,\)\n]+\s*\)""")
        val violations = mutableListOf<String>()

        Files.walk(sourceRoot).use { paths ->
            paths.asSequence()
                .filter { file -> Files.isRegularFile(file) && file.toString().endsWith(".kt") }
                .forEach { file ->
                    val text = String(Files.readAllBytes(file))
                    singleArgPattern.findAll(text).forEach { match ->
                        val relative = sourceRoot.relativize(file).toString().replace("\\", "/")
                        if (relative.startsWith("ui/")) {
                            violations += "$relative -> ${match.value}"
                        }
                    }
                }
        }

        assertTrue(
            "UI recipe navigation should pass meal-label context to avoid duplicate-slot ambiguity:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    private fun resolveMainSourceRoot(): Path {
        val candidates = listOf(
            Paths.get("app", "src", "main", "java", "com", "pcosina", "app"),
            Paths.get("src", "main", "java", "com", "pcosina", "app")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Could not locate app source root for policy test.")
    }
}
