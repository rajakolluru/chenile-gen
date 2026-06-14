package com.mycompany.myorg.serviceName.configuration.dao;

import com.mycompany.myorg.serviceName.model.ServiceName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository  public interface ServiceNameRepository extends JpaRepository<ServiceName,String> {}
