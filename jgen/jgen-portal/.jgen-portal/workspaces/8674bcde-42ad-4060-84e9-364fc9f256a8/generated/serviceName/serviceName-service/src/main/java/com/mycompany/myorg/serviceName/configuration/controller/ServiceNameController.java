package com.mycompany.myorg.serviceName.configuration.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.chenile.base.response.GenericResponse;
import com.mycompany.myorg.serviceName.model.ServiceName;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.handler.ControllerSupport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ChenileController(value = "serviceNameService", serviceName = "_serviceNameService_",
		healthCheckerName = "serviceNameHealthChecker", bluePrintName = "chenile-service")
public class ServiceNameController extends ControllerSupport{
	
    @PostMapping("/serviceName")
    public ResponseEntity<GenericResponse<ServiceName>> save(
        HttpServletRequest httpServletRequest,
        @RequestBody ServiceName entity){
        return process(httpServletRequest,entity);
        }

    @GetMapping("/serviceName/{id}")
    public ResponseEntity<GenericResponse<ServiceName>> retrieve(
    HttpServletRequest httpServletRequest,
    @PathVariable("id") String id){
    return process(httpServletRequest,id);
    }
}
