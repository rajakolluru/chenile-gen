package com.mycompany.myorg.s1.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.mycompany.myorg.s1.model.S1;
import com.mycompany.myorg.s1.service.S1Service;

import com.mycompany.myorg.s1.configuration.dao.S1Repository;
import org.chenile.base.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;

public class S1ServiceImpl implements S1Service{
    private static final Logger logger = LoggerFactory.getLogger(S1ServiceImpl.class);
    @Autowired
    S1Repository s1Repository;
    @Override
    public S1 save(S1 entity) {
        entity = s1Repository.save(entity);
        return entity;
    }

    @Override
    public S1 retrieve(String id) {
        Optional<S1> entity = s1Repository.findById(id);
        if (entity.isPresent()) return entity.get();
        throw new NotFoundException("1500","Unable to find s1 with ID " + id);
    }
}