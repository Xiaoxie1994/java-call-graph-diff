package com.analysis.cg.example.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class BService {

    @Resource
    private DService dService;

    public void run() {
        log.info("this is B!");
        dService.run();
    }
}
