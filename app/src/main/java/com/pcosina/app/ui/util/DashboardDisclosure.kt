package com.pcosina.app.ui.util

const val DASHBOARD_ADVANCED_MIN_STEP = 6
private const val DASHBOARD_COMPRESS_MAX_STEP = 4
private const val DASHBOARD_FIRST_PLAN_COPY_MAX_STEP = 3
private const val DASHBOARD_PROGRESS_SNAPSHOT_STEP = 4

fun shouldShowAdvancedMetrics(stepIndex: Int, hasTracked: Boolean): Boolean =
    stepIndex >= DASHBOARD_ADVANCED_MIN_STEP || hasTracked

fun shouldShowAdvancedTools(stepIndex: Int): Boolean =
    stepIndex >= DASHBOARD_ADVANCED_MIN_STEP

fun shouldCompressSecondaryStats(stepIndex: Int): Boolean =
    stepIndex <= DASHBOARD_COMPRESS_MAX_STEP

fun shouldUseFirstPlanUnlockCopy(stepIndex: Int): Boolean =
    stepIndex <= DASHBOARD_FIRST_PLAN_COPY_MAX_STEP

fun shouldShowProgressSnapshot(stepIndex: Int): Boolean =
    stepIndex >= DASHBOARD_PROGRESS_SNAPSHOT_STEP
