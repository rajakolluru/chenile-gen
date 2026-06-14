package com.mycompany.myorg.demogaurav.service;

import com.mycompany.myorg.demogaurav.model.Demogaurav;

public interface DemogauravService {
	// Define your interface here
    public Demogaurav save(Demogaurav demogaurav);
    public Demogaurav retrieve(String id);
}
