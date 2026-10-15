package com.example.travelagency.tour;

import com.example.travelagency.aop.BookingFee;
import com.example.travelagency.aop.NormalizeTourInput;
import com.example.travelagency.aop.PassengerLimit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TourService {

    private final TourRepository tourRepository;
    private final TourMapper tourMapper;

    public TourService(TourRepository tourRepository, TourMapper tourMapper) {
        this.tourRepository = tourRepository;
        this.tourMapper = tourMapper;
    }

    public List<TourResponse> findAll() {
        return tourRepository.findAll()
                .stream()
                .map(tourMapper::toResponse)
                .toList();
    }

    public TourResponse findById(Long id) {
        return tourMapper.toResponse(getTour(id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @NormalizeTourInput
    public TourResponse create(TourRequest request) {
//        addBookingFee(request);
        Tour tour = tourMapper.toEntity(request);
        return tourMapper.toResponse(tourRepository.save(tour));
    }

//    @BookingFee(value = "30")
//    public TourRequest addBookingFee(TourRequest tourRequest) {
//        return tourRequest;
//    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @NormalizeTourInput
    public TourResponse update(Long id, TourRequest request) {
        Tour tour = getTour(id);
        tourMapper.updateEntity(request, tour);
        return tourMapper.toResponse(tour);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        Tour tour = getTour(id);
        tourRepository.delete(tour);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public TourResponse toggleFeatured(Long id) {
        Tour tour = getTour(id);
        tour.setFeatured(!tour.isFeatured());
        return tourMapper.toResponse(tour);
    }

    @PassengerLimit
    @BookingFee("20.00")
    public TourQuote quote(Long id, int passengers) {
        Tour tour = getTour(id);
        if (passengers > tour.getAvailableSeats()) {
            throw new InvalidPassengerCountException("Not enough available seats for this tour");
        }
        BigDecimal subtotal = tour.getPrice().multiply(BigDecimal.valueOf(passengers))
                .setScale(2, RoundingMode.HALF_UP);
        return new TourQuote(id, passengers, subtotal, new BigDecimal("0.00"), subtotal);
    }

    public TourQuote quoteViaSelfInvocation(Long id, int passengers) {
        return this.quote(id, passengers);
    }

    private Tour getTour(Long id) {
        return tourRepository.findById(id)
                .orElseThrow(() -> new TourNotFoundException(id));
    }
}
