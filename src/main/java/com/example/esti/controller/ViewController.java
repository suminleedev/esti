package com.example.esti.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {
    @GetMapping("/estimate")
    public String estimate() { return "estimatePage"; }
}
