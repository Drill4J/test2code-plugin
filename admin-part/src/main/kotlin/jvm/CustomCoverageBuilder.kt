package com.epam.drill.plugins.test2code.jvm

import org.jacoco.core.analysis.*
import org.jacoco.core.internal.analysis.*
import java.util.*
import java.util.concurrent.*

// TODO Can be removed by using reflation
class CustomCoverageBuilder : ICoverageVisitor {

    private val classes: MutableMap<String, IClassCoverage> = ConcurrentHashMap()

    private val sourcefiles: MutableMap<String, ISourceFileCoverage> = ConcurrentHashMap()

    /**
     * Returns all class nodes currently contained in this builder.
     *
     * @return all class nodes
     */
    fun getClasses(): Collection<IClassCoverage?> {
        return Collections.unmodifiableCollection(classes.values)
    }

    /**
     * Returns all source file nodes currently contained in this builder.
     *
     * @return all source file nodes
     */
    fun getSourceFiles(): Collection<ISourceFileCoverage?> {
        return Collections.unmodifiableCollection(sourcefiles.values)
    }

    /**
     * Creates a bundle from all nodes currently contained in this bundle.
     *
     * @param name
     * Name of the bundle
     * @return bundle containing all classes and source files
     */
    fun getBundle(name: String?): IBundleCoverage {
        return BundleCoverageImpl(name, classes.values,
            sourcefiles.values)
    }

    /**
     * Returns all classes for which execution data does not match.
     *
     * @see IClassCoverage.isNoMatch
     * @return collection of classes with non-matching execution data
     */
    fun getNoMatchClasses(): Collection<IClassCoverage> {
        val result: MutableCollection<IClassCoverage> = ArrayList()
        for (c in classes.values) {
            if (c.isNoMatch) {
                result.add(c)
            }
        }
        return result
    }

    // === ICoverageVisitor ===

    // === ICoverageVisitor ===
    override fun visitCoverage(coverage: IClassCoverage) {
        val name = coverage.name
        val dup = classes.put(name, coverage)
        if (dup != null) {
            check(dup.id == coverage.id) { "Can't add different class with same name: $name" }
        } else {
            val source = coverage.sourceFileName
            if (source != null) {
                val sourceFile = getSourceFile(source,
                    coverage.packageName)
                sourceFile.increment(coverage)
            }
        }
    }

    private fun getSourceFile(
        filename: String,
        packagename: String,
    ): SourceFileCoverageImpl {
        val key = "$packagename/$filename"
        var sourcefile = sourcefiles[key] as SourceFileCoverageImpl?
        if (sourcefile == null) {
            sourcefile = SourceFileCoverageImpl(filename, packagename)
            sourcefiles[key] = sourcefile
        }
        return sourcefile
    }

}
