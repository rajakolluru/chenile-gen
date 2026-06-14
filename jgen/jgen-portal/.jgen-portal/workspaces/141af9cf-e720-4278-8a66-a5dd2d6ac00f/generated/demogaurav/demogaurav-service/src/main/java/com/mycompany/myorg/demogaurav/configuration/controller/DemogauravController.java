package com.mycompany.myorg.demogaurav.configuration.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.chenile.base.response.GenericResponse;
import com.mycompany.myorg.demogaurav.model.Demogaurav;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.handler.ControllerSupport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ChenileController(value = "demogauravService", serviceName = "_demogauravService_",
		healthCheckerName = "demogauravHealthChecker", bluePrintName = "chenile-service")
public class DemogauravController extends ControllerSupport{
	
    @PostMapping("/demogaurav")
    public ResponseEntity<GenericResponse<Demogaurav>> save(
        HttpServletRequest httpServletRequest,
        @RequestBody Demogaurav entity){
        return process(httpServletRequest,entity);
        }

    @GetMapping("/demogaurav/{id}")
    public ResponseEntity<GenericResponse<Demogaurav>> retrieve(
    HttpServletRequest httpServletRequest,
    @PathVariable("id") String id){
    return process(httpServletRequest,id);
    }
}
