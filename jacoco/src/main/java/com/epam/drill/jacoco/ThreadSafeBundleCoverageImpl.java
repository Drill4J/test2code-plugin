package com.epam.drill.jacoco;

import org.jacoco.core.analysis.*;
import org.jacoco.core.internal.analysis.PackageCoverageImpl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class ThreadSafeBundleCoverageImpl extends CoverageNodeImpl
        implements IBundleCoverage {

    private final Collection<IPackageCoverage> packages;

    /**
     * Creates a new instance of a bundle with the given name.
     *
     * @param name     name of this bundle
     * @param packages collection of all packages contained in this bundle
     */
    public ThreadSafeBundleCoverageImpl(final String name,
                                        final Collection<IPackageCoverage> packages) {
        super(ICoverageNode.ElementType.BUNDLE, name);
        this.packages = packages;
        increment(packages);
    }

    /**
     * Creates a new instance of a bundle with the given name. The packages are
     * calculated from the given classes and source files.
     *
     * @param name        name of this bundle
     * @param classes     all classes in this bundle
     * @param sourcefiles all source files in this bundle
     */
    public ThreadSafeBundleCoverageImpl(final String name,
                                        final Collection<IClassCoverage> classes,
                                        final Collection<ISourceFileCoverage> sourcefiles) {
        this(name, groupByPackage(classes, sourcefiles));
    }

    private static Collection<IPackageCoverage> groupByPackage(
            final Collection<IClassCoverage> classes,
            final Collection<ISourceFileCoverage> sourcefiles) {
        final Map<String, Collection<IClassCoverage>> classesByPackage = new ConcurrentHashMap<>();
        for (final IClassCoverage c : classes) {
            addByName(classesByPackage, c.getPackageName(), c);
        }

        final Map<String, Collection<ISourceFileCoverage>> sourceFilesByPackage = new ConcurrentHashMap<>();
        for (final ISourceFileCoverage s : sourcefiles) {
            addByName(sourceFilesByPackage, s.getPackageName(), s);
        }

        final Set<String> packageNames = ConcurrentHashMap.newKeySet();
        packageNames.addAll(classesByPackage.keySet());
        packageNames.addAll(sourceFilesByPackage.keySet());

        final Collection<IPackageCoverage> result = Collections.synchronizedList(new ArrayList<>());
        for (final String name : packageNames) {
            Collection<IClassCoverage> c = classesByPackage.get(name);
            if (c == null) {
                c = Collections.emptyList();
            }
            Collection<ISourceFileCoverage> s = sourceFilesByPackage.get(name);
            if (s == null) {
                s = Collections.emptyList();
            }
            result.add(new PackageCoverageImpl(name, c, s));
        }
        return result;
    }

    private static <T> void addByName(final Map<String, Collection<T>> map,
                                      final String name, final T value) {
        Collection<T> list = map.get(name);
        if (list == null) {
            list = new ArrayList<T>();
            map.put(name, list);
        }
        list.add(value);
    }

    // === IBundleCoverage implementation ===

    public Collection<IPackageCoverage> getPackages() {
        return packages;
    }

}
