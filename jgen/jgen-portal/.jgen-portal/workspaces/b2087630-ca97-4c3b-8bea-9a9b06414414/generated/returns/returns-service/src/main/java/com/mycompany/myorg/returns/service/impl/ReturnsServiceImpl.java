package com.mycompany.myorg.returns.service.impl;

import com.mycompany.myorg.returns.dto.ReturnsIn;
import com.mycompany.myorg.returns.model.Returns;
import com.mycompany.myorg.returns.api.ReturnsService;
import org.chenile.workflow.service.impl.StateEntityServiceImpl;
import org.chenile.stm.*;
import org.chenile.stm.impl.*;
import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.dto.StateEntityServiceResponse;

public class ReturnsServiceImpl extends StateEntityServiceImpl<Returns> implements ReturnsService {

    public ReturnsServiceImpl(
         STM<Returns> stm,
         STMActionsInfoProvider returnsInfoProvider,
         EntityStore<Returns> entityStore
        ){
        super(stm,returnsInfoProvider,entityStore);
    }

    public StateEntityServiceResponse<Returns> createReturns(ReturnsIn returnsIn){
        Returns returns = new Returns();
        returns.description = returnsIn.description;
        returns.id = returnsIn.id;
        // copy the other attributes here as well.
        return create(returns);
    }

}