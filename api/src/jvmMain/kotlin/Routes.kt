package com.epam.drill.plugins.test2code.api.routes

import io.ktor.locations.*

@OptIn(KtorExperimentalLocationsAPI::class)
@Suppress("unused")
class Routes {

    @Location("/active-scope")
    object ActiveScope

    @Location("/active-sessions")
    object ActiveSessionStats

    @Location("/active/sessions")
    object ActiveSessions

    @Location("/scopes")
    object Scopes


    @Location("/scope/{scopeId}")
    class Scope(val scopeId: String) {

        @Location("/coverage")
        class Coverage(val scope: Scope) {
            @Location("/packages")
            class Packages(val coverage: Coverage) {
                @Location("/{path}")
                class Package(val path: String, val packages: Packages)
            }
        }

        @Location("/associated-tests")
        class AssociatedTests(val scope: Scope)

        @Location("/methods")
        class Methods(val scope: Scope) {
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
        class Summary(val build: Scope) {
            @Location("/tests")
            class Tests(val parent: Summary) {
                @Location("/all")
                class All(val tests: Tests)

                @Location("/by-type")
                class ByType(val tests: Tests)
            }
        }

        @Location("/tests-usages")
        class TestsUsages(val scope: Scope)

        //TODO remove after changes on the frontend
        @Location("/tests/covered-methods")
        class CoveredMethodsByTest(val scope: Scope)

        //TODO remove after changes on the frontend
        @Location("/test-types/covered-methods")
        class CoveredMethodsByType(val scope: Scope)

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

        @Location("/test-types/{testType}/methods")
        class MethodsCoveredByTestType(val testType: String, val scope: Scope) {
            @Location("/summary")
            class Summary(val type: MethodsCoveredByTestType)

            @Location("/all")
            class All(val type: MethodsCoveredByTestType)

            @Location("/new")
            class New(val type: MethodsCoveredByTestType)

            @Location("/modified")
            class Modified(val type: MethodsCoveredByTestType)

            @Location("/unaffected")
            class Unaffected(val type: MethodsCoveredByTestType)
        }
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

        @Location("/associated-tests")
        class AssociatedTests(val build: Build)

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

            @Location("/tests-to-run/by-type")
            class TestsToRun(val parent: Summary)
        }

        @Location("/tests-usages")
        class TestsUsages(val build: Build)

        @Location("/risks")
        class Risks(val build: Build)

        @Location("/tests-to-run")
        class TestsToRun(val build: Build)

        //TODO remove after changes on the frontend
        @Location("/tests/covered-methods")
        class CoveredMethodsByTest(val build: Build)

        //TODO remove after changes on the frontend
        @Location("/test-types/covered-methods")
        class CoveredMethodsByType(val build: Build)

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

        @Location("/test-types/{testType}/methods")
        class MethodsCoveredByTestType(val testType: String, val build: Build) {
            @Location("/summary")
            class Summary(val type: MethodsCoveredByTestType)

            @Location("/all")
            class All(val type: MethodsCoveredByTestType)

            @Location("/new")
            class New(val type: MethodsCoveredByTestType)

            @Location("/modified")
            class Modified(val type: MethodsCoveredByTestType)

            @Location("/unaffected")
            class Unaffected(val type: MethodsCoveredByTestType)
        }
    }

    @Location("/data")
    class Data {
        @Location("/build")
        class Build(val parent: Data)

        @Location("/stats")
        class Stats(val parent: Data)

        @Location("/tests-to-run")
        class TestsToRun(val parent: Data)

        @Location("/recommendations")
        class Recommendations(val parent: Data)

        @Location("/quality-gate-settings")
        class QualityGateSettings(val parent: Data)

        @Location("/quality-gate")
        class QualityGate(val parent: Data)
    }

    @Location("/service-group")
    class ServiceGroup {
        @Location("/summary")
        class Summary(val serviceGroup: ServiceGroup)

        @Location("/active/sessions")
        class ActiveSessions(val serviceGroup: ServiceGroup)

        @Location("/data")
        class Data(val serviceGroup: ServiceGroup) {
            @Location("/tests-to-run")
            class TestsToRun(val parent: Data)

            @Location("/recommendations")
            class Recommendations(val parent: Data)
        }
    }
}
