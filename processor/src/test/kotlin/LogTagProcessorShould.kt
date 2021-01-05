/*
 * Copyright (c) 2021. Christian Grach <christian.grach@cmgapps.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.cmgapps.kotlin.LogTagProcessor
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.Is.`is`
import org.intellij.lang.annotations.Language
import org.junit.Test

class LogTagProcessorShould {

    @Test
    fun generateForClass() {
        val result = SourceFile.kotlin(
            "class.kt",
            """
              package cmgapps.test

              @com.cmgapps.LogTag
              class TestClass
            """
        ).compile()

        @Language("kotlin")
        val expected = """
          package cmgapps.test

          import kotlin.String

          public val TestClass.LOG_TAG: String
            inline get() = "TestClass"

        """.trimIndent()
        assertThat(
            result.sourcesGeneratedByAnnotationProcessor.find { it.name == "__TestClassLogTag.kt" }?.readText(),
            `is`(expected)
        )
    }

    @Test
    fun notGenerateForPrivateClass() {
        val result = SourceFile.kotlin(
            "object.kt",
            """
              package cmgapps.test

              @com.cmgapps.LogTag
              private class TestClass
            """
        ).compile()

        assertThat(result.exitCode, `is`(KotlinCompilation.ExitCode.COMPILATION_ERROR))
    }

    @Test
    fun generateForObject() {
        val result = SourceFile.kotlin(
            "file1.kt",
            """
              package cmgapps.test

              @com.cmgapps.LogTag
              object TestObject
            """
        ).compile()

        @Language("kotlin")
        val expected = """
          package cmgapps.test

          import kotlin.String

          public val TestObject.LOG_TAG: String
            inline get() = "TestObject"

        """.trimIndent()
        assertThat(
            result.sourcesGeneratedByAnnotationProcessor.find { it.name == "__TestObjectLogTag.kt" }?.readText(),
            `is`(expected)
        )
    }

    private fun SourceFile.compile() = KotlinCompilation().apply {
        sources = listOf(this@compile)
        annotationProcessors = listOf(LogTagProcessor())
        inheritClassPath = true
    }.compile()
}
