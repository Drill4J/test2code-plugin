/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.plugins.test2code.api.routes

import io.ktor.locations.*

@OptIn(KtorExperimentalLocationsAPI::class)
@Suppress("unused")
class Routes {

    @Location("/active-scope")
    class ActiveScope {

        @Location("/summary/active-sessions")
        class ActiveSessionSummary(val activeScope: ActiveScope)

        @Location("/active-sessions")
        class ActiveSessions(val activeScope: ActiveScope)
    }

    @Location("/build")
    class Build {
        @Location("/coverage")
        class Coverage(val build: Build) {
            @Location("/packages")
            class Packages(val coverage: Coverage) {
                @Location("/{path}")
                class Package(val path: String, val packages: Packages)
            }
        }

        @Location("/tests/associatedWith/{id}")
        class AssociatedTests(val id: String, val build: Build)

        @Location("/methods")
        class Methods(val build: Build) {
            @Location("/all")
            class All(val methods: Methods)

            @Location("/new")
            class New(val methods: Methods)

            @Location("/modified")
            class Modified(val methods: Methods)

            @Location("/deleted")
            class Deleted(val methods: Methods)

            @Location("/unaffected")
            class Unaffected(val methods: Methods)
        }

        @Location("/summary")
        class Summary(val build: Build) {
            @Location("/tests")
            class Tests(val parent: Summary) {
                @Location("/all")
                class All(val tests: Tests)

                @Location("/by-type")
                class ByType(val tests: Tests)
            }

            @Location("/tests-to-run")
            class TestsToRun(val parent: Summary)
        }

        @Location("/tests")
        class Tests(val build: Build)


        @Location("/risks")
        class Risks(val build: Build)

        @Location("/tests-to-run")
        class TestsToRun(val build: Build) {
            @Location("/parent-stats")
            class ParentTestsToRunStats(val parent: TestsToRun)
        }

        @Location("/tests/{testId}/methods")
        class MethodsCoveredByTest(val testId: String, val build: Build) {
            @Location("/summary")
            class Summary(val test: MethodsCoveredByTest)

            @Location("/all")
            class All(val test: MethodsCoveredByTest)

            @Location("/new")
            class New(val test: MethodsCoveredByTest)

            @Location("/modified")
            class Modified(val test: MethodsCoveredByTest)

            @Location("/unaffected")
            class Unaffected(val test: MethodsCoveredByTest)
        }

        @Location("/scopes")
        class Scopes(val build: Build) {
            @Location("/finished")
            class FinishedScopes(val scopes: Scopes)

            @Location("/{scopeId}")
            class Scope(val scopeId: String, val scopes: Scopes) {
                @Location("/coverage")
                class Coverage(val scope: Scope) {
                    @Location("/packages")
                    class Packages(val coverage: Coverage) {
                        @Location("/{path}")
                        class Package(val path: String, val packages: Packages)
                    }
                }

                @Location("/tests/associatedWith/{id}")
                class AssociatedTests(val id: String, val scope: Scope)

                @Location("/methods")
                class Methods(val scope: Scope)

                @Location("/summary")
                class Summary(val build: Scope) {
                    @Location("/tests")
                    class Tests(val parent: Summary) {
                        @Location("/all")
                        class All(val tests: Tests)

                        @Location("/by-type")
                        class ByType(val tests: Tests)
                    }
                }

                @Location("/tests")
                class Tests(val scope: Scope)
                
                @Location("/tests/{testId}/methods")
                class MethodsCoveredByTest(val testId: String, val scope: Scope) {
                    @Location("/summary")
                    class Summary(val test: MethodsCoveredByTest)

                    @Location("/all")
                    class All(val test: MethodsCoveredByTest)

                    @Location("/new")
                    class New(val test: MethodsCoveredByTest)

                    @Location("/modified")
                    class Modified(val test: MethodsCoveredByTest)

                    @Location("/unaffected")
                    class Unaffected(val test: MethodsCoveredByTest)
                }
            }

        }
    }

    @Location("/data")
    class Data {
        @Location("/baseline")
        class Baseline(val build: Data)

        @Location("/parent")
        class Parent(val parent: Data)

        @Location("/build")
        class Build(val parent: Data)

        @Location("/stats")
        class Stats(val parent: Data)

        @Location("/tests")
        class Tests(val parent: Data)

        @Location("/tests-to-run")
        class TestsToRun(val parent: Data)

        @Location("/recommendations")
        class Recommendations(val parent: Data)

        @Location("/quality-gate-settings")
        class QualityGateSettings(val parent: Data)

        @Location("/quality-gate")
        class QualityGate(val parent: Data)
    }

    @Location("/group")
    class Group {
        @Location("/summary")
        class Summary(val group: Group)

        @Location("/active-sessions")
        class ActiveSessions(val parent: Group)

        @Location("/data")
        class Data(val group: Group) {
            @Location("/tests")
            class Tests(val parent: Data)

            @Location("/tests-to-run")
            class TestsToRun(val parent: Data)

            @Location("/recommendations")
            class Recommendations(val parent: Data)
        }
    }
}
