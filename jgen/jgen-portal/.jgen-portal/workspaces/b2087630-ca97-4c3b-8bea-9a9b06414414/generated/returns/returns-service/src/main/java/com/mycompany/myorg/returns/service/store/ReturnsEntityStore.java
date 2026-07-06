package com.mycompany.myorg.returns.service.store;

import org.chenile.utils.entity.service.EntityStore;
import com.mycompany.myorg.returns.model.Returns;
import org.springframework.beans.factory.annotation.Autowired;
import org.chenile.base.exception.NotFoundException;
import com.mycompany.myorg.returns.configuration.dao.ReturnsRepository;
import java.util.Optional;

public class ReturnsEntityStore implements EntityStore<Returns>{
    @Autowired private ReturnsRepository returnsRepository;

	@Override
	public void store(Returns entity) {
        returnsRepository.save(entity);
	}

	@Override
	public Returns retrieve(String id) {
        Optional<Returns> entity = returnsRepository.findById(id);
        if (entity.isPresent()) return entity.get();
        throw new NotFoundException("1500","Unable to find Returns with ID " + id);
	}

}
