package com.mycompany.myorg.demogaurav.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.mycompany.myorg.demogaurav.model.Demogaurav;
import com.mycompany.myorg.demogaurav.service.DemogauravService;

import com.mycompany.myorg.demogaurav.configuration.dao.DemogauravRepository;
import org.chenile.base.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;

public class DemogauravServiceImpl implements DemogauravService{
    private static final Logger logger = LoggerFactory.getLogger(DemogauravServiceImpl.class);
    @Autowired
    DemogauravRepository demogauravRepository;
    @Override
    public Demogaurav save(Demogaurav entity) {
        entity = demogauravRepository.save(entity);
        return entity;
    }

    @Override
    public Demogaurav retrieve(String id) {
        Optional<Demogaurav> entity = demogauravRepository.findById(id);
        if (entity.isPresent()) return entity.get();
        throw new NotFoundException("1500","Unable to find demogaurav with ID " + id);
    }
}