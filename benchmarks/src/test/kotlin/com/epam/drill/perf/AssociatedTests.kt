package com.epam.drill.perf

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.annotations.Scope
import java.util.concurrent.*

@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(2)
class AssociatedTests {

    //  Change fun access modifier
    private lateinit var bundleCounter: Map<CoverageKey, List<TypedTest>>

//    @Setup
//    fun setUp() {
//        bundleCounter = (1..1_200).associate {
//            TypedTest("Name$it", "type") to BundleCounter(
//                "name",
//                Count(it, it),
//                Count(it, it),
//                Count(it, it),
//                Count(it, it),
//                (1..2_000).map {
//                    PackageCounter(
//                        "name ${it % 10}",
//                        Count(it, it),
//                        Count(it, it),
//                        Count(it, it),
//                        listOf(ClassCounter("Path ${it % 10}",
//                            "name $it",
//                            Count(it, it),
//                            listOf(MethodCounter("name ${it % 10} ",
//                                "desc $it",
//                                "desc $it",
//                                "$it",
//                                "$it",
//                                Count(it, it))), "")
//                        ))
//                }
//            )
//        }.associatedTests(false)
//    }


//    @Benchmark
//    fun streamApi() {
//        bundleCounter.getAssociatedTests()
//    }

}
