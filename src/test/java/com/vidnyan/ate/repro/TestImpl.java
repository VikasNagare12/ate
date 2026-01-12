package com.vidnyan.ate.repro;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class TestImpl implements TestInterface {
    
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void remoteCall() {
        restTemplate.getForObject("https://example.com", String.class);
    }
}
