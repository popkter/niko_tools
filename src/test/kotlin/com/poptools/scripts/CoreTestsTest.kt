package com.poptools.scripts

import org.junit.Test

/** Runs the core regression suite through the standard Gradle test task. */
class CoreTestsTest {
    @Test fun coreRegressionSuite() {
        val fixture = System.getProperty("nikotools.compatibilityFixture", "")
        CoreTests.main(if (fixture.isBlank()) emptyArray() else arrayOf(fixture))
    }
}
