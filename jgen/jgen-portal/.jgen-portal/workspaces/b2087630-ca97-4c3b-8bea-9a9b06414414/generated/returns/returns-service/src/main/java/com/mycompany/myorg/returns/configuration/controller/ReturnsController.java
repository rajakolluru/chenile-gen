package com.mycompany.myorg.returns.configuration.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.chenile.base.response.GenericResponse;
import org.chenile.http.annotation.BodyTypeSelector;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.annotation.ChenileParamType;
import org.chenile.http.handler.ControllerSupport;
import org.chenile.mcp.model.ChenileMCP;
import org.chenile.mcp.model.ChenilePolymorph;
import org.springframework.http.ResponseEntity;

import org.chenile.stm.StateEntity;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.chenile.workflow.dto.StateEntityServiceResponse;
import com.mycompany.myorg.returns.model.Returns;
import com.mycompany.myorg.returns.dto.ReturnsIn;

@RestController
@ChenileController(value = "returnsService", serviceName = "_returnsStateEntityService_",
		healthCheckerName = "returnsHealthChecker", bluePrintName = "wfcustom")
public class ReturnsController extends ControllerSupport{
	
	@GetMapping("/returns/{id}")
    @ChenileMCP(name = "returnsRetrieveById", description = "Retrieve returns by id")
	public ResponseEntity<GenericResponse<StateEntityServiceResponse<Returns>>> retrieve(
			HttpServletRequest httpServletRequest,
			@PathVariable String id){
		return process(httpServletRequest,id);
	}

	@PostMapping("/returns")
    @ChenileMCP(name = "returnsCreate", description = "Create a new returns")
	public ResponseEntity<GenericResponse<StateEntityServiceResponse<Returns>>> createReturns(
			HttpServletRequest httpServletRequest,
			@RequestBody ReturnsIn entity){
		return process(httpServletRequest,entity);
	}

	
	@PatchMapping("/returns/{id}/{eventID}")
	@BodyTypeSelector("returnsBodyTypeSelector")
	@ChenileMCP(name = "returnsProcessById", description = "Process returns workflow event by id")
	@ChenilePolymorph("returnsProcessIdPolymorph")
	public ResponseEntity<GenericResponse<StateEntityServiceResponse<Returns>>> processById(
			HttpServletRequest httpServletRequest,
			@PathVariable String id,
			@PathVariable String eventID,
			@ChenileParamType(Object.class) 
			@RequestBody String eventPayload){
		return process(httpServletRequest,id,eventID,eventPayload);
	}


}
