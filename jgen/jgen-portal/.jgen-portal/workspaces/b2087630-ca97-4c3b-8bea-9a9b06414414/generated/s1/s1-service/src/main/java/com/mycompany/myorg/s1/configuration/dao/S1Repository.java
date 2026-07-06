package com.mycompany.myorg.s1.configuration.dao;

import com.mycompany.myorg.s1.model.S1;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository  public interface S1Repository extends JpaRepository<S1,String> {}
