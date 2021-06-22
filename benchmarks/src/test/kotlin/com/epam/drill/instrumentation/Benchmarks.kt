///**
// * Copyright 2020 EPAM Systems
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.epam.drill.instrumentation
//
//import com.epam.drill.instrumentation.data.*
//import com.epam.drill.plugins.test2code.*
//import org.openjdk.jmh.annotations.*
//import org.openjdk.jmh.annotations.Scope
//import java.util.concurrent.*
//
//@State(Scope.Benchmark)
//@Fork(1)
//@Warmup(iterations = 3)
//@Measurement(iterations = 5, timeUnit = TimeUnit.MILLISECONDS)
//class Benchmarks {
//
//    @Benchmark
//    @BenchmarkMode(Mode.AverageTime)
//    fun cyclesWithInstrument() {
//        val instrumentation = InstrumentationForTest(InvokeCycles::class)
//        instrumentation.runClass()
//    }
//
//    @Benchmark
//    @BenchmarkMode(Mode.AverageTime)
//    fun cyclesWithoutInstrument() {
//        val instrumentation = InstrumentationForTest(InvokeCycles::class)
//        instrumentation.runNonInstrumentedClass()
//    }
//
//    @Benchmark
//    @Measurement(iterations = 1)
//    @BenchmarkMode(Mode.AverageTime)
//    fun conditionsWithInstrument() {
//        val instrumentation = InstrumentationForTest(InvokeConditions::class)
//        instrumentation.runClass()
//    }
//
//    @Benchmark
//    @Measurement(iterations = 1)
//    @BenchmarkMode(Mode.AverageTime)
//    fun conditionsWithoutInstrument() {
//        val instrumentation = InstrumentationForTest(InvokeConditions::class)
//        instrumentation.runNonInstrumentedClass()
//    }
//
//}
