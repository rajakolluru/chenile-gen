package com.mycompany.myorg.demogaurav.configuration.dao;

import com.mycompany.myorg.demogaurav.model.Demogaurav;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository  public interface DemogauravRepository extends JpaRepository<Demogaurav,String> {}
