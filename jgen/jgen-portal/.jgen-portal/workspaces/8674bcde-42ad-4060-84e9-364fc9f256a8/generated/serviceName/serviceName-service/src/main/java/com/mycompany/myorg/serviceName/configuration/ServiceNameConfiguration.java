package com.mycompany.myorg.serviceName.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mycompany.myorg.serviceName.service.ServiceNameService;
import com.mycompany.myorg.serviceName.service.impl.ServiceNameServiceImpl;
import com.mycompany.myorg.serviceName.service.healthcheck.ServiceNameHealthChecker;

/**
 This is where you will instantiate all the required classes in Spring

*/
@Configuration
public class ServiceNameConfiguration {
	@Bean public ServiceNameService _serviceNameService_() {
		return new ServiceNameServiceImpl();
	}

	@Bean ServiceNameHealthChecker serviceNameHealthChecker(){
    	return new ServiceNameHealthChecker();
    }
}
