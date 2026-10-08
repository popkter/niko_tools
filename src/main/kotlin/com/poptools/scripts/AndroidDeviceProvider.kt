package com.poptools.scripts

import com.intellij.openapi.project.Project

/** Loaded only when the optional Android plugin is available. */
fun interface AndroidDeviceProvider {
    fun selectedSerial(project: Project?): String
}
