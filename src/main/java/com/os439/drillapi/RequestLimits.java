package com.os439.drillapi;

import java.util.HashMap;
import java.util.Map;

/** Per-instance protection. Use a shared gateway limit when running multiple replicas. */
final class RequestLimits {
    private record Window(long expires, int count) {}
    private final Map<String,Window> windows=new HashMap<>();
    synchronized boolean allow(String key,int limit,long durationMillis) {
        long now=System.currentTimeMillis();
        windows.entrySet().removeIf(e -> e.getValue().expires()<=now);
        Window previous=windows.get(key);
        if(previous==null) {
            if(windows.size()>=10000) return false;
            windows.put(key,new Window(now+durationMillis,1)); return true;
        }
        if(previous.count()>=limit) return false;
        windows.put(key,new Window(previous.expires(),previous.count()+1)); return true;
    }
}
