package com.mycompany.myorg.returns.model;

import org.chenile.workflow.model.*;
import jakarta.persistence.*;
import org.chenile.jpautils.entity.AbstractJpaStateEntity;
@Entity
@Table(name = "returns")
public class Returns extends AbstractJpaStateEntity
        implements
    ContainsTransientMap
{
	public String description;
    @Transient
    public TransientMap transientMap = new TransientMap();
    public TransientMap getTransientMap(){
        return this.transientMap;
    }

}
