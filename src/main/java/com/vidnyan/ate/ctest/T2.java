package com.vidnyan.ate.ctest;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class T2 {
    private final T1 tq;

    public void ttttt(){
        tq.xxx();
    }

    public void ddddd() {
    }
}

