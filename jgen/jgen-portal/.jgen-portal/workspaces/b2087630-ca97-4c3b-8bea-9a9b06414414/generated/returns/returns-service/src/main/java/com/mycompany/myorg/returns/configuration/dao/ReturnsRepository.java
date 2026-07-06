package com.mycompany.myorg.returns.configuration.dao;

import com.mycompany.myorg.returns.model.Returns;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository  public interface ReturnsRepository extends JpaRepository<Returns,String> {}
