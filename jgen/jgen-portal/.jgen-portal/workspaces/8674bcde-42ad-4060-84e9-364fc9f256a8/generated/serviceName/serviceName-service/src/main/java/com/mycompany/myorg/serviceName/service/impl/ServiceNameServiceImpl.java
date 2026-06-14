package com.mycompany.myorg.serviceName.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.mycompany.myorg.serviceName.model.ServiceName;
import com.mycompany.myorg.serviceName.service.ServiceNameService;

import com.mycompany.myorg.serviceName.configuration.dao.ServiceNameRepository;
import org.chenile.base.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;

public class ServiceNameServiceImpl implements ServiceNameService{
    private static final Logger logger = LoggerFactory.getLogger(ServiceNameServiceImpl.class);
    @Autowired
    ServiceNameRepository serviceNameRepository;
    @Override
    public ServiceName save(ServiceName entity) {
        entity = serviceNameRepository.save(entity);
        return entity;
    }

    @Override
    public ServiceName retrieve(String id) {
        Optional<ServiceName> entity = serviceNameRepository.findById(id);
        if (entity.isPresent()) return entity.get();
        throw new NotFoundException("1500","Unable to find serviceName with ID " + id);
    }
}