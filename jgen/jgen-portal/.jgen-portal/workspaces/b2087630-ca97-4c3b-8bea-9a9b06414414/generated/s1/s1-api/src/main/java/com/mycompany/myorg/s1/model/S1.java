package com.mycompany.myorg.s1.model;

import java.io.Serializable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.chenile.jpautils.entity.BaseJpaEntity;


@Entity
@Table(name = "s1")
public class S1 extends BaseJpaEntity  {
    // Define your data model here
    public String attribute1;
}
