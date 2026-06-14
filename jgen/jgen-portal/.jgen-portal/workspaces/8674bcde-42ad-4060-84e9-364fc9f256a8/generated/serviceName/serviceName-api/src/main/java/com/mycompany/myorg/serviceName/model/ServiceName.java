package com.mycompany.myorg.serviceName.model;

import java.io.Serializable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.chenile.jpautils.entity.BaseJpaEntity;


@Entity
@Table(name = "serviceName")
public class ServiceName extends BaseJpaEntity  {
    // Define your data model here
    public String attribute1;
}
