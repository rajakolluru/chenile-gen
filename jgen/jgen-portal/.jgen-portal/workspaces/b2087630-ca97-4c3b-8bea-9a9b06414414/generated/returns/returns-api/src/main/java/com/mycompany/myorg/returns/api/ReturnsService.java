package com.mycompany.myorg.returns.api;

import org.chenile.workflow.api.StateEntityService;
import com.mycompany.myorg.returns.dto.ReturnsIn;
import com.mycompany.myorg.returns.model.Returns;
import org.chenile.workflow.dto.StateEntityServiceResponse;


public interface ReturnsService extends StateEntityService<Returns> {
    public StateEntityServiceResponse<Returns> createReturns(ReturnsIn returnsIn);
}