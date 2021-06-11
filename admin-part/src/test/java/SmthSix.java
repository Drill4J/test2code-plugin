/**
 * Copyright 2020 EPAM Systems
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class SmthSix implements Runnable {

    private String owners;

    public void ifElseMethod(int a) {
        System.out.println("0000");
        if (20 * a > 999) {
            System.out.println("1000");
        } else {
            System.out.println("111");
        }
        if (30 * a > 999) {
            System.out.println("222");
        } else {
            System.out.println("333");
            return;
        }
        System.out.println("4444");
    }

    @Override
    public void run() {
        ifElseMethod(1);
    }

}
