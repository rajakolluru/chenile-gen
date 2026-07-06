package com.mycompany.myorg.returns.configuration;

import org.chenile.stm.*;
import org.chenile.stm.action.STMTransitionAction;
import org.chenile.stm.impl.*;
import org.chenile.stm.spring.SpringBeanFactoryAdapter;
import org.chenile.workflow.param.MinimalPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.chenile.utils.entity.service.EntityStore;
import org.chenile.configuration.workflow.info.service.StateEntityInfoServiceImpl;
import org.chenile.workflow.service.impl.StateEntityServiceImpl;
import org.chenile.workflow.service.stmcmds.*;
import com.mycompany.myorg.returns.model.Returns;
import com.mycompany.myorg.returns.service.cmds.*;
import com.mycompany.myorg.returns.service.healthcheck.ReturnsHealthChecker;
import com.mycompany.myorg.returns.service.store.ReturnsEntityStore;
import com.mycompany.myorg.returns.service.impl.ReturnsServiceImpl;
import org.chenile.workflow.api.WorkflowRegistry;
import com.mycompany.myorg.returns.service.postSaveHooks.*;

/**
 This is where you will instantiate all the required classes in Spring
*/
@Configuration
public class ReturnsConfiguration {
	private static final String FLOW_DEFINITION_FILE = "com/mycompany/myorg/returns/returns-states.xml";
	public static final String PREFIX_FOR_PROPERTIES = "Returns";
    public static final String PREFIX_FOR_RESOLVER = "returns";

    @Bean BeanFactoryAdapter returnsBeanFactoryAdapter() {
		return new SpringBeanFactoryAdapter();
	}
	
	@Bean STMFlowStoreImpl returnsFlowStore(
            @Qualifier("returnsBeanFactoryAdapter") BeanFactoryAdapter returnsBeanFactoryAdapter
            )throws Exception{
		STMFlowStoreImpl stmFlowStore = new STMFlowStoreImpl();
		stmFlowStore.setBeanFactory(returnsBeanFactoryAdapter);
		return stmFlowStore;
	}
	
	@Bean  STM<Returns> returnsEntityStm(@Qualifier("returnsFlowStore") STMFlowStoreImpl stmFlowStore) throws Exception{
		STMImpl<Returns> stm = new STMImpl<>();		
		stm.setStmFlowStore(stmFlowStore);
		return stm;
	}
	
	@Bean  STMActionsInfoProvider returnsActionsInfoProvider(@Qualifier("returnsFlowStore") STMFlowStoreImpl stmFlowStore) {
		STMActionsInfoProvider provider =  new STMActionsInfoProvider(stmFlowStore);
        WorkflowRegistry.addSTMActionsInfoProvider("returns",provider);
        return provider;
	}
	
	@Bean EntityStore<Returns> returnsEntityStore() {
		return new ReturnsEntityStore();
	}
	
	@Bean  StateEntityServiceImpl<Returns> _returnsStateEntityService_(
			@Qualifier("returnsEntityStm") STM<Returns> stm,
			@Qualifier("returnsActionsInfoProvider") STMActionsInfoProvider returnsInfoProvider,
			@Qualifier("returnsEntityStore") EntityStore<Returns> entityStore){
		return new ReturnsServiceImpl(stm, returnsInfoProvider, entityStore);
	}

	@Bean StateEntityInfoServiceImpl _returnsStateEntityInfoService_(
			@Qualifier("returnsFlowStore") STMFlowStoreImpl stmFlowStore,
			@Autowired XmlFlowReader returnsFlowReader,
			@Qualifier("returnsActionsInfoProvider") STMActionsInfoProvider returnsInfoProvider){
		return new StateEntityInfoServiceImpl(stmFlowStore, returnsInfoProvider);
	}
	
	// Now we start constructing the STM Components 


    @Bean  DefaultPostSaveHook<Returns> returnsDefaultPostSaveHook(
    @Qualifier("returnsTransitionActionResolver") STMTransitionActionResolver stmTransitionActionResolver){
    DefaultPostSaveHook<Returns> postSaveHook = new DefaultPostSaveHook<>(stmTransitionActionResolver);
    return postSaveHook;
    }

    @Bean  GenericEntryAction<Returns> returnsEntryAction(@Qualifier("returnsEntityStore") EntityStore<Returns> entityStore,
    @Qualifier("returnsActionsInfoProvider") STMActionsInfoProvider returnsInfoProvider,
    @Qualifier("returnsFlowStore") STMFlowStoreImpl stmFlowStore,
    @Qualifier("returnsDefaultPostSaveHook") DefaultPostSaveHook<Returns> postSaveHook)  {
    GenericEntryAction<Returns> entryAction =  new GenericEntryAction<Returns>(entityStore,returnsInfoProvider,postSaveHook);
    stmFlowStore.setEntryAction(entryAction);
    return entryAction;
    }

    @Bean  DefaultAutomaticStateComputation<Returns> returnsDefaultAutoState(
    @Qualifier("returnsTransitionActionResolver") STMTransitionActionResolver stmTransitionActionResolver,
    @Qualifier("returnsFlowStore") STMFlowStoreImpl stmFlowStore){
    DefaultAutomaticStateComputation<Returns> autoState = new DefaultAutomaticStateComputation<>(stmTransitionActionResolver);
    stmFlowStore.setDefaultAutomaticStateComputation(autoState);
    return autoState;
    }

	@Bean GenericExitAction<Returns> returnsExitAction(@Qualifier("returnsFlowStore") STMFlowStoreImpl stmFlowStore){
        GenericExitAction<Returns> exitAction = new GenericExitAction<Returns>();
        stmFlowStore.setExitAction(exitAction);
        return exitAction;
	}

	@Bean
	XmlFlowReader returnsFlowReader(@Qualifier("returnsFlowStore") STMFlowStoreImpl flowStore) throws Exception {
		XmlFlowReader flowReader = new XmlFlowReader(flowStore);
		flowReader.setFilename(FLOW_DEFINITION_FILE);
		return flowReader;
	}
	

	@Bean ReturnsHealthChecker returnsHealthChecker(){
    	return new ReturnsHealthChecker();
    }

    @Bean STMTransitionAction<Returns> defaultreturnsSTMTransitionAction() {
        return new DefaultSTMTransitionAction<MinimalPayload>();
    }

    @Bean
    STMTransitionActionResolver returnsTransitionActionResolver(
    @Qualifier("defaultreturnsSTMTransitionAction") STMTransitionAction<Returns> defaultSTMTransitionAction){
        return new STMTransitionActionResolver(PREFIX_FOR_RESOLVER,defaultSTMTransitionAction,true);
    }

    @Bean  StmBodyTypeSelector returnsBodyTypeSelector(
    @Qualifier("returnsActionsInfoProvider") STMActionsInfoProvider returnsInfoProvider,
    @Qualifier("returnsTransitionActionResolver") STMTransitionActionResolver stmTransitionActionResolver) {
        return new StmBodyTypeSelector(returnsInfoProvider,stmTransitionActionResolver);
    }

    @Bean ProcessIdPolymorph returnsProcessIdPolymorph(
    @Qualifier("returnsBodyTypeSelector") StmBodyTypeSelector returnsBodyTypeSelector) {
        return new ProcessIdPolymorph("returns", returnsBodyTypeSelector);
    }

    @Bean  STMTransitionAction<Returns> returnsBaseTransitionAction(
        @Qualifier("returnsTransitionActionResolver") STMTransitionActionResolver stmTransitionActionResolver,
        @Qualifier("returnsFlowStore") STMFlowStoreImpl stmFlowStore){
        BaseTransitionAction<Returns> baseTransitionAction = new BaseTransitionAction<>(stmTransitionActionResolver);
        stmFlowStore.setDefaultTransitionAction(baseTransitionAction);
        return baseTransitionAction;
    }


    // Create the specific transition actions here. Make sure that these actions are inheriting from
    // AbstractSTMTransitionMachine (The sample classes provide an example of this). To automatically wire
    // them into the STM use the convention of "returns" + eventId + "Action" for the method name. (returns is the
    // prefix passed to the TransitionActionResolver above.)
    // This will ensure that these are detected automatically by the Workflow system.
    // The payload types will be detected as well so that there is no need to introduce an <event-information/>
    // segment in src/main/resources/com/mycompany/returns/returns-states.xml


    @Bean CancelReturnsAction
            returnsCancelAction(){
        return new CancelReturnsAction();
    }

    @Bean ReceiveReturnsAction
            returnsReceiveAction(){
        return new ReceiveReturnsAction();
    }

    @Bean RejectReturnsAction
            returnsRejectAction(){
        return new RejectReturnsAction();
    }

    @Bean ApproveReturnsAction
            returnsApproveAction(){
        return new ApproveReturnsAction();
    }

    @Bean RefundReturnsAction
            returnsRefundAction(){
        return new RefundReturnsAction();
    }


    @Bean ConfigProviderImpl returnsConfigProvider() {
        return new ConfigProviderImpl();
    }

    @Bean ConfigBasedEnablementStrategy returnsConfigBasedEnablementStrategy(
        @Qualifier("returnsConfigProvider") ConfigProvider configProvider,
        @Qualifier("returnsFlowStore") STMFlowStoreImpl stmFlowStore) {
        ConfigBasedEnablementStrategy enablementStrategy = new ConfigBasedEnablementStrategy(configProvider,PREFIX_FOR_PROPERTIES);
        stmFlowStore.setEnablementStrategy(enablementStrategy);
        return enablementStrategy;
    }



    @Bean RECEIVEDReturnsPostSaveHook
        returnsRECEIVEDPostSaveHook(){
            return new RECEIVEDReturnsPostSaveHook();
    }

    @Bean CANCELLEDReturnsPostSaveHook
        returnsCANCELLEDPostSaveHook(){
            return new CANCELLEDReturnsPostSaveHook();
    }

    @Bean REFUNDEDReturnsPostSaveHook
        returnsREFUNDEDPostSaveHook(){
            return new REFUNDEDReturnsPostSaveHook();
    }

    @Bean INITIATEDReturnsPostSaveHook
        returnsINITIATEDPostSaveHook(){
            return new INITIATEDReturnsPostSaveHook();
    }

    @Bean APPROVEDReturnsPostSaveHook
        returnsAPPROVEDPostSaveHook(){
            return new APPROVEDReturnsPostSaveHook();
    }

    @Bean REJECTEDReturnsPostSaveHook
        returnsREJECTEDPostSaveHook(){
            return new REJECTEDReturnsPostSaveHook();
    }

}
