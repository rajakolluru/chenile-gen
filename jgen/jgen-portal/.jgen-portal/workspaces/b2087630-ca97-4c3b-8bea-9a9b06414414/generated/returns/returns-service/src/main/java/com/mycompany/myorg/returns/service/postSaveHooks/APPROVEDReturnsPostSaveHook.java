package com.mycompany.myorg.returns.service.postSaveHooks;

import com.mycompany.myorg.returns.model.Returns;
import org.chenile.stm.State;
import org.chenile.workflow.model.TransientMap;
import org.chenile.workflow.service.stmcmds.PostSaveHook;

/**
 Contains customized post Save Hook for the State ID.
*/
public class APPROVEDReturnsPostSaveHook implements PostSaveHook<Returns>{
	@Override
    public void execute(State startState, State endState, Returns returns, TransientMap map){
    }
}
