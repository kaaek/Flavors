package com.flavors.mapper;
import com.flavors.dto.SavedCreatorResponse;
import com.flavors.entity.SavedCreator;
import org.springframework.stereotype.Component;
@Component
public class SavedCreatorMapper {
    public SavedCreatorResponse toResponse(SavedCreator saved) {
        return new SavedCreatorResponse(
                saved.getCreatorId(),
                saved.getCreatedAt());
    }
}
