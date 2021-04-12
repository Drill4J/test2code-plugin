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
package com.data;

import java.util.Random;

public class BigClazz {

    public long manyForInFor(long start) {
        long sum = start;
        Random random = new Random();
        int firstRange = 2;
        int toMin = 7;
        int toMax = 2;
        for (int i10 = random.nextInt(firstRange); i10 <= random.nextInt(toMax) + toMin; i10++) {
            sum += i10;
            for (int i9 = random.nextInt(firstRange); i9 <= random.nextInt(toMax) + toMin; i9++) {
                sum += i9;
                for (int i8 = random.nextInt(firstRange); i8 <= random.nextInt(toMax) + toMin; i8++) {
                    sum += i8;
                    for (int i7 = random.nextInt(firstRange); i7 <= random.nextInt(toMax) + toMin; i7++) {
                        sum += i7;
                        for (int i6 = random.nextInt(firstRange); i6 <= random.nextInt(toMax) + toMin; i6++) {
                            sum += i6;
                            for (int i5 = random.nextInt(firstRange); i5 <= random.nextInt(toMax) + toMin; i5++) {
                                sum += i5;
                                for (int i4 = random.nextInt(firstRange); i4 <= random.nextInt(toMax) + toMin; i4++) {
                                    sum += i4;
                                    for (int i3 = random.nextInt(firstRange); i3 <= random.nextInt(toMax) + toMin; i3++) {
                                        sum += i3;
                                        for (int i2 = random.nextInt(firstRange); i2 <= random.nextInt(toMax) + toMin; i2++) {
                                            sum += i2;
                                            for (int i1 = random.nextInt(firstRange); i1 <= random.nextInt(toMax) + toMin; i1++) {
                                                sum += i1;

                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return sum;
    }

    public int methodIfAndInFor(int s) {
        int sum = s;
        Random random = new Random();
        int firstRange = 3;
        int toMin = 100_000;
        int toMax = 100;
        for (int i = random.nextInt(firstRange); i <= random.nextInt(toMax) + toMin; i++) {
            sum += methodIfWithAnd(s);
            sum += methodDiffConditions(s);
            sum += methodIfAndAndOrOr(s);
            sum += methodSimpleIfAndAndOrOr(s);
            sum += methodWithCondition();
        }
        return sum;
    }

    int methodWithCondition() {
        int sum = 0;
        Random random = new Random();
        if (isaBoolean(random)) {
            sum += 110;
            if ((int) (Math.random() * 10000) % 2 == 0 | random.nextInt(100) + 1 > random.nextInt(200) | random.nextInt(100) + 2 > random.nextInt(200) | random.nextInt(100) + 3 > random.nextInt(200) | random.nextInt(100) + 4 > random.nextInt(200) | random.nextInt(100) + 5 > random.nextInt(200) | random.nextInt(100) + 6 > random.nextInt(200) | random.nextInt(100) + 7 > random.nextInt(200) | random.nextInt(100) + 8 > random.nextInt(200) | random.nextInt(100) + 9 > random.nextInt(200) | random.nextInt(100) + 10 > random.nextInt(200) | random.nextInt(100) + 11 > random.nextInt(200) | random.nextInt(100) + 12 > random.nextInt(200) | random.nextInt(100) + 13 > random.nextInt(200) | random.nextInt(100) + 14 > random.nextInt(200) | random.nextInt(100) + 15 > random.nextInt(200) | random.nextInt(100) + 16 > random.nextInt(200) | random.nextInt(100) + 17 > random.nextInt(200) | random.nextInt(100) + 18 > random.nextInt(200) | random.nextInt(100) >= random.nextInt(200)) {
                sum += 19;
                if ((int) (Math.random() * 10000) % 2 == 0 | random.nextInt(100) + 1 > random.nextInt(200) | random.nextInt(100) + 2 > random.nextInt(200) | random.nextInt(100) + 3 > random.nextInt(200) | random.nextInt(100) + 4 > random.nextInt(200) | random.nextInt(100) + 5 > random.nextInt(200) | random.nextInt(100) + 6 > random.nextInt(200) | random.nextInt(100) + 7 > random.nextInt(200) | random.nextInt(100) + 8 > random.nextInt(200) | random.nextInt(100) + 9 > random.nextInt(200) | random.nextInt(100) + 10 > random.nextInt(200) | random.nextInt(100) + 11 > random.nextInt(200) | random.nextInt(100) + 12 > random.nextInt(200) | random.nextInt(100) + 13 > random.nextInt(200) | random.nextInt(100) + 14 > random.nextInt(200) | random.nextInt(100) + 15 > random.nextInt(200) | random.nextInt(100) + 16 > random.nextInt(200) | random.nextInt(100) + 17 > random.nextInt(200) | random.nextInt(100) >= random.nextInt(200)) {
                    sum += 18;
                    if ((int) (Math.random() * 10000) % 2 == 0 | random.nextInt(100) + 1 > random.nextInt(200) | random.nextInt(100) + 2 > random.nextInt(200) | random.nextInt(100) + 3 > random.nextInt(200) | random.nextInt(100) + 4 > random.nextInt(200) | random.nextInt(100) + 5 > random.nextInt(200) | random.nextInt(100) + 6 > random.nextInt(200) | random.nextInt(100) + 7 > random.nextInt(200) | random.nextInt(100) + 8 > random.nextInt(200) | random.nextInt(100) + 9 > random.nextInt(200) | random.nextInt(100) + 10 > random.nextInt(200) | random.nextInt(100) + 11 > random.nextInt(200) | random.nextInt(100) + 12 > random.nextInt(200) | random.nextInt(100) + 13 > random.nextInt(200) | random.nextInt(100) + 14 > random.nextInt(200) | random.nextInt(100) + 15 > random.nextInt(200) | random.nextInt(100) + 16 > random.nextInt(200) | random.nextInt(100) >= random.nextInt(200)) {
                        sum += 17;
                        if ((int) (Math.random() * 10000) % 2 == 0 | random.nextInt(100) + 1 > random.nextInt(200) | random.nextInt(100) + 2 > random.nextInt(200) | random.nextInt(100) + 3 > random.nextInt(200) | random.nextInt(100) + 4 > random.nextInt(200) | random.nextInt(100) + 5 > random.nextInt(200) | random.nextInt(100) + 6 > random.nextInt(200) | random.nextInt(100) + 7 > random.nextInt(200) | random.nextInt(100) + 8 > random.nextInt(200) | random.nextInt(100) + 9 > random.nextInt(200) | random.nextInt(100) + 10 > random.nextInt(200) | random.nextInt(100) + 11 > random.nextInt(200) | random.nextInt(100) + 12 > random.nextInt(200) | random.nextInt(100) + 13 > random.nextInt(200) | random.nextInt(100) + 14 > random.nextInt(200) | random.nextInt(100) + 15 > random.nextInt(200) | random.nextInt(100) >= random.nextInt(200)) {
                            sum += 16;
                            if ((int) (Math.random() * 10000) % 2 == 0 | random.nextInt(100) + 1 > random.nextInt(200) | random.nextInt(100) + 2 > random.nextInt(200) | random.nextInt(100) + 3 > random.nextInt(200) | random.nextInt(100) + 4 > random.nextInt(200) | random.nextInt(100) + 5 > random.nextInt(200) | random.nextInt(100) + 6 > random.nextInt(200) | random.nextInt(100) + 7 > random.nextInt(200) | random.nextInt(100) + 8 > random.nextInt(200) | random.nextInt(100) + 9 > random.nextInt(200) | random.nextInt(100) + 10 > random.nextInt(200) | random.nextInt(100) + 11 > random.nextInt(200) | random.nextInt(100) + 12 > random.nextInt(200) | random.nextInt(100) + 13 > random.nextInt(200) | random.nextInt(100) + 14 > random.nextInt(200) | random.nextInt(100) >= random.nextInt(200)) {
                                sum += 15;
                                if ((int) (Math.random() * 10000) % 2 == 0 | random.nextInt(100) + 1 > random.nextInt(200) | random.nextInt(100) + 2 > random.nextInt(200) | random.nextInt(100) + 3 > random.nextInt(200) | random.nextInt(100) + 4 > random.nextInt(200) | random.nextInt(100) + 5 > random.nextInt(200) | random.nextInt(100) + 6 > random.nextInt(200) | random.nextInt(100) + 7 > random.nextInt(200) | random.nextInt(100) + 8 > random.nextInt(200) | random.nextInt(100) + 9 > random.nextInt(200) | random.nextInt(100) + 10 > random.nextInt(200) | random.nextInt(100) + 11 > random.nextInt(200) | random.nextInt(100) + 12 > random.nextInt(200) | random.nextInt(100) + 13 > random.nextInt(200) | random.nextInt(100) >= random.nextInt(200)) {
                                    sum += 14;
                                    if ((int) (Math.random() * 10000) % 2 == 0 | random.nextInt(100) + 1 > random.nextInt(200) | random.nextInt(100) + 2 > random.nextInt(200) | random.nextInt(100) + 3 > random.nextInt(200) | random.nextInt(100) + 4 > random.nextInt(200) | random.nextInt(100) + 5 > random.nextInt(200) | random.nextInt(100) + 6 > random.nextInt(200) | random.nextInt(100) + 7 > random.nextInt(200) | random.nextInt(100) + 8 > random.nextInt(200) | random.nextInt(100) + 9 > random.nextInt(200) | random.nextInt(100) + 10 > random.nextInt(200) | random.nextInt(100) + 11 > random.nextInt(200) | random.nextInt(100) + 12 > random.nextInt(200) | random.nextInt(100) >= random.nextInt(200)) {
                                        sum += 13;
                                        if ((int) (Math.random() * 10000) % 2 == 0 | random.nextInt(100) + 1 > random.nextInt(200) | random.nextInt(100) + 2 > random.nextInt(200) | random.nextInt(100) + 3 > random.nextInt(200) | random.nextInt(100) + 4 > random.nextInt(200) | random.nextInt(100) + 5 > random.nextInt(200) | random.nextInt(100) + 6 > random.nextInt(200) | random.nextInt(100) + 7 > random.nextInt(200) | random.nextInt(100) + 8 > random.nextInt(200) | random.nextInt(100) + 9 > random.nextInt(200) | random.nextInt(100) + 10 > random.nextInt(200) | random.nextInt(100) + 11 > random.nextInt(200) | random.nextInt(100) >= random.nextInt(200)) {
                                            sum += 12;
                                            if ((int) (Math.random() * 10000) % 2 == 0 | random.nextInt(100) + 1 > random.nextInt(200) | random.nextInt(100) + 2 > random.nextInt(200) | random.nextInt(100) + 3 > random.nextInt(200) | random.nextInt(100) + 4 > random.nextInt(200) | random.nextInt(100) + 5 > random.nextInt(200) | random.nextInt(100) + 6 > random.nextInt(200) | random.nextInt(100) + 7 > random.nextInt(200) | random.nextInt(100) + 8 > random.nextInt(200) | random.nextInt(100) + 9 > random.nextInt(200) | random.nextInt(100) + 10 > random.nextInt(200) | random.nextInt(100) >= random.nextInt(200)) {
                                                sum += 11;

                                            } else {
                                                sum -= 11;

                                            }
                                        } else {
                                            sum -= 12;

                                        }
                                    } else {
                                        sum -= 13;

                                    }
                                } else {
                                    sum -= 14;

                                }
                            } else {
                                sum -= 15;

                            }
                        } else {
                            sum -= 16;

                        }
                    } else {
                        sum -= 17;

                    }
                } else {
                    sum -= 18;

                }
            } else {
                sum -= 19;

            }
        } else {
            sum -= 110;

        }
        return sum;
    }

    private boolean isaBoolean(Random random) {
        return (int) (Math.random() * 10000) % 2 == 0 | random.nextInt(100) + 1 > random.nextInt(200) | random.nextInt(100) + 2 > random.nextInt(200) | random.nextInt(100) + 3 > random.nextInt(200) | random.nextInt(100) + 4 > random.nextInt(200) | random.nextInt(100) + 5 > random.nextInt(200) | random.nextInt(100) + 6 > random.nextInt(200) | random.nextInt(100) + 7 > random.nextInt(200) | random.nextInt(100) + 8 > random.nextInt(200) | random.nextInt(100) + 9 > random.nextInt(200) | random.nextInt(100) + 10 > random.nextInt(200) | random.nextInt(100) + 11 > random.nextInt(200) | random.nextInt(100) + 12 > random.nextInt(200) | random.nextInt(100) + 13 > random.nextInt(200) | random.nextInt(100) + 14 > random.nextInt(200) | random.nextInt(100) + 15 > random.nextInt(200) | random.nextInt(100) + 16 > random.nextInt(200) | random.nextInt(100) + 17 > random.nextInt(200) | random.nextInt(100) + 18 > random.nextInt(200) | random.nextInt(100) + 19 > random.nextInt(200) | random.nextInt(100) >= random.nextInt(200);
    }

    int methodIfWithAnd(int s) {
        int sum = s;
        Random random = new Random();
        if (isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random) & isaBoolean(random)) {
            sum += 1000;
        } else {
            sum -= 1000;
        }
        return sum;
    }

    int methodIfAndAndOrOr(int s) {
        int sum = s;
        Random random = new Random();
        if (((int) (Math.random() * 10000) % 2 == 0 || random.nextInt(100) + 1 > random.nextInt(200) || random.nextInt(100) + 2 > random.nextInt(200))
                && ((int) (Math.random() * 10000) % 2 == 0 || random.nextInt(100) + 1 > random.nextInt(200) || random.nextInt(100) + 2 > random.nextInt(200))
                && ((int) (Math.random() * 10000) % 2 == 0 || random.nextInt(100) + 1 > random.nextInt(200) || random.nextInt(100) + 2 > random.nextInt(200))
        ) {
            sum += 1000;
        } else {
            sum -= 1000;
        }
        return sum;
    }

    int methodSimpleIfAndAndOrOr(int s) {
        int sum = s;
        if (((int) (Math.random() * 10000) % 2 == 0 || s > 10)
                && ((int) (Math.random() * 10000) % 2 == 0 || s <= 10)
        ) {
            sum += 1000;
        } else {
            sum -= 1000;
        }
        return sum;
    }

    int methodDiffConditions(int s) {
        int sum = s;

        if ((int) (Math.random() * 10000) % 2 == 0) {
            sum += 1000;
        } else {
            sum -= 1000;
        }

        if ((int) (Math.random() * 10000) % 2 == 0 || s > 10) {
            sum += 1000;
        } else {
            sum -= 1000;
        }

        if ((int) (Math.random() * 10000) % 2 == 0 && s > 10) {
            sum += 1000;
        } else {
            sum -= 1000;
        }

        if ((int) (Math.random() * 10000) % 2 == 0 | s > 10) {
            sum += 1000;
        } else {
            sum -= 1000;
        }

        if ((int) (Math.random() * 10000) % 2 == 0 & s > 10) {
            sum += 1000;
        } else {
            sum -= 1000;
        }

        return sum;
    }

}
