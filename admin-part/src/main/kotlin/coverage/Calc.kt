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
package com.epam.drill.plugins.test2code.coverage

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.util.*

internal fun Sequence<ExecClassData>.bundle(
    bundleName: String,
    preparedData: MutableList<PackageCounter>,
    tree: PackageTree,
): BundleCounter = run {
    val associatedPackage = preparedData.associateBy { it }
    val probeCounts: Map<String, Int> = tree.packages.run {
        flatMap { it.classes }.associateBy({ fullClassname(it.path, it.name) }) { it.totalCount }
    }
    val probesByClasses: Map<String, List<Boolean>> = filter {
        it.className in probeCounts
    }.groupBy(ExecClassData::className).mapValues { (className, execDataList) ->
        val initialProbe = BooleanArray(probeCounts.getValue(className)) { false }.toList()
        execDataList.map(ExecClassData::probes).fold(initialProbe) { acc, probes ->
            acc.merge(probes.toList())
        }
    }
    val classMethods = tree.packages.flatMap { it.classes }.associate {
        fullClassname(it.path, it.name) to it.methods
    }
    val covered = probesByClasses.values.sumOf { probes -> probes.count { it } }
    val packages = probesByClasses.keys.groupBy { classPath(it) }.map { (pkgName, classNames) ->
        val packageName = pkgName.weakIntern()
        val packageCounter = associatedPackage[PackageCounter(packageName)] ?: PackageCounter(packageName).also {
            preparedData.add(it)
        }
        val associatedClasses = packageCounter.classes.associateBy { it }
        val classes = classNames.map { fullClassname ->
            val classFullName = fullClassname.weakIntern()
            val classCounterKey = ClassCounter(path = packageName,
                name = classname(fullClassname),
                fullName = classFullName
            )
            val classBundle = associatedClasses[classCounterKey] ?: classCounterKey.also {
                packageCounter.classes.add(it)
            }
            val probes = probesByClasses.getValue(fullClassname)
            classBundle.bundleCount[bundleName] = ClassInfo(count = probes.toCount(), probes = probes)
            val associatedMethods = classBundle.methods.associateBy { it }
            classMethods.getValue(fullClassname).map {
                val methodProbes = probes.slice(it.probeRange)
                val sign = signature(fullClassname, it.name, it.desc)
                val methodCounterKey = MethodCounter(
                    it.name, it.desc, it.decl,
                    sign = sign,
                    fullName = fullMethodName(fullClassname, it.name, it.desc),
                )
                val methodBundle = associatedMethods[methodCounterKey] ?: methodCounterKey.also {
                    classBundle.methods.add(it)
                }
                methodBundle.bundleCount[bundleName] = methodProbes.toCount()
            }
            classCounterKey
        }
        packageCounter.bundleCount[bundleName] = PackageInfo(
            count = classNames.flatMap { probesByClasses[it] ?: emptyList() }.toCount(),
            classCount = Count(
                classNames.count { name -> probesByClasses.getValue(name).any { it } },
                classNames.size
            ),
            methodCount = Count(
                classes.sumOf { c -> c.methods.count { (it.bundleCount[bundleName]?.covered ?: 0) > 0 } },
                classes.sumOf { it.methods.count() }
            ),
        )
        packageCounter
    }
    BundleCounter(
        name = bundleName,
        count = Count(covered, tree.totalCount),
        methodCount = packages.run {
            Count(sumOf { it.bundleCount[bundleName]?.methodCount?.covered ?: 0 },
                sumOf { it.bundleCount[bundleName]?.methodCount?.total ?: 0 })
        },
        classCount = packages.run {
            Count(sumOf { it.bundleCount[bundleName]?.classCount?.covered ?: 0 },
                sumOf { it.bundleCount[bundleName]?.classCount?.total ?: 0 })
        },
        packageCount = packages.run {
            Count(count { (it.bundleCount[bundleName]?.classCount?.covered ?: 0) > 0 }, count())
        },
    )
}
