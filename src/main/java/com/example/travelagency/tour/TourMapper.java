package com.example.travelagency.tour;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TourMapper {

    TourResponse toResponse(Tour tour);

    @Mapping(target = "id", ignore = true)
    Tour toEntity(TourRequest request);

    @Mapping(target = "id", ignore = true)
    void updateEntity(TourRequest request, @MappingTarget Tour tour);
}
