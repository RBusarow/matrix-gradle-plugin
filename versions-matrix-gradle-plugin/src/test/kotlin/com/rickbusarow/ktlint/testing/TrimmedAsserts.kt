/*
 * Copyright (C) 2024 Rick Busarow
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

package com.rickbusarow.ktlint.testing

import com.rickbusarow.ktlint.internal.requireNotNull
import java.io.File
import kotlin.reflect.KClass
import io.kotest.matchers.shouldBe as kotestShouldBe

/**
 * Contains wrappers for Kotest's assertions which catch
 * AssertionErrors and clean up stack traces before rethrowing.
 */
@SkipInStackTrace
interface TrimmedAsserts {

  /** reads the file's text and asserts */
  infix fun File.shouldHaveText(expected: String) {
    asClueCatching {
      readText() shouldBe expected
    }
  }

  /**
   * Delegates to Kotest's [shouldBe][io.kotest.matchers.shouldBe],
   * but removes all the noise at the beginning of a stacktrace, like:
   *
   * ```text
   * at app//kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
   * at app//kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:106)
   * ```
   *
   * @see io.kotest.matchers.shouldBe
   * @see trimmedAssert
   * @see trimmedShouldBe
   */
  infix fun <T, U : T> T.shouldBe(expected: U?) {
    trimmedShouldBe(expected)
  }

  /**
   * Delegates to Kotest's [shouldBe][io.kotest.matchers.shouldBe],
   * but removes all the noise at the beginning of a stacktrace, like:
   *
   * ```text
   * at app//kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
   * at app//kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:106)
   * ```
   *
   * @see io.kotest.matchers.shouldBe
   * @see trimmedAssert
   * @see trimmedShouldBe
   */
  fun <T, U : T> T.trimmedShouldBe(expected: U?, vararg excludeFromStack: KClass<*>) {
    trimmedAssert(*excludeFromStack) {
      kotestShouldBe(expected)
    }
  }

  /** Treat sequences like lists when asserting. */
  infix fun Sequence<*>.shouldBe(expected: List<*>) {
    toList().shouldBe(expected)
  }
}

/** Removes the noise at the beginning of a stacktrace, in the event that [assertion] fails. */
inline fun <T, R> T.trimmedAssert(
  vararg excludeFromStack: KClass<*>,
  crossinline assertion: T.() -> R
): R {

  // Any AssertionError generated by this function will have this function at the top of its
  // stacktrace, followed by lots of coroutines noise. So, we can catch the assertion error, remove the
  // noise from the stacktrace, and rethrow.
  return try {
    assertion()
  } catch (assertionError: AssertionError) {

    val excludes = sequenceOf(TrimmedAsserts::class, HasWorkingDir::class)
      .plus(excludeFromStack)
      .map { it.qualifiedName.requireNotNull() }
      .flatMap { fqName -> setOf(fqName, "${fqName}Kt") }
      .flatMap { fqName ->
        setOf(
          fqName,
          "${fqName}\$runBlocking\$1",
          "${fqName}\$shouldBe\$1",
          "${fqName}\$trimmedAssert\$1",
          "${fqName}\$trimmedShouldBe\$1"
        )
      }
      .toSet()

    // remove this function from the stacktrace and rethrow
    @Suppress("MagicNumber")
    assertionError.stackTrace = assertionError
      .stackTrace
      // Note that this is `dropWhile` instead of `filter`.
      // We only remove lines until we reach something which isn't skipped.
      .dropWhile {
        when {
          it.className in excludes -> true
          it.className in coroutinesClasses -> true
          it.methodName == "shouldBe" -> true
          it.isSkipped() -> true
          it.declaringClass().hasSkipAnnotation() -> true
          else -> false
        }
      }
      .take(15) // keep stack traces short
      .toTypedArray()
    throw assertionError
  }
}

@PublishedApi
internal val coroutinesClasses: Set<String> = setOf(
  "io.kotest.common.runBlocking",
  "kotlin.coroutines.jvm.internal.BaseContinuationImpl",
  "kotlinx.coroutines.AbstractCoroutine",
  "kotlinx.coroutines.BlockingCoroutine",
  "kotlinx.coroutines.BuildersKt",
  "kotlinx.coroutines.BuildersKt__BuildersKt",
  "kotlinx.coroutines.DispatchedTask",
  "kotlinx.coroutines.EventLoopImplBase",
  "kotlinx.coroutines.internal.ScopeCoroutine"
)
