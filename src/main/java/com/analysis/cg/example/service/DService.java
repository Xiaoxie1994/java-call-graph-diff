package com.analysis.cg.example.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class DService {

    @Resource
    private EService eService;

    public void run() {
        log.info("this is D!");
        eService.run();
    }
}
