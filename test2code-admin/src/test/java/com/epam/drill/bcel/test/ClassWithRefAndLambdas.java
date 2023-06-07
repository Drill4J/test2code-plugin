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
package com.epam.drill.bcel.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClassWithRefAndLambdas {

    public static Map<String, String> methodsMap = new HashMap<String, String>() {{
        put("firstMethod", "println");
        put("secondMethod", "lambda$secondMethod$0");
    }};

    void firstMethod(List<String> strings) {
        strings.stream().forEach(System.out::println);
    }

    void secondMethod(List<String> strings) {
        strings.stream().map(str -> str + "2").collect(Collectors.toList());
    }
}
