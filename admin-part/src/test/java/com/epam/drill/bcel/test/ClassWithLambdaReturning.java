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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ClassWithLambdaReturning {
    public static Map<String, String> methodsMap = new HashMap<String, String>() {{
        put("method", "lambda$method$1");
        put("lambda$method$1", "lambda$null$0");
    }};

    Function<String, Set<Integer>> method() {
        return (value) -> {
            String[] split = value.split(":");
            Set<Integer> sum = Arrays.stream(split).map(str -> {
                        Integer integer = Integer.getInteger(str);
                        return integer + 10;
                    }
            ).collect(Collectors.toSet());
            return sum;
        };
    }

    void firstMethod() {
        method().apply("5:8");
    }
}
