package com.mycompany.myorg.returns.configuration.controller;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.chenile.base.response.GenericResponse;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.handler.ControllerSupport;
import org.chenile.workflow.info.model.StateEntityAllowedActionsRequest;
import org.chenile.workflow.info.model.StateEntityInfoRequest;
import org.chenile.workflow.info.model.WorkflowInfoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ChenileController(value = "returnsStateEntityInfoService", serviceName = "_returnsStateEntityInfoService_",
        healthCheckerName = "returnsHealthChecker", bluePrintName = "wfcustom")
public class ReturnsStateEntityInfoController extends ControllerSupport {
    @RequestMapping(value = "/returns/info/state-diagram", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<GenericResponse<WorkflowInfoResponse<byte[]>>> renderStateDiagram(
            HttpServletRequest request,
            @RequestBody(required = false) StateEntityInfoRequest workflowInfoRequest,
            @RequestParam(required = false) String stylingPropertiesText,
            @RequestParam(required = false) String flowId) {
        return process("renderStateDiagram", request,
                mergeStateEntityInfoRequest(workflowInfoRequest, stylingPropertiesText, flowId));
    }

    @RequestMapping(value = "/returns/info/allowed-actions", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<GenericResponse<WorkflowInfoResponse<List<String>>>> allowedActions(
            HttpServletRequest request,
            @RequestBody(required = false) StateEntityAllowedActionsRequest workflowInfoRequest,
            @RequestParam(required = false) String stylingPropertiesText,
            @RequestParam(required = false) String flowId,
            @RequestParam(required = false) String state) {
        return process("allowedActions", request,
                mergeAllowedActionsRequest(workflowInfoRequest, stylingPropertiesText, flowId, state));
    }

    @RequestMapping(value = "/returns/info/json", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<GenericResponse<WorkflowInfoResponse<Map<String, Object>>>> toJson(
            HttpServletRequest request,
            @RequestBody(required = false) StateEntityInfoRequest workflowInfoRequest,
            @RequestParam(required = false) String stylingPropertiesText,
            @RequestParam(required = false) String flowId) {
        return process("toJson", request,
                mergeStateEntityInfoRequest(workflowInfoRequest, stylingPropertiesText, flowId));
    }

    @RequestMapping(value = "/returns/info/test-cases", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<GenericResponse<WorkflowInfoResponse<String>>> generateTestCases(
            HttpServletRequest request,
            @RequestBody(required = false) StateEntityInfoRequest workflowInfoRequest,
            @RequestParam(required = false) String stylingPropertiesText,
            @RequestParam(required = false) String flowId) {
        return process("generateTestCases", request,
                mergeStateEntityInfoRequest(workflowInfoRequest, stylingPropertiesText, flowId));
    }

    @RequestMapping(value = "/returns/info/test-visualization", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<GenericResponse<WorkflowInfoResponse<String>>> visualizeTestCases(
            HttpServletRequest request,
            @RequestBody(required = false) StateEntityInfoRequest workflowInfoRequest,
            @RequestParam(required = false) String stylingPropertiesText,
            @RequestParam(required = false) String flowId) {
        return process("visualizeTestCases", request,
                mergeStateEntityInfoRequest(workflowInfoRequest, stylingPropertiesText, flowId));
    }

    @RequestMapping(value = "/returns/info/test-state-diagrams", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<GenericResponse<WorkflowInfoResponse<Map<String, byte[]>>>> renderTestsAsStateDiagram(
            HttpServletRequest request,
            @RequestBody(required = false) StateEntityInfoRequest workflowInfoRequest,
            @RequestParam(required = false) String stylingPropertiesText,
            @RequestParam(required = false) String flowId) {
        return process("renderTestsAsStateDiagram", request,
                mergeStateEntityInfoRequest(workflowInfoRequest, stylingPropertiesText, flowId));
    }

    private StateEntityInfoRequest mergeStateEntityInfoRequest(StateEntityInfoRequest request,
                                                               String stylingPropertiesText, String flowId) {
        StateEntityInfoRequest stateEntityInfoRequest = request == null ? new StateEntityInfoRequest() : request;
        if (stylingPropertiesText != null) {
            stateEntityInfoRequest.setStylingPropertiesText(stylingPropertiesText);
        }
        if (flowId != null) {
            stateEntityInfoRequest.setFlowId(flowId);
        }
        return stateEntityInfoRequest;
    }

    private StateEntityAllowedActionsRequest mergeAllowedActionsRequest(StateEntityAllowedActionsRequest request,
                                                                        String stylingPropertiesText,
                                                                        String flowId, String state) {
        StateEntityAllowedActionsRequest allowedActionsRequest =
                request == null ? new StateEntityAllowedActionsRequest() : request;
        mergeStateEntityInfoRequest(allowedActionsRequest, stylingPropertiesText, flowId);
        if (state != null) {
            allowedActionsRequest.setState(state);
        }
        return allowedActionsRequest;
    }
}
