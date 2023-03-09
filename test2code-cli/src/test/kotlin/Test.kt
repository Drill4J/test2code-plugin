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
import com.epam.drill.plugins.test2code.cli.*
import java.io.*
import kotlin.test.*
import kotlin.test.Test

class Test {

    @Test
    fun `specified path is jar file`() {
        val rootDir = File("..", "test2code-admin")
        val jarFile = rootDir.walkTopDown().first { it.name.endsWith(".jar") }
        val file = File.createTempFile("test2code-", "-parsed-class.txt")
        val args = listOf(
            "--classpath=$jarFile",
            "--packages=com/epam",
            "--output=${file.path}"
        )
        main(args.toTypedArray())
        assertTrue { file.length() > 0 }
    }
}
