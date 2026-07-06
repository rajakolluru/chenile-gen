package com.mycompany.myorg.returns;


import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.ActiveProfiles;

import org.chenile.utils.entity.service.EntityStore;
import com.mycompany.myorg.returns.model.Returns;


@Configuration
@PropertySource("classpath:com/mycompany/myorg/returns/TestService.properties")
@SpringBootApplication(scanBasePackages = { "org.chenile.configuration", "com.mycompany.myorg.returns.configuration" })
@ActiveProfiles("unittest")
public class SpringTestConfig extends SpringBootServletInitializer{
	
}

