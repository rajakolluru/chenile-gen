package com.mycompany.myorg.demogaurav.model;

import java.io.Serializable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.chenile.jpautils.entity.BaseJpaEntity;


@Entity
@Table(name = "demogaurav")
public class Demogaurav extends BaseJpaEntity  {
    // Define your data model here
    public String attribute1;
}
