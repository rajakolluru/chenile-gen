package org.chenile.samples.issue.model;

import jakarta.persistence.*;
import org.chenile.stm.State;
import org.chenile.stm.StateEntity;
import org.chenile.workflow.model.AbstractStateEntity;

import java.util.Date;
import java.util.UUID;

@MappedSuperclass
public class BaseEntity extends AbstractStateEntity {
    @Id @Column(name = "id") public String id;

    @Override
    public State getCurrentState() {
        return currentState;
    }

    @Override
    public void setCurrentState(State currentState) {
        this.currentState = currentState;
    }

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="flowId", column=@Column(name = "flowId")),
            @AttributeOverride(name="stateId", column=@Column(name = "stateId"))
    })
    public State currentState;
    public String tenant;
    public String createdBy;

    public String getId(){
        return this.id;
    }

    public void setId(String id){
        this.id = id;
    }

    @Version  public int version;
    @PrePersist
    @PreUpdate
    void setIdIfMissing() {
        if (getId() == null) {
            setId(UUID.randomUUID().toString());
        }
    }

}
