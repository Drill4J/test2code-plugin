package com.epam.drill.plugins.test2code.lambda;

import java.util.List;
import java.util.Random;

public class ClassWithInstanceType {
    void method1(List<String> strings) {
        Double valuer = new Random().nextDouble();
        strings.stream().forEach(System.out::printf);
    }

    void method2(List<String> strings) {
        Integer valuer = new Random().nextInt();
        strings.stream().forEach(System.out::printf);
    }
}
