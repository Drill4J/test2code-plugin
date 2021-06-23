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
                                    Count(it, it))
                            })
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
                methods.toCoverMap(it, true)
            }
        }.join()
    }


}
