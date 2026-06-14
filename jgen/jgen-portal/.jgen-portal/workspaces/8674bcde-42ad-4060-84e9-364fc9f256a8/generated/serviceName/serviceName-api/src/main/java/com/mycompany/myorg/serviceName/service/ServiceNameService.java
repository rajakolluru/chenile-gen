package com.mycompany.myorg.serviceName.service;

import com.mycompany.myorg.serviceName.model.ServiceName;

public interface ServiceNameService {
	// Define your interface here
    public ServiceName save(ServiceName serviceName);
    public ServiceName retrieve(String id);
}
