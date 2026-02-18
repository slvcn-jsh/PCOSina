package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressModePolicyTest {

    @Test
    fun progressScreen_exposesTodayWeekModes_andPrimaryCta() {
        val file = resolveMainSourceRoot().resolve(
            Paths.get(
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "ProgressScreen.kt"
            )
        )
        val text = String(Files.readAllBytes(file))
        assertTrue("Progress screen must define Today mode.", text.contains("Today(\"Today\")"))
        assertTrue("Progress screen must define Week mode.", text.contains("Week(\"Week\")"))
        assertTrue("Progress screen should show focus mode switcher.", text.contains("Focus Mode"))
        assertTrue("Progress screen should expose one primary CTA label.", text.contains("primaryCtaLabel"))
    }

    private fun resolveMainSourceRoot(): Path {
        val candidates = listOf(
            Paths.get("app", "src", "main", "java"),
            Paths.get("src", "main", "java")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Could not locate main source root for progress mode policy test.")
    }
}
