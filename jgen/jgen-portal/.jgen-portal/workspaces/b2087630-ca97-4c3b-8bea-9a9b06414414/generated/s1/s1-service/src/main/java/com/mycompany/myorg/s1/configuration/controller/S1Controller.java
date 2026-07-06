package com.mycompany.myorg.s1.configuration.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.chenile.base.response.GenericResponse;
import com.mycompany.myorg.s1.model.S1;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.handler.ControllerSupport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ChenileController(value = "s1Service", serviceName = "_s1Service_",
		healthCheckerName = "s1HealthChecker", bluePrintName = "chenile-service")
public class S1Controller extends ControllerSupport{
	
    @PostMapping("/s1")
    public ResponseEntity<GenericResponse<S1>> save(
        HttpServletRequest httpServletRequest,
        @RequestBody S1 entity){
        return process(httpServletRequest,entity);
        }

    @GetMapping("/s1/{id}")
    public ResponseEntity<GenericResponse<S1>> retrieve(
    HttpServletRequest httpServletRequest,
    @PathVariable("id") String id){
    return process(httpServletRequest,id);
    }
}
