package com.example.myapplication.tool;

public class ThreadLocalManager {
    public static final ThreadLocal THREAD_LOCAL=new ThreadLocal();
    public static <T>T get(){return (T)THREAD_LOCAL.get();}
    public static void set(Object v){THREAD_LOCAL.set(v);}
    public static void remove() {THREAD_LOCAL.remove();}
}
