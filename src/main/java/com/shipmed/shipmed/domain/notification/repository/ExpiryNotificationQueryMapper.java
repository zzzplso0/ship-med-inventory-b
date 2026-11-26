package com.shipmed.shipmed.domain.notification.repository;

import com.shipmed.shipmed.domain.notification.dto.response.ExpiryNotificationLotRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ExpiryNotificationQueryMapper {

    List<ExpiryNotificationLotRow> selectNotificationLots(@Param("today") LocalDate today,
                                                          @Param("dMinus30") LocalDate dMinus30,
                                                          @Param("dMinus7") LocalDate dMinus7);
}
