package com.mycompany.myorg.s1.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mycompany.myorg.s1.service.S1Service;
import com.mycompany.myorg.s1.service.impl.S1ServiceImpl;
import com.mycompany.myorg.s1.service.healthcheck.S1HealthChecker;

/**
 This is where you will instantiate all the required classes in Spring

*/
@Configuration
public class S1Configuration {
	@Bean public S1Service _s1Service_() {
		return new S1ServiceImpl();
	}

	@Bean S1HealthChecker s1HealthChecker(){
    	return new S1HealthChecker();
    }
}
