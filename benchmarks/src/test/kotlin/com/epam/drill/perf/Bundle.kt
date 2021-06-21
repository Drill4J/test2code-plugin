package com.epam.drill.perf

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.annotations.Scope
import java.io.*
import java.util.concurrent.*

@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(2)
class Bundle {
////    Change fun access modifier
//
//    private lateinit var session: Map<String, List<ActiveSession>>
//    private lateinit var coverContext: CoverContext
//    private lateinit var bytes: Map<String, ByteArray>
//
//    @Setup
//    fun setup() {
//        session = (1..100).map {
//            ActiveSession("id$it", "type$it").apply {
//                addAll(
//                    (1..10_000).map {
//                        ExecClassData(className = "$it",
//                            probes = (1..100).map { i -> i % 2 > 0 }.toBitSet())
//                    }
//                )
//                setTestRun(TestRun("$it", 10L, 12L, (1..1000).map { TestInfo("$it", TestResult.PASSED, 10L, 11L) }))
//            }
//        }.groupBy(Session::testType)
//        coverContext = CoverContext("JAVA",
//            PackageTree(),
//            emptyList(),
//            DiffMethods(),
//            (1..1000).associate { "$it" to it.toLong() },
//            build = CachedBuild("0.1.0"))
//        bytes =
//            (1..1000).associate { "$it" to File("D:\\GitHub\\Drill\\test2code-plugin\\tests\\classesBytes").readBytes() }
//    }
//
//    Change fun access modifier
//    @Benchmark
//    fun kotlin() {
//        session.bundlesByTests(coverContext, bytes, null)
//    }
}
