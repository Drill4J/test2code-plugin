package com.epam.drill.plugins.test2code.ast;

import java.util.List;

public class CheckProbeRanges {

    public void noOp() {
    }
    public void oneOp(List<String> list) {
        list.add("1");
    }
    public void twoOps(List<String> list) {
        list.add("1");
        list.add("2");
    }
    public String ifOp(boolean b) {
        if (b)
            return "true";
        else
            return "false";
    }

    public String ifExpr(boolean b) {
        return b ? "true" : "false";
    }

    public void whileOp(List<String> list) {
        while (!list.isEmpty()) {
            list.remove(0);
        }
    }

    public void methodWithLambda(List<String> list) {
        list.forEach(s -> {
            System.out.println(s);
        });
    }

    public void methodRef(List<String> list) {
        list.forEach(System.out::println);
    }

}
