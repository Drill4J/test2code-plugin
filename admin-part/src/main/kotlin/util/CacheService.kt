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
package com.epam.drill.plugins.test2code.util

/**
 * The cache is used for real-time coverage calculations.
 * Enabling and disabling depends on the system property: "drill.plugins.test2code.features.realtime.cache".
 * Only tests in the finalize state are added to the cache
 */
fun <K, V> getCache(enabled: Boolean = true): AtomicCache<K, V>? = if (enabled) AtomicCache() else null
