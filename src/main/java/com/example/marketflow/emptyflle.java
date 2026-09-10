package com.example.marketflow;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class emptyflle{
    public static void main(String[] args) {
        final int x;

        if (args.length > 0) {
            x = 10;
        } else {
            x = 20;
        }

        Runnable r = () -> System.out.println(x);
        r.run();
    }
}