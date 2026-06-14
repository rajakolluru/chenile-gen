package com.mycompany.myorg.demogaurav.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mycompany.myorg.demogaurav.service.DemogauravService;
import com.mycompany.myorg.demogaurav.service.impl.DemogauravServiceImpl;
import com.mycompany.myorg.demogaurav.service.healthcheck.DemogauravHealthChecker;

/**
 This is where you will instantiate all the required classes in Spring

*/
@Configuration
public class DemogauravConfiguration {
	@Bean public DemogauravService _demogauravService_() {
		return new DemogauravServiceImpl();
	}

	@Bean DemogauravHealthChecker demogauravHealthChecker(){
    	return new DemogauravHealthChecker();
    }
}
