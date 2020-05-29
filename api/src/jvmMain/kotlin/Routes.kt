@file:OptIn(KtorExperimentalLocationsAPI::class)

package com.epam.drill.plugins.test2code.api.routes

import io.ktor.locations.*

class Routes {

    @Location("/active-scope")
    object ActiveScope

    @Location("/active-sessions")
    object ActiveSessions

    @Location("/scopes")
    object Scopes


    @Location("/scope/{scopeId}")
    class Scope(val scopeId: String) {

        @Location("/coverage")
        class Coverage(val scope: Scope)

        @Location("/associated-tests")
        class AssociatedTests(val scope: Scope)

        @Location("/coverage-by-packages")
        class CoverageByPackages(val scope: Scope)

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

        @Location("/tests-usages")
        class TestsUsages(val scope: Scope)

        @Location("/tests/covered-methods")
        class MethodsCoveredByTest(val scope: Scope)

        @Location("/test-types/covered-methods")
        class MethodsCoveredByTestType(val scope: Scope)
    }

    @Location("/build")
    class Build {
        @Location("/coverage")
        class Coverage(val build: Build)

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

        @Location("/tests-usages")
        class TestsUsages(val build: Build)

        @Location("/coverage-by-packages")
        class CoverageByPackages(val build: Build)

        @Location("/risks")
        class Risks(val build: Build)

        @Location("/tests-to-run")
        class TestsToRun(val build: Build)

        @Location("/tests/covered-methods")
        class MethodsCoveredByTest(val build: Build)

        @Location("/test-types/covered-methods")
        class MethodsCoveredByTestType(val build: Build)
    }

    @Location("/service-group")
    class ServiceGroup {
        @Location("/summary")
        class Summary(val serviceGroup: ServiceGroup)
    }
}
