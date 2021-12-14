package com.epam;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Java17TestClass {

    void stringTransform() {
        Object obj = """
                DrillTestJava17: \
                Class with main features of java version newest then 11;
                """;
        if (obj instanceof String text && !text.isBlank()) {
            String transformed = text.transform(value ->
                    new StringBuilder(value).reverse().toString()
            );
            System.out.println(transformed);
        }
    }

    void teeingCollector() {
        double mean = Stream.of(1, 2, 3, 4, 5)
                .collect(
                        Collectors.teeing(
                                Collectors.summingDouble(i -> i),
                                Collectors.counting(),
                                (sum, count) -> sum / count
                        )
                );
    }

    void switchCase() {
        DayOfWeek dayOfWeek = LocalDate.now().getDayOfWeek();
        String typeOfDay = switch (dayOfWeek) {
            case MONDAY -> {
                var value = new Random().nextInt();
                yield String.valueOf(value * new Random().nextInt());
            }
            case TUESDAY, WEDNESDAY, THURSDAY, FRIDAY -> "Working Day";
            case SATURDAY, SUNDAY -> "Day Off";
            default -> throw new IllegalArgumentException(String.valueOf(dayOfWeek));
        };
        System.out.println(typeOfDay);
    }

    void concatenationOfConstants(){
        var value = new Random().nextLong();
        System.out.println("String concatenations" + value);
    }
}