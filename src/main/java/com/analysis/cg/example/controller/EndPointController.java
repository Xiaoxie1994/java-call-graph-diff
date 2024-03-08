package com.analysis.cg.example.controller;

import com.analysis.cg.example.service.AService;
import com.analysis.cg.example.service.BService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/test")
public class EndPointController {

    @Resource
    private AService aService;

    @Resource
    private BService bService;

    @GetMapping("/endpoint")
    public void endPoint() {
        log.info("this is start!");
        aService.run();
        bService.run();
    }
}
