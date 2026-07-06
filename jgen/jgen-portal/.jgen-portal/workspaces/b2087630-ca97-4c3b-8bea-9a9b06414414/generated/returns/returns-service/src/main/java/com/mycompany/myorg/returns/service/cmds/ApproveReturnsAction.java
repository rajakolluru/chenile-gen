package com.mycompany.myorg.returns.service.cmds;

import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;

import org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction;
import com.mycompany.myorg.returns.model.Returns;
import com.mycompany.myorg.returns.dto.ApproveReturnsPayload;

/**
 Contains customized logic for the transition. Common logic resides at {@link DefaultSTMTransitionAction}
 <p>Use this class if you want to augment the common logic for this specific transition</p>
 <p>Use a customized payload if required instead of MinimalPayload</p>
*/
public class ApproveReturnsAction extends AbstractSTMTransitionAction<Returns,

    ApproveReturnsPayload>{


	@Override
	public void transitionTo(Returns returns,
            ApproveReturnsPayload payload,
            State startState, String eventId,
			State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {
            returns.transientMap.previousPayload = payload;
	}

}
