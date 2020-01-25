package com.epam.drill.plugins.test2code


val BuildMethods.allModifiedMethods: MethodsInfo
    get() = (modifiedBodyMethods.methods +
        modifiedDescMethods.methods +
        modifiedNameMethods.methods).run {
        MethodsInfo(count(), count { it.coverageRate != CoverageRate.MISSED }, this)
    }


fun BuildMethods.withDeletedMethodCount(
    buildVersion: String,
    testsAssociatedWithBuildTests: BuildTests
) = copy(
    deletedCoveredMethodsCount = testsAssociatedWithBuildTests.deletedCoveredMethodsCount(
        buildVersion,
        deletedMethods.methods
    )
)
