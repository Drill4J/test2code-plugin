/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.plugins.test2code.e2e

import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import io.kotlintest.*
import kotlinx.coroutines.*
import kotlin.test.*

class BuildMethodsTests : E2EPluginTest() {

    @Test
    fun `deploy 2 builds without coverage`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, _ ->
                plugUi.methods()!!.apply {
                    all.total shouldBe 4
                    new.total shouldBe 0
                    modified.total shouldBe 0
                    deleted.covered shouldBe 0
                    deleted.total shouldBe 0
                }
                delay(300)
            }.reconnect<Build2> { plugUi, _ ->
                plugUi.methods()!!.apply {
                    all.total shouldBe 5
                    new.total shouldBe 1
                    modified.total shouldBe 3
                    deleted.covered shouldBe 0
                    deleted.total shouldBe 0
                }
            }
        }
    }
}
