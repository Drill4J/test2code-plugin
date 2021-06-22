package com.epam.drill.perf

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import kotlinx.coroutines.*
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.annotations.Scope
import java.util.concurrent.*


@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(2)
class ToCovered {

    private lateinit var methods: List<Method>

    private lateinit var bundleCounter: Map<TypedTest, BundleCounter>

    //private val context = CoverContext()

    @Setup
    fun setUp() {
        bundleCounter = (1..1_000).associate {
            TypedTest("Name$it", "type") to BundleCounter(
                "name",
                Count(it, it),
                Count(it, it),
                Count(it, it),
                Count(it, it),
                listOf(
                    PackageCounter(
                        "name ${it % 10}",
                        Count(it, it),
                        Count(it, it),
                        Count(it, it),
                        listOf(ClassCounter("Path ${it % 10}",
                            "Class $it",
                            Count(it, it),
                            (1..4_000).map {
                                MethodCounter("Method $it",
                                    "desc $it",
                                    "desc $it",
                                    "Method ${it}desc $it",
                                    "Class ${it}:Method ${it}desc $it",
                                    Count(it, it))
                            }, "")
                        ))
                )
            )
        }
        methods = (1..4_000).map {
            Method(
                "Class $it", "Method $it",
                "desc $it",
                "$it",
            )
        }
    }

    @Benchmark
    fun toCoverMapDefault() = runBlocking {
        AsyncJobDispatcher.launch {
            bundleCounter.values.forEach {
                val coveredMethods = methods.toCoverMap(it, true)
                val all = coveredMethods.values.toList()
//                val modified = coveredMethods.filterValues { it  }
//                val new = coveredMethods.filterValues { it in context.methodChanges.new }
//                val unaffected = coveredMethods.filterValues { it in context.methodChanges.unaffected }
            }
        }.join()
    }

    @Benchmark
    fun toCoverMapDefaultParallel() {
        bundleCounter.values.forEach {
            val coveredMethods = methods.toCoverMapStream(it, true)
            val all = coveredMethods.values.toList()
//                val modified = coveredMethods.filterValues { it  }
//                val new = coveredMethods.filterValues { it in context.methodChanges.new }
//                val unaffected = coveredMethods.filterValues { it in context.methodChanges.unaffected }

        }
    }

    @Benchmark
    fun toCoverMapByMax() {
        bundleCounter.values.forEach {
            val associate = methods.associate { it.key to it.toCovered() }
            associate.toCoverMap(it, true)
        }
    }

    @Benchmark
    fun toCoverMapDefaultPP() = runBlocking {
        AsyncJobDispatcher.launch {
            bundleCounter.values.forEach {
                val coveredMethods = methods.toCoverMap(it, true)
                val all = coveredMethods.values.toList()
//                val modified = coveredMethods.filterValues { it  }
//                val new = coveredMethods.filterValues { it in context.methodChanges.new }
//                val unaffected = coveredMethods.filterValues { it in context.methodChanges.unaffected }
            }
        }.join()
    }

    @Benchmark
    fun toCoverMapDefaultParallelPP() {
        bundleCounter.values.forEach {
            val coveredMethods = methods.toCoverMapStream(it, true)
            val all = coveredMethods.values.toList()
//                val modified = coveredMethods.filterValues { it  }
//                val new = coveredMethods.filterValues { it in context.methodChanges.new }
//                val unaffected = coveredMethods.filterValues { it in context.methodChanges.unaffected }

        }
    }

    @Benchmark
    fun toCoverMapByMaxPP() {
        bundleCounter.values.parallelStream().forEach {
            val associate = methods.associate { it.key to it.toCovered() }
            associate.toCoverMap(it, true)
        }
    }


}
